package myweddinginvitation.webapp.wedding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WeddingMediaController {
	private static final MediaType WEBP = MediaType.valueOf("image/webp");
	private static final MediaType MP3 = MediaType.valueOf("audio/mpeg");
	private final GalleryPhotoRepository photos;
	private final WeddingSettingsRepository settings;
	private final PartnerRepository partners;
	private final GalleryImageStorage galleryStorage;
	private final WeddingAudioStorage audioStorage;
	private final PartnerPhotoStorage partnerStorage;

	public WeddingMediaController(GalleryPhotoRepository photos, WeddingSettingsRepository settings, PartnerRepository partners,
			GalleryImageStorage galleryStorage, WeddingAudioStorage audioStorage, PartnerPhotoStorage partnerStorage) {
		this.photos = photos;
		this.settings = settings;
		this.partners = partners;
		this.galleryStorage = galleryStorage;
		this.audioStorage = audioStorage;
		this.partnerStorage = partnerStorage;
	}

	@GetMapping("/media/gallery/{id}/thumbnail")
	ResponseEntity<FileSystemResource> thumbnail(@PathVariable String id) {
		GalleryPhoto photo = photo(id);
		return photo == null ? notFound() : media(() -> galleryStorage.resolve(photo.getThumbnailPath()), WEBP);
	}

	@GetMapping("/media/gallery/{id}/image")
	ResponseEntity<FileSystemResource> image(@PathVariable String id) {
		GalleryPhoto photo = photo(id);
		return photo == null ? notFound() : media(() -> galleryStorage.resolve(photo.getMainPath()), WEBP);
	}

	@GetMapping("/media/wedding/audio")
	ResponseEntity<FileSystemResource> audio() {
		WeddingSettings wedding = settings.getSingleton().orElse(null);
		if (wedding == null || !wedding.isBackgroundAudioEnabled()) return notFound();
		return media(() -> audioStorage.resolve(wedding.getBackgroundAudioPath()), MP3);
	}

	@GetMapping("/media/wedding/cover")
	ResponseEntity<FileSystemResource> cover() {
		WeddingSettings wedding = settings.getSingleton().orElse(null);
		if (wedding == null || wedding.getInvitationCoverPath() == null
				|| wedding.getInvitationCoverPath().isBlank()) return notFound();
		return media(() -> galleryStorage.resolveCover(wedding.getInvitationCoverPath()), WEBP);
	}

	@GetMapping("/media/partner/{id}")
	ResponseEntity<FileSystemResource> partner(@PathVariable String id) {
		Long partnerId = id(id);
		Partner partner = partnerId == null ? null : partners.findById(partnerId).orElse(null);
		if (partner == null || partner.getPhotoPath() == null || partner.getPhotoPath().isBlank()) return notFound();
		try {
			return media(() -> partnerStorage.resolve(partner.getPhotoPath()), partnerStorage.contentType(partner.getPhotoPath()));
		} catch (RuntimeException exception) {
			return notFound();
		}
	}

	@GetMapping("/media/**")
	ResponseEntity<Void> unavailableMedia() {
		return ResponseEntity.notFound().build();
	}

	private GalleryPhoto photo(String id) {
		Long photoId = id(id);
		return photoId == null ? null : photos.findById(photoId).orElse(null);
	}

	private Long id(String value) {
		try {
			return Long.valueOf(value);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private ResponseEntity<FileSystemResource> media(Supplier<Path> pathSupplier, MediaType contentType) {
		try {
			Path path = pathSupplier.get();
			if (!Files.isRegularFile(path)) return notFound();
			return ResponseEntity.ok().contentType(contentType).cacheControl(CacheControl.noCache())
					.header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
		} catch (RuntimeException exception) {
			return notFound();
		}
	}

	private ResponseEntity<FileSystemResource> notFound() {
		return ResponseEntity.notFound().build();
	}

}
