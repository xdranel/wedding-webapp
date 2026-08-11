package myweddinginvitation.webapp.messaging;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.guest.WhatsappNumberService;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import myweddinginvitation.webapp.wedding.EventType;
import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingContentService;
import myweddinginvitation.webapp.wedding.WeddingPreview;
import myweddinginvitation.webapp.wedding.WeddingSettings;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ReminderService {
	private static final int MAX_QUEUE_GUESTS = 2_000;
	private final GuestRepository guests;
	private final RsvpRepository rsvps;
	private final WeddingSettingsRepository settings;
	private final InvitationLinkSigner signer;
	private final MessageTemplateService templates;
	private final WeddingContentService weddingContent;
	private final WhatsappNumberService numbers;

	public ReminderService(GuestRepository guests, RsvpRepository rsvps, WeddingSettingsRepository settings,
			InvitationLinkSigner signer, MessageTemplateService templates, WeddingContentService weddingContent,
			WhatsappNumberService numbers) {
		this.guests = guests;
		this.rsvps = rsvps;
		this.settings = settings;
		this.signer = signer;
		this.templates = templates;
		this.weddingContent = weddingContent;
		this.numbers = numbers;
	}

	@Transactional(readOnly = true)
	public List<ReminderGuestView> queue(ReminderKind kind, Long categoryId) {
		WeddingSettings wedding = wedding();
		requireContent(kind, wedding, null, MessageLanguage.ID);
		// ponytail: bounded single-wedding scan; add a database projection only if guest volume exceeds the documented 2,000 limit.
		List<Guest> allGuests = guests.findAllForReminderQueue(PageRequest.of(0, MAX_QUEUE_GUESTS + 1));
		if (allGuests.size() > MAX_QUEUE_GUESTS) {
			throw new IllegalStateException("Reminder queues support at most 2,000 guests.");
		}
		Map<Long, Rsvp> rsvpsByGuest = byGuest(allGuests.stream().map(Guest::getId).toList());
		return allGuests.stream()
				.filter(guest -> categoryId == null || (guest.getCategory() != null
						&& Objects.equals(guest.getCategory().getId(), categoryId)))
				.filter(guest -> eligible(guest, rsvpsByGuest.get(guest.getId()), kind))
				.map(guest -> view(guest, rsvpsByGuest.get(guest.getId()), kind))
				.sorted(Comparator.comparing((ReminderGuestView view) -> view.lastSentAt() != null)
						.thenComparing(ReminderGuestView::displayName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(ReminderGuestView::id))
				.toList();
	}

	@Transactional(readOnly = true)
	public URI whatsappUri(long guestId, ReminderKind kind, MessageLanguage language) {
		Guest guest = guests.findById(guestId).orElseThrow();
		WeddingSettings wedding = wedding();
		Rsvp rsvp = rsvps.findByGuestId(guestId).orElse(null);
		requireEligible(guest, rsvp, kind, wedding, language);
		WeddingPreview preview = weddingContent.preview(guest.getSalutation(), guest.getDisplayName(), language.name());
		String message = templates.render(type(kind), language, new MessageTemplateValues(
				guest.getSalutation(), guest.getDisplayName(), preview.coupleTitle(), signer.urlFor(guest),
				value(wedding.getRsvpDeadline()), date(event(preview, EventType.CEREMONY)),
				location(event(preview, EventType.CEREMONY)), date(event(preview, EventType.RECEPTION)),
				location(event(preview, EventType.RECEPTION))));
		return UriComponentsBuilder.fromUriString("https://wa.me")
				.pathSegment(guest.getNormalizedWhatsappNumber().substring(1))
				.queryParam("text", message).build().encode().toUri();
	}

	@Transactional
	public Long confirmSent(long guestId, long version, ReminderKind kind, Long categoryId, Instant sentAt) {
		Guest guest = guests.findByIdForUpdate(guestId).orElseThrow();
		if (guest.getVersion() != version) throw new OptimisticLockingFailureException("Guest has changed");
		WeddingSettings wedding = wedding();
		Rsvp rsvp = rsvps.findByGuestId(guestId).orElse(null);
		requireEligible(guest, rsvp, kind, wedding, guest.getPreferredLanguage());
		if (kind == ReminderKind.RSVP) guest.confirmRsvpReminder(sentAt);
		else guest.confirmEventReminder(sentAt);
		guests.saveAndFlush(guest);
		return queue(kind, categoryId).stream().map(ReminderGuestView::id)
				.filter(id -> id != guestId).findFirst().orElse(null);
	}

	private Map<Long, Rsvp> byGuest(Collection<Long> guestIds) {
		if (guestIds.isEmpty()) return Map.of();
		return rsvps.findByGuestIdIn(guestIds).stream()
				.collect(Collectors.toMap(rsvp -> rsvp.getGuest().getId(), Function.identity()));
	}

	private void requireEligible(Guest guest, Rsvp rsvp, ReminderKind kind, WeddingSettings wedding,
			MessageLanguage language) {
		requireContent(kind, wedding, guest, language);
		if (!eligible(guest, rsvp, kind)) throw new IllegalStateException("Guest is not eligible for this reminder.");
	}

	private void requireContent(ReminderKind kind, WeddingSettings wedding, Guest guest, MessageLanguage language) {
		if (kind == null) throw new IllegalArgumentException("Reminder kind is required.");
		if (wedding.getPublicationState() != PublicationState.PUBLISHED) {
			throw new IllegalStateException("Reminders require a published wedding.");
		}
		if (kind == ReminderKind.RSVP && wedding.getRsvpDeadline() == null) {
			throw new IllegalStateException("RSVP reminders require a deadline.");
		}
		if (kind == ReminderKind.EVENT) {
			WeddingPreview preview = weddingContent.preview(guest == null ? "" : guest.getSalutation(),
					guest == null ? "" : guest.getDisplayName(), language.name());
			if (preview.events().stream().noneMatch(this::complete)) {
				throw new IllegalStateException("Event reminders require a complete visible event.");
			}
		}
	}

	private boolean eligible(Guest guest, Rsvp rsvp, ReminderKind kind) {
		if (guest.isArchived() || !numbers.isValidE164(guest.getNormalizedWhatsappNumber())) return false;
		return kind == ReminderKind.RSVP ? rsvp == null
				: rsvp != null && rsvp.getResponse() == AttendanceResponse.HADIR;
	}

	private ReminderGuestView view(Guest guest, Rsvp rsvp, ReminderKind kind) {
		return new ReminderGuestView(guest.getId(), guest.getVersion(), guest.getDisplayName(),
				guest.getCategory() == null ? null : guest.getCategory().getDisplayName(),
				guest.getNormalizedWhatsappNumber(), guest.getPreferredLanguage(),
				rsvp == null ? null : rsvp.getResponse(),
				kind == ReminderKind.RSVP ? guest.getLastRsvpReminderSentAt() : guest.getLastEventReminderSentAt());
	}

	private WeddingSettings wedding() {
		return settings.getSingleton().orElseThrow();
	}

	private MessageType type(ReminderKind kind) {
		return kind == ReminderKind.RSVP ? MessageType.RSVP_REMINDER : MessageType.EVENT_REMINDER;
	}

	private boolean complete(WeddingPreview.EventView event) {
		return event.date() != null && event.startTime() != null && hasText(event.venueName()) && hasText(event.address())
				&& httpUrl(event.mapUrl()) && (event.endTime() == null || event.endTime().isAfter(event.startTime()));
	}

	private WeddingPreview.EventView event(WeddingPreview preview, EventType type) {
		return preview.events().stream().filter(candidate -> candidate.type() == type).findFirst().orElse(null);
	}

	private String date(WeddingPreview.EventView event) {
		return event == null ? null : value(event.date());
	}

	private String location(WeddingPreview.EventView event) {
		return event == null ? null : event.venueName();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static boolean httpUrl(String value) {
		if (!hasText(value)) return false;
		try {
			URI uri = URI.create(value);
			return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
					|| "https".equalsIgnoreCase(uri.getScheme()));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static String value(Object value) {
		return value == null ? null : value.toString();
	}
}
