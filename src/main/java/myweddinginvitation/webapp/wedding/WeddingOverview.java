package myweddinginvitation.webapp.wedding;

import java.util.List;

public record WeddingOverview(
		long version,
		PublicationState publicationState,
		boolean settingsComplete,
		List<Boolean> partnerComplete,
		List<EventType> completeVisibleEvents,
		boolean storyPresent,
		List<String> translationWarnings) {
	public WeddingOverview {
		partnerComplete = List.copyOf(partnerComplete);
		completeVisibleEvents = List.copyOf(completeVisibleEvents);
		translationWarnings = List.copyOf(translationWarnings);
	}
}
