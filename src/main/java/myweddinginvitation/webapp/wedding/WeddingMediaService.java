package myweddinginvitation.webapp.wedding;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WeddingMediaService {
	private static final int MAX_PHOTOS = 10;
	private final WeddingSettingsRepository settings;
	private final GalleryPhotoRepository photos;
	private final GalleryImageStorage storage;
	private final WeddingAudioStorage audio;

	public WeddingMediaService(WeddingSettingsRepository settings, GalleryPhotoRepository photos,
			GalleryImageStorage storage, WeddingAudioStorage audio) {
		this.settings = settings;
		this.photos = photos;
		this.storage = storage;
		this.audio = audio;
	}

	@Transactional(readOnly = true)
	public WeddingMediaView adminView() {
		return view("ID");
	}

	@Transactional(readOnly = true)
	public WeddingMediaView publicView(String language) {
		return view(language);
	}

	@Transactional
	public long addPhoto(MultipartFile file, GalleryPhotoForm form) {
		String altText = requiredAlt(form.altText());
		String captionId = normalized(form.captionId());
		String captionEn = normalized(form.captionEn());
		lockWedding();
		List<GalleryPhoto> ordered = photos.findAllByOrderByPositionAsc();
		if (ordered.size() >= MAX_PHOTOS) throw new IllegalStateException("Gallery allows at most ten photos");
		StoredGalleryImage image = storage.store(file);
		cleanUpAfterTransaction(() -> storage.delete(image), null);
		return photos.saveAndFlush(GalleryPhoto.create(ordered.size(), image.mainPath(), image.thumbnailPath(),
				altText, captionId, captionEn)).getId();
	}

	@Transactional
	public void updatePhoto(long id, GalleryPhotoForm form) {
		String altText = requiredAlt(form.altText());
		String captionId = normalized(form.captionId());
		String captionEn = normalized(form.captionEn());
		GalleryPhoto photo = lockedPhoto(id, form.version());
		photo.updateMetadata(altText, captionId, captionEn);
		photos.saveAndFlush(photo);
	}

	@Transactional
	public void replacePhoto(long id, long version, MultipartFile file) {
		GalleryPhoto photo = lockedPhoto(id, version);
		StoredGalleryImage oldImage = stored(photo);
		StoredGalleryImage newImage = storage.store(file);
		cleanUpAfterTransaction(() -> storage.delete(newImage), () -> storage.deleteAfterCommit(oldImage));
		photo.replacePaths(newImage.mainPath(), newImage.thumbnailPath());
		photos.saveAndFlush(photo);
	}

	@Transactional
	public void movePhoto(long id, long version, int direction) {
		if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
		lockWedding();
		List<GalleryPhoto> ordered = photos.findAllByOrderByPositionAsc();
		int index = java.util.stream.IntStream.range(0, ordered.size())
				.filter(candidate -> ordered.get(candidate).getId() == id).findFirst().orElseThrow();
		GalleryPhoto selected = ordered.get(index);
		requireVersion(selected, version);
		int adjacent = index + direction;
		if (adjacent < 0 || adjacent >= ordered.size()) return;
		GalleryPhoto neighbor = ordered.get(adjacent);
		int selectedPosition = selected.getPosition();
		int neighborPosition = neighbor.getPosition();
		selected.moveTo(-1);
		photos.saveAndFlush(selected);
		neighbor.moveTo(selectedPosition);
		photos.saveAndFlush(neighbor);
		selected.moveTo(neighborPosition);
		photos.saveAndFlush(selected);
	}

	@Transactional
	public void deletePhoto(long id, long version) {
		WeddingSettings wedding = lockWedding();
		List<GalleryPhoto> ordered = photos.findAllByOrderByPositionAsc();
		GalleryPhoto deleted = ordered.stream().filter(photo -> photo.getId() == id).findFirst().orElseThrow();
		requireVersion(deleted, version);
		cleanUpAfterTransaction(null, () -> storage.deleteAfterCommit(stored(deleted)));
		photos.delete(deleted);
		photos.flush();
		int position = 0;
		for (GalleryPhoto photo : ordered) {
			if (photo == deleted) continue;
			if (photo.getPosition() != position) {
				photo.moveTo(position);
				photos.saveAndFlush(photo);
			}
			position++;
		}
		if (position == 0) {
			wedding.setGalleryEnabled(false, false);
			settings.saveAndFlush(wedding);
		}
	}

	@Transactional
	public void setGalleryEnabled(long weddingVersion, boolean enabled) {
		WeddingSettings wedding = lockWedding();
		if (wedding.getVersion() != weddingVersion) {
			throw new OptimisticLockingFailureException("Wedding settings have changed");
		}
		wedding.setGalleryEnabled(enabled, photos.count() > 0);
		settings.saveAndFlush(wedding);
	}

	@Transactional
	public void replaceAudio(MultipartFile file) {
		WeddingSettings wedding = lockWedding();
		String oldPath = wedding.getBackgroundAudioPath();
		String newPath = audio.store(file);
		cleanUpAfterTransaction(() -> audio.delete(newPath),
				oldPath == null ? null : () -> audio.deleteAfterCommit(oldPath));
		wedding.replaceBackgroundAudio(newPath);
		settings.saveAndFlush(wedding);
	}

	@Transactional
	public void deleteAudio(long weddingVersion) {
		WeddingSettings wedding = lockWedding();
		requireWeddingVersion(wedding, weddingVersion);
		String oldPath = wedding.removeBackgroundAudio();
		cleanUpAfterTransaction(null, oldPath == null ? null : () -> audio.deleteAfterCommit(oldPath));
		settings.saveAndFlush(wedding);
	}

	@Transactional
	public void setAudioEnabled(long weddingVersion, boolean enabled) {
		WeddingSettings wedding = lockWedding();
		requireWeddingVersion(wedding, weddingVersion);
		wedding.setBackgroundAudioEnabled(enabled);
		settings.saveAndFlush(wedding);
	}

	private WeddingMediaView view(String language) {
		WeddingSettings wedding = settings.getSingleton().orElseThrow();
		return new WeddingMediaView(wedding.isGalleryEnabled(), wedding.isBackgroundAudioEnabled(), wedding.getVersion(),
				photos.findAllByOrderByPositionAsc().stream().map(photo -> new WeddingMediaView.Photo(
						photo.getId(), photo.getVersion(), photo.getAltText(), caption(photo, language),
						"/media/gallery/" + photo.getId() + "/thumbnail",
						"/media/gallery/" + photo.getId() + "/image")).toList());
	}

	private WeddingSettings lockWedding() {
		return settings.findSingletonForUpdate().orElseThrow();
	}

	private GalleryPhoto lockedPhoto(long id, long version) {
		GalleryPhoto photo = photos.findByIdForUpdate(id).orElseThrow();
		requireVersion(photo, version);
		return photo;
	}

	private static void requireVersion(GalleryPhoto photo, long version) {
		if (photo.getVersion() != version) throw new OptimisticLockingFailureException("Gallery photo has changed");
	}

	private static void requireWeddingVersion(WeddingSettings wedding, long version) {
		if (wedding.getVersion() != version) throw new OptimisticLockingFailureException("Wedding settings have changed");
	}

	private static String requiredAlt(String value) {
		String normalized = normalized(value);
		if (normalized == null) throw new IllegalArgumentException("Alternative text is required");
		return normalized;
	}

	private static String normalized(String value) {
		if (value == null) return null;
		String normalized = value.strip();
		return normalized.isEmpty() ? null : normalized;
	}

	private static String caption(GalleryPhoto photo, String language) {
		if ("EN".equals(language)) {
			return photo.getCaptionEn() != null ? photo.getCaptionEn() : photo.getCaptionId();
		}
		return photo.getCaptionId() != null ? photo.getCaptionId() : photo.getCaptionEn();
	}

	private static StoredGalleryImage stored(GalleryPhoto photo) {
		return new StoredGalleryImage(photo.getMainPath(), photo.getThumbnailPath());
	}

	private void cleanUpAfterTransaction(Runnable added, Runnable obsolete) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				if (obsolete != null) obsolete.run();
			}

			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED && added != null) added.run();
			}
		});
	}
}
