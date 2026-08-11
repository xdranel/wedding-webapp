package myweddinginvitation.webapp.wedding;

import java.util.List;

public record WeddingMediaView(
		boolean galleryEnabled,
		boolean audioEnabled,
		long weddingVersion,
		List<Photo> photos) {
	public record Photo(
			long id,
			long version,
			String altText,
			String caption,
			String captionId,
			String captionEn,
			String thumbnailUrl,
			String imageUrl) {
	}
}
