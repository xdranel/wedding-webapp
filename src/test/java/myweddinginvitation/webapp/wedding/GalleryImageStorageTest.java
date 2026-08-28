package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class GalleryImageStorageTest {
	static {
		System.setProperty("java.awt.headless", "true");
	}

	@TempDir
	Path mediaDirectory;

	@ParameterizedTest
	@MethodSource("imageFixtures")
	void storesRealImagesAsBoundedWebp(String filename, String format, BufferedImage image) throws IOException {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		StoredGalleryImage stored = storage.store(file(filename, format, image));

		assertThat(stored.mainPath()).matches("gallery/[0-9a-f-]{36}\\.webp");
		assertThat(stored.thumbnailPath()).matches("gallery/[0-9a-f-]{36}-thumbnail\\.webp");
		assertDimensions(webp(storage.resolve(stored.mainPath())), 1920, 960);
		assertDimensions(webp(storage.resolve(stored.thumbnailPath())), 480, 240);
		assertThat(countFiles(mediaDirectory.resolve("gallery"))).isEqualTo(2);
	}

	@Test
	void doesNotUpscaleSmallImages() throws IOException {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		StoredGalleryImage stored = storage.store(file("small.png", "PNG", image(120, 80, true)));

		assertDimensions(webp(storage.resolve(stored.mainPath())), 120, 80);
		assertDimensions(webp(storage.resolve(stored.thumbnailPath())), 120, 80);
	}

	@Test
	void storesCoverAsOneBoundedWebp() throws IOException {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		String stored = storage.storeCover(file("cover.png", "PNG", image(2000, 1000, true)));

		assertThat(stored).matches("cover/[0-9a-f-]{36}\\.webp");
		assertDimensions(webp(storage.resolveCover(stored)), 1920, 960);
		assertThat(countFiles(mediaDirectory.resolve("cover"))).isEqualTo(1);
	}

	@Test
	void confinesAndDeletesCoverPaths() throws IOException {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);
		String stored = storage.storeCover(file("cover.png", "PNG", image(20, 10, false)));

		assertThatIllegalArgumentException().isThrownBy(() -> storage.resolveCover("../outside.webp"));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.resolveCover("gallery/outside.webp"));

		storage.deleteCover(stored);

		assertThat(mediaDirectory.resolve(stored)).doesNotExist();
	}

	@Test
	void preservesTransparency() throws IOException {
		BufferedImage image = image(800, 400, true);
		image.setRGB(0, 0, new Color(10, 20, 30, 0).getRGB());
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		StoredGalleryImage stored = storage.store(file("transparent.png", "PNG", image));

		BufferedImage main = webp(storage.resolve(stored.mainPath()));
		assertThat(main.getColorModel().hasAlpha()).isTrue();
		assertThat((main.getRGB(0, 0) >>> 24) & 0xff).isZero();
	}

	@Test
	void rejectsOversizedUploadBeforeOpeningIt() throws IOException {
		MultipartFile file = oversizedFile();

		assertThatIllegalArgumentException().isThrownBy(() -> new GalleryImageStorage(mediaDirectory).store(file));
	}

	@Test
	void rejectsCorruptAndUnsupportedImages() {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(
				new MockMultipartFile("image", "bad.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(
				new MockMultipartFile("image", "document.txt", "text/plain", "not an image".getBytes())));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(file("animation.gif", "GIF", image(10, 10, false))));
	}

	@Test
	void rejectsImagesAbovePixelLimitBeforeRasterDecoding() {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(
				new MockMultipartFile("image", "large.png", "image/png", pngHeader(40_000_001, 1))));
	}

	@Test
	void rejectsTraversalPaths() {
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory);

		assertThatIllegalArgumentException().isThrownBy(() -> storage.resolve("../outside.webp"));
	}

	@Test
	void removesFirstTemporaryOutputWhenSecondWriteFails() throws IOException {
		int[] writes = {0};
		GalleryImageStorage storage = new GalleryImageStorage(mediaDirectory, (image, path) -> {
			if (++writes[0] == 2) throw new UncheckedIOException(new IOException("disk full"));
			try {
				ImageIO.write(image, "webp", path.toFile());
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			}
		});

		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(file("image.png", "PNG", image(800, 400, true))));

		assertThat(countFiles(mediaDirectory.resolve("gallery"))).isZero();
	}

	static Stream<Arguments> imageFixtures() {
		return Stream.of(
				Arguments.of("camera.jpg", "JPEG", image(2000, 1000, false)),
				Arguments.of("camera.png", "PNG", image(2000, 1000, true)),
				Arguments.of("camera.webp", "webp", image(2000, 1000, true)));
	}

	private static MockMultipartFile file(String filename, String format, BufferedImage image) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, output)).isTrue();
		return new MockMultipartFile("image", filename, "application/octet-stream", output.toByteArray());
	}

	private static BufferedImage image(int width, int height, boolean alpha) {
		BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		image.setRGB(width / 2, height / 2, new Color(40, 80, 120, alpha ? 180 : 255).getRGB());
		return image;
	}

	private static BufferedImage webp(Path path) throws IOException {
		return ImageIO.read(path.toFile());
	}

	private static void assertDimensions(BufferedImage image, int width, int height) {
		assertThat(image.getWidth()).isEqualTo(width);
		assertThat(image.getHeight()).isEqualTo(height);
	}

	private static long countFiles(Path directory) throws IOException {
		if (!Files.exists(directory)) return 0;
		try (Stream<Path> paths = Files.list(directory)) {
			return paths.filter(Files::isRegularFile).count();
		}
	}

	private static byte[] pngHeader(int width, int height) {
		ByteBuffer chunk = ByteBuffer.allocate(25);
		chunk.putInt(13).put("IHDR".getBytes()).putInt(width).putInt(height).put((byte) 8).put((byte) 2)
				.put((byte) 0).put((byte) 0).put((byte) 0);
		CRC32 crc = new CRC32();
		crc.update(chunk.array(), 4, 17);
		chunk.putInt((int) crc.getValue());
		ByteBuffer image = ByteBuffer.allocate(8 + chunk.capacity());
		image.put(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}).put(chunk.array());
		return image.array();
	}

	private static MultipartFile oversizedFile() {
		return new MultipartFile() {
			@Override
			public String getName() {
				return "image";
			}

			@Override
			public String getOriginalFilename() {
				return "large.jpg";
			}

			@Override
			public String getContentType() {
				return "image/jpeg";
			}

			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public long getSize() {
				return 10L * 1024 * 1024 + 1;
			}

			@Override
			public byte[] getBytes() {
				throw new AssertionError("Oversized upload must not be read");
			}

			@Override
			public InputStream getInputStream() {
				throw new AssertionError("Oversized upload must not be read");
			}

			@Override
			public void transferTo(File destination) {
				throw new AssertionError("Oversized upload must not be read");
			}
		};
	}
}
