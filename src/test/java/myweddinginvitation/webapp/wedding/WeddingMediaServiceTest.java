package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import({MySqlTestConfiguration.class, WeddingMediaServiceTest.LockBarrierConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class WeddingMediaServiceTest {
	@TempDir
	static Path mediaDirectory;

	@Autowired WeddingMediaService service;
	@Autowired GalleryPhotoRepository photos;
	@Autowired WeddingSettingsRepository settings;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;
	@Autowired WeddingLockBarrier weddingLockBarrier;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@BeforeEach
	void resetMedia() throws IOException {
		jdbc.update("delete from gallery_photo");
		jdbc.update("""
				update wedding_settings set gallery_enabled = false,
				background_audio_enabled = false, background_audio_path = null, version = 0
				where id = 1
				""");
		Path gallery = mediaDirectory.resolve("gallery");
		if (Files.exists(gallery)) {
			try (Stream<Path> paths = Files.list(gallery)) {
				paths.forEach(this::delete);
			}
		}
		Path audio = mediaDirectory.resolve("audio");
		if (Files.exists(audio)) {
			try (Stream<Path> paths = Files.list(audio)) {
				paths.forEach(this::delete);
			}
		}
	}

	@Test
	void addsTenInContiguousOrderAndRejectsElevenWithoutStoring() throws IOException {
		for (int index = 0; index < 10; index++) {
			service.addPhoto(image(index), form("Photo " + index, null, null, 0));
		}

		assertThat(photos.findAllByOrderByPositionAsc())
				.extracting(GalleryPhoto::getPosition)
				.containsExactlyElementsOf(IntStream.range(0, 10).boxed().toList());
		assertThat(fileCount()).isEqualTo(20);

		assertThatThrownBy(() -> service.addPhoto(image(11), form("Photo 11", null, null, 0)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(photos.count()).isEqualTo(10);
		assertThat(fileCount()).isEqualTo(20);
	}

	@Test
	void stripsRequiredAltAndNormalizesOptionalCaptions() throws IOException {
		assertThatThrownBy(() -> service.addPhoto(image(1), form(" \n\t ", "Caption", "Caption", 0)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(photos.count()).isZero();
		assertThat(fileCount()).isZero();

		long id = service.addPhoto(image(2), form("  A happy couple  ", "  Keterangan  ", " \t ", 0));

		assertThat(photos.findById(id)).get()
				.extracting(GalleryPhoto::getAltText, GalleryPhoto::getCaptionId, GalleryPhoto::getCaptionEn)
				.containsExactly("A happy couple", "Keterangan", null);
	}

	@Test
	void editsMetadataRejectsStaleVersionAndFallsBackBothWaysInViews() throws IOException {
		long id = service.addPhoto(image(1), form("Initial alt", "Keterangan", null, 0));
		GalleryPhoto initial = photos.findById(id).orElseThrow();

		service.updatePhoto(id, form("  Updated alt  ", "  ", "  Caption  ", initial.getVersion()));

		GalleryPhoto updated = photos.findById(id).orElseThrow();
		assertThat(updated)
				.extracting(GalleryPhoto::getAltText, GalleryPhoto::getCaptionId, GalleryPhoto::getCaptionEn)
				.containsExactly("Updated alt", null, "Caption");
		assertThat(service.publicView("ID").photos().getFirst().caption()).isEqualTo("Caption");
		assertThat(service.publicView("EN").photos().getFirst().caption()).isEqualTo("Caption");
		assertThat(service.adminView().photos().getFirst())
				.extracting(WeddingMediaView.Photo::thumbnailUrl, WeddingMediaView.Photo::imageUrl)
				.containsExactly("/media/gallery/" + id + "/thumbnail", "/media/gallery/" + id + "/image");

		assertThatThrownBy(() -> service.updatePhoto(id,
				form("Stale alt", "Stale ID", "Stale EN", initial.getVersion())))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(photos.findById(id)).get().extracting(GalleryPhoto::getAltText).isEqualTo("Updated alt");

		service.updatePhoto(id, form("Updated alt", "  Keterangan baru  ", null, updated.getVersion()));
		assertThat(service.publicView("EN").photos().getFirst().caption()).isEqualTo("Keterangan baru");
	}

	@Test
	void committedReplacementDeletesOldFiles() throws IOException {
		long id = service.addPhoto(image(1), form("Alt", null, null, 0));
		GalleryPhoto old = photos.findById(id).orElseThrow();
		StoredGalleryImage oldImage = stored(old);

		service.replacePhoto(id, old.getVersion(), image(2));

		GalleryPhoto replacement = photos.findById(id).orElseThrow();
		assertThat(stored(replacement)).isNotEqualTo(oldImage);
		assertThatFilesExist(stored(replacement));
		assertThatFilesDoNotExist(oldImage);
		assertThat(fileCount()).isEqualTo(2);
	}

	@Test
	void failedReplacementPreservesOldFilesAndRollbackRemovesNewFiles() throws IOException {
		long id = service.addPhoto(image(1), form("Alt", null, null, 0));
		GalleryPhoto old = photos.findById(id).orElseThrow();
		StoredGalleryImage oldImage = stored(old);
		MockMultipartFile corrupt = new MockMultipartFile("image", "bad.jpg", "image/jpeg", new byte[] {1, 2, 3});

		assertThatThrownBy(() -> service.replacePhoto(id, old.getVersion(), corrupt))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(stored(photos.findById(id).orElseThrow())).isEqualTo(oldImage);
		assertThatFilesExist(oldImage);

		AtomicReference<StoredGalleryImage> rolledBack = new AtomicReference<>();
		new TransactionTemplate(transactions).executeWithoutResult(status -> {
			service.replacePhoto(id, old.getVersion(), image(3));
			rolledBack.set(stored(photos.findById(id).orElseThrow()));
			assertThatFilesExist(rolledBack.get());
			status.setRollbackOnly();
		});

		assertThat(stored(photos.findById(id).orElseThrow())).isEqualTo(oldImage);
		assertThatFilesExist(oldImage);
		assertThatFilesDoNotExist(rolledBack.get());
		assertThat(fileCount()).isEqualTo(2);
	}

	@Test
	void movesOnlyOneStepWithinBoundsAndKeepsPositionsContiguous() throws IOException {
		long first = service.addPhoto(image(1), form("First", null, null, 0));
		long second = service.addPhoto(image(2), form("Second", null, null, 0));
		long third = service.addPhoto(image(3), form("Third", null, null, 0));

		service.movePhoto(first, photo(first).getVersion(), -1);
		service.movePhoto(third, photo(third).getVersion(), 1);
		assertThat(photoIds()).containsExactly(first, second, third);

		service.movePhoto(second, photo(second).getVersion(), -1);

		assertThat(photoIds()).containsExactly(second, first, third);
		assertThat(photos.findAllByOrderByPositionAsc()).extracting(GalleryPhoto::getPosition)
				.containsExactly(0, 1, 2);
		assertThatThrownBy(() -> service.movePhoto(first, photo(first).getVersion(), 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deleteCompactsOrderCleansFilesAndLastDeleteDisablesGallery() throws IOException {
		long first = service.addPhoto(image(1), form("First", null, null, 0));
		long second = service.addPhoto(image(2), form("Second", null, null, 0));
		long third = service.addPhoto(image(3), form("Third", null, null, 0));
		StoredGalleryImage secondImage = stored(photo(second));
		service.setGalleryEnabled(wedding().getVersion(), true);

		service.deletePhoto(second, photo(second).getVersion());

		assertThat(photoIds()).containsExactly(first, third);
		assertThat(photos.findAllByOrderByPositionAsc()).extracting(GalleryPhoto::getPosition)
				.containsExactly(0, 1);
		assertThatFilesDoNotExist(secondImage);
		assertThat(wedding().isGalleryEnabled()).isTrue();

		service.deletePhoto(first, photo(first).getVersion());
		service.deletePhoto(third, photo(third).getVersion());

		assertThat(photos.count()).isZero();
		assertThat(fileCount()).isZero();
		assertThat(wedding().isGalleryEnabled()).isFalse();
	}

	@Test
	void galleryCannotBeEnabledWithoutPhotoAndRejectsStaleWeddingVersion() throws IOException {
		long emptyVersion = wedding().getVersion();
		assertThatThrownBy(() -> service.setGalleryEnabled(emptyVersion, true))
				.isInstanceOf(IllegalStateException.class);

		service.addPhoto(image(1), form("Alt", null, null, 0));
		service.setGalleryEnabled(emptyVersion, true);

		assertThat(service.adminView())
				.extracting(WeddingMediaView::galleryEnabled, WeddingMediaView::audioEnabled,
						WeddingMediaView::weddingVersion)
				.containsExactly(true, false, emptyVersion + 1);
		assertThatThrownBy(() -> service.setGalleryEnabled(emptyVersion, false))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(wedding().isGalleryEnabled()).isTrue();
	}

	@Test
	void replacesAudioAndRemovesOnlyCommittedObsoleteFile() throws IOException {
		service.replaceAudio(audio(1));
		String oldPath = wedding().getBackgroundAudioPath();
		assertThat(audioPath(oldPath)).isRegularFile();

		service.replaceAudio(audio(2));

		String replacement = wedding().getBackgroundAudioPath();
		assertThat(replacement).isNotEqualTo(oldPath);
		assertThat(audioPath(replacement)).isRegularFile();
		assertThat(audioPath(oldPath)).doesNotExist();

		AtomicReference<String> rolledBack = new AtomicReference<>();
		new TransactionTemplate(transactions).executeWithoutResult(status -> {
			service.replaceAudio(audio(3));
			rolledBack.set(wedding().getBackgroundAudioPath());
			assertThat(audioPath(rolledBack.get())).isRegularFile();
			status.setRollbackOnly();
		});
		assertThat(wedding().getBackgroundAudioPath()).isEqualTo(replacement);
		assertThat(audioPath(replacement)).isRegularFile();
		assertThat(audioPath(rolledBack.get())).doesNotExist();
	}

	@Test
	void failedAudioReplacementPreservesOldFile() {
		service.replaceAudio(audio(1));
		String oldPath = wedding().getBackgroundAudioPath();

		assertThatThrownBy(() -> service.replaceAudio(new MockMultipartFile("audio", "bad.mp3", "audio/mpeg", new byte[] {1, 2})))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(wedding().getBackgroundAudioPath()).isEqualTo(oldPath);
		assertThat(audioPath(oldPath)).isRegularFile();
	}

	@Test
	void audioEnablementRequiresFileDisablePreservesAndDeleteDisables() {
		long emptyVersion = wedding().getVersion();
		assertThatThrownBy(() -> service.setAudioEnabled(emptyVersion, true)).isInstanceOf(IllegalStateException.class);

		service.replaceAudio(audio(1));
		String path = wedding().getBackgroundAudioPath();
		service.setAudioEnabled(wedding().getVersion(), true);
		service.setAudioEnabled(wedding().getVersion(), false);
		assertThat(wedding().isBackgroundAudioEnabled()).isFalse();
		assertThat(audioPath(path)).isRegularFile();

		service.deleteAudio(wedding().getVersion());
		assertThat(wedding().getBackgroundAudioPath()).isNull();
		assertThat(wedding().isBackgroundAudioEnabled()).isFalse();
		assertThat(audioPath(path)).doesNotExist();
	}

	@Test
	void concurrentTenthAddsSerializeAtTheLimitWithoutDuplicatePosition() throws Exception {
		for (int index = 0; index < 9; index++) {
			service.addPhoto(image(index), form("Photo " + index, null, null, 0));
		}
		byte[] upload = png(20);
		weddingLockBarrier.arm();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executor.submit(() -> concurrentAdd(upload, "Concurrent one"));
			Future<Boolean> second = executor.submit(() -> concurrentAdd(upload, "Concurrent two"));
			assertThat(weddingLockBarrier.awaitBoth()).isTrue();
			weddingLockBarrier.release();

			assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(true, false);
		} finally {
			weddingLockBarrier.release();
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject("select count(*) from gallery_photo", Integer.class)).isEqualTo(10);
		assertThat(jdbc.queryForObject("select count(distinct position) from gallery_photo", Integer.class)).isEqualTo(10);
		assertThat(fileCount()).isEqualTo(20);
	}

	private boolean concurrentAdd(byte[] upload, String alt) {
		try {
			service.addPhoto(new MockMultipartFile("image", "photo.png", "image/png", upload), form(alt, null, null, 0));
			return true;
		} catch (IllegalStateException exception) {
			return false;
		}
	}

	static final class WeddingLockBarrier implements MethodInterceptor {
		private CountDownLatch waiting;
		private CountDownLatch release;

		void arm() {
			waiting = new CountDownLatch(2);
			release = new CountDownLatch(1);
		}

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			CountDownLatch currentWaiting = waiting;
			CountDownLatch currentRelease = release;
			if (currentWaiting != null) {
				currentWaiting.countDown();
				if (!currentRelease.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("Wedding lock release timed out");
				}
			}
			return invocation.proceed();
		}

		boolean awaitBoth() throws InterruptedException {
			return waiting.await(5, TimeUnit.SECONDS);
		}

		void release() {
			if (release != null) release.countDown();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static class LockBarrierConfig {
		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		WeddingLockBarrier weddingLockBarrier() {
			return new WeddingLockBarrier();
		}

		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		Advisor weddingLockBarrierAdvisor(WeddingLockBarrier barrier) {
			NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
			pointcut.setMappedName("findSingletonForUpdate");
			return new DefaultPointcutAdvisor(pointcut, barrier);
		}
	}

	private GalleryPhoto photo(long id) {
		return photos.findById(id).orElseThrow();
	}

	private List<Long> photoIds() {
		return photos.findAllByOrderByPositionAsc().stream().map(GalleryPhoto::getId).toList();
	}

	private WeddingSettings wedding() {
		return settings.getSingleton().orElseThrow();
	}

	private static GalleryPhotoForm form(String altText, String captionId, String captionEn, long version) {
		return new GalleryPhotoForm(altText, captionId, captionEn, version);
	}

	private static MockMultipartFile image(int color) {
		return new MockMultipartFile("image", "photo.png", "image/png", png(color));
	}

	private static MockMultipartFile audio(int value) {
		return new MockMultipartFile("audio", "track.mp3", "audio/mpeg",
				new byte[] {(byte) 0xff, (byte) 0xfb, (byte) 0x90, 0x64, (byte) value});
	}

	private Path audioPath(String relativePath) {
		return mediaDirectory.resolve(relativePath);
	}

	private static byte[] png(int color) {
		try {
			BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			image.setRGB(0, 0, new Color(color, color, color).getRGB());
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			assertThat(ImageIO.write(image, "PNG", output)).isTrue();
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static StoredGalleryImage stored(GalleryPhoto photo) {
		return new StoredGalleryImage(photo.getMainPath(), photo.getThumbnailPath());
	}

	private void assertThatFilesExist(StoredGalleryImage image) {
		assertThat(mediaDirectory.resolve(image.mainPath())).isRegularFile();
		assertThat(mediaDirectory.resolve(image.thumbnailPath())).isRegularFile();
	}

	private void assertThatFilesDoNotExist(StoredGalleryImage image) {
		assertThat(mediaDirectory.resolve(image.mainPath())).doesNotExist();
		assertThat(mediaDirectory.resolve(image.thumbnailPath())).doesNotExist();
	}

	private long fileCount() throws IOException {
		Path gallery = mediaDirectory.resolve("gallery");
		if (!Files.exists(gallery)) return 0;
		try (Stream<Path> paths = Files.list(gallery)) {
			return paths.filter(Files::isRegularFile).count();
		}
	}

	private void delete(Path path) {
		try {
			Files.delete(path);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
