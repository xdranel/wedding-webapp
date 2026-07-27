package myweddinginvitation.webapp.wedding;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeddingContentService {
	private final WeddingSettingsRepository settings;
	private final PartnerRepository partners;
	private final EventPartRepository events;
	private final StoryEntryRepository story;

	public WeddingContentService(WeddingSettingsRepository settings, PartnerRepository partners,
			EventPartRepository events, StoryEntryRepository story) {
		this.settings = settings;
		this.partners = partners;
		this.events = events;
		this.story = story;
	}

	@Transactional(readOnly = true)
	public WeddingOverview overview() {
		WeddingSettings weddingSettings = weddingSettings();
		List<Partner> orderedPartners = partners.findAllByOrderByDisplayOrderAsc();
		List<EventPart> orderedEvents = events.findAllByOrderByTypeAsc();
		List<StoryEntry> orderedStory = story.findAllByOrderByDisplayOrderAsc();
		return new WeddingOverview(weddingSettings.getPublicationState(), settingsComplete(weddingSettings),
				orderedPartners.stream().map(this::isPartnerComplete).toList(),
				orderedEvents.stream().filter(this::isCompleteVisibleEvent).map(EventPart::getType).toList(),
				!orderedStory.isEmpty(), translationWarnings(weddingSettings, orderedPartners, orderedEvents, orderedStory));
	}

	@Transactional(readOnly = true)
	public PublicationCheck checkPublication() {
		WeddingSettings weddingSettings = weddingSettings();
		return new PublicationCheck(weddingSettings.getPublicationState() == PublicationState.PUBLISHED,
				publicationErrors(weddingSettings, partners.findAllByOrderByDisplayOrderAsc(), events.findAllByOrderByTypeAsc()));
	}

	@Transactional
	public PublicationCheck publish() {
		WeddingSettings weddingSettings = weddingSettings();
		List<String> errors = publicationErrors(weddingSettings, partners.findAllByOrderByDisplayOrderAsc(),
				events.findAllByOrderByTypeAsc());
		if (errors.isEmpty()) {
			weddingSettings.publish();
			return new PublicationCheck(true, errors);
		}
		return new PublicationCheck(false, errors);
	}

	@Transactional
	public void returnToDraft() {
		weddingSettings().returnToDraft();
	}

	@Transactional(readOnly = true)
	public WeddingPreview preview(String salutation, String guestName, String language) {
		WeddingSettings weddingSettings = weddingSettings();
		List<Partner> orderedPartners = partners.findAllByOrderByDisplayOrderAsc();
		List<EventPart> orderedEvents = events.findAllByOrderByTypeAsc();
		return new WeddingPreview(weddingSettings.getPublicationState(), coupleTitle(weddingSettings, orderedPartners), salutation,
				guestName, coverDate(orderedEvents), localized(weddingSettings.getOpeningTextId(), weddingSettings.getOpeningTextEn(), language),
				localized(weddingSettings.getClosingTextId(), weddingSettings.getClosingTextEn(), language),
				orderedPartners.stream().map(partner -> new WeddingPreview.PartnerView(partner.getFullName(), partner.getNickname(),
						partner.getPhotoPath(), localized(partner.getChildOfLabelId(), partner.getChildOfLabelEn(), language),
						localized(partner.getParentsNamesId(), partner.getParentsNamesEn(), language), partner.getInstagramUrl())).toList(),
				orderedEvents.stream().filter(EventPart::isVisible).map(event -> new WeddingPreview.EventView(event.getType(), event.getDate(),
						event.getStartTime(), event.getEndTime(), event.getVenueName(),
						localized(event.getAddressId(), event.getAddressEn(), language), event.getMapUrl())).toList(),
				story.findAllByOrderByDisplayOrderAsc().stream().map(entry -> new WeddingPreview.StoryView(entry.getDate(),
						localized(entry.getTitleId(), entry.getTitleEn(), language), localized(entry.getBodyId(), entry.getBodyEn(), language))).toList());
	}

	public static String localized(String indonesian, String english, String language) {
		return "EN".equals(language) && hasText(english) ? english : indonesian;
	}

	private WeddingSettings weddingSettings() {
		return settings.getSingleton().orElseThrow();
	}

	private List<String> publicationErrors(WeddingSettings weddingSettings, List<Partner> orderedPartners, List<EventPart> orderedEvents) {
		List<String> errors = new ArrayList<>();
		for (int displayOrder = 1; displayOrder <= 2; displayOrder++) {
			int partnerOrder = displayOrder;
			Partner partner = orderedPartners.stream().filter(candidate -> candidate.getDisplayOrder() == partnerOrder).findFirst().orElse(null);
			if (partner == null) {
				errors.add("Partner " + displayOrder + ": profile is required");
			} else {
				partnerErrors(partner, errors);
			}
		}
		if (!isValidZone(weddingSettings.getTimeZone())) {
			errors.add("Time zone is invalid");
		}
		for (EventPart event : orderedEvents) {
			if (event.isVisible()) {
				eventErrors(event, errors);
			}
		}
		if (orderedEvents.stream().noneMatch(this::isCompleteVisibleEvent)) {
			errors.add("At least one complete event must be visible");
		}
		return errors;
	}

	private void partnerErrors(Partner partner, List<String> errors) {
		String label = "Partner " + partner.getDisplayOrder() + ": ";
		if (!hasText(partner.getFullName())) errors.add(label + "full name is required");
		if (!hasText(partner.getNickname())) errors.add(label + "nickname is required");
		if (!hasText(partner.getPhotoPath())) errors.add(label + "photo is required");
		if (!hasText(partner.getChildOfLabelId())) errors.add(label + "Indonesian child-of label is required");
		if (!hasText(partner.getParentsNamesId())) errors.add(label + "Indonesian parents' names are required");
	}

	private void eventErrors(EventPart event, List<String> errors) {
		String label = eventLabel(event) + ": ";
		if (event.getDate() == null) errors.add(label + "date is required");
		if (event.getStartTime() == null) errors.add(label + "start time is required");
		if (!hasText(event.getVenueName())) errors.add(label + "venue name is required");
		if (!hasText(event.getAddressId())) errors.add(label + "Indonesian address is required");
		if (!isHttpUrl(event.getMapUrl())) errors.add(label + "map URL must use HTTP or HTTPS");
		if (event.getStartTime() != null && event.getEndTime() != null && !event.getEndTime().isAfter(event.getStartTime())) {
			errors.add(label + "end time must be after start time");
		}
	}

	private boolean isPartnerComplete(Partner partner) {
		return hasText(partner.getFullName()) && hasText(partner.getNickname()) && hasText(partner.getPhotoPath())
				&& hasText(partner.getChildOfLabelId()) && hasText(partner.getParentsNamesId());
	}

	private boolean isCompleteVisibleEvent(EventPart event) {
		return event.isVisible() && event.getDate() != null && event.getStartTime() != null && hasText(event.getVenueName())
				&& hasText(event.getAddressId()) && isHttpUrl(event.getMapUrl())
				&& (event.getEndTime() == null || event.getEndTime().isAfter(event.getStartTime()));
	}

	private boolean settingsComplete(WeddingSettings weddingSettings) {
		return hasText(weddingSettings.getOpeningTextId()) && hasText(weddingSettings.getClosingTextId())
				&& isValidZone(weddingSettings.getTimeZone());
	}

	private List<String> translationWarnings(WeddingSettings weddingSettings, List<Partner> orderedPartners, List<EventPart> orderedEvents,
			List<StoryEntry> orderedStory) {
		List<String> warnings = new ArrayList<>();
		if (!hasText(weddingSettings.getOpeningTextEn())) warnings.add("Opening text: English translation is missing");
		if (!hasText(weddingSettings.getClosingTextEn())) warnings.add("Closing text: English translation is missing");
		for (Partner partner : orderedPartners) {
			String label = "Partner " + partner.getDisplayOrder() + ": ";
			if (!hasText(partner.getChildOfLabelEn())) warnings.add(label + "English child-of label is missing");
			if (!hasText(partner.getParentsNamesEn())) warnings.add(label + "English parents' names are missing");
		}
		for (EventPart event : orderedEvents) {
			if (event.isVisible() && !hasText(event.getAddressEn())) warnings.add(eventLabel(event) + ": English address is missing");
		}
		for (StoryEntry entry : orderedStory) {
			String label = "Story " + entry.getDisplayOrder() + ": ";
			if (!hasText(entry.getTitleEn())) warnings.add(label + "English title is missing");
			if (!hasText(entry.getBodyEn())) warnings.add(label + "English body is missing");
		}
		return warnings;
	}

	private String coupleTitle(WeddingSettings weddingSettings, List<Partner> orderedPartners) {
		if (hasText(weddingSettings.getCoupleTitle())) return weddingSettings.getCoupleTitle();
		return orderedPartners.stream().map(Partner::getNickname).filter(WeddingContentService::hasText).collect(java.util.stream.Collectors.joining(" & "));
	}

	private LocalDate coverDate(List<EventPart> orderedEvents) {
		return orderedEvents.stream().filter(EventPart::isVisible).map(EventPart::getDate).filter(java.util.Objects::nonNull)
				.min(Comparator.naturalOrder()).orElse(null);
	}

	private boolean isValidZone(String value) {
		if (!hasText(value)) return false;
		try {
			ZoneId.of(value);
			return true;
		} catch (DateTimeException exception) {
			return false;
		}
	}

	private boolean isHttpUrl(String value) {
		if (!hasText(value)) return false;
		try {
			String scheme = URI.create(value).getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private String eventLabel(EventPart event) {
		return event.getType().name().substring(0, 1) + event.getType().name().substring(1).toLowerCase(Locale.ROOT);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
