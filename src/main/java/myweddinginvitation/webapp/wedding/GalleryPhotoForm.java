package myweddinginvitation.webapp.wedding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GalleryPhotoForm(
		@NotBlank @Size(max = 300) String altText,
		@Size(max = 500) String captionId,
		@Size(max = 500) String captionEn,
		long version) {
}
