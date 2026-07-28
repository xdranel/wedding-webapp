package myweddinginvitation.webapp.wedding;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record WeddingPreview(
		PublicationState publicationState,
		String coupleTitle,
		String salutation,
		String guestName,
		LocalDate coverDate,
		String accentColor,
		FontPreset fontPreset,
		String openingText,
		String closingText,
		List<PartnerView> partners,
		List<EventView> events,
		List<StoryView> story) {
	public WeddingPreview {
		partners = List.copyOf(partners);
		events = List.copyOf(events);
		story = List.copyOf(story);
	}

	public record PartnerView(
			String fullName,
			String nickname,
			String photoPath,
			String childOfLabel,
			String parentsNames,
			String instagramUrl) {
	}

	public record EventView(
			EventType type,
			LocalDate date,
			LocalTime startTime,
			LocalTime endTime,
			String venueName,
			String address,
			String mapUrl) {
	}

	public record StoryView(LocalDate date, String title, String body) {
	}
}
