package myweddinginvitation.webapp.messaging;

import java.net.URI;
import java.time.Instant;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.wedding.EventType;
import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingContentService;
import myweddinginvitation.webapp.wedding.WeddingPreview;
import myweddinginvitation.webapp.wedding.WeddingSettings;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GuestDeliveryService {
	private final GuestService guests;
	private final InvitationLinkSigner signer;
	private final MessageTemplateService templates;
	private final WeddingContentService weddingContent;
	private final WeddingSettingsRepository settings;

	public GuestDeliveryService(GuestService guests, InvitationLinkSigner signer,
			MessageTemplateService templates, WeddingContentService weddingContent, WeddingSettingsRepository settings) {
		this.guests = guests;
		this.signer = signer;
		this.templates = templates;
		this.weddingContent = weddingContent;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public URI whatsappUri(long guestId, MessageLanguage language) {
		requireOpen();
		Guest guest = guests.get(guestId);
		requireActive(guest);
		WeddingPreview preview = weddingContent.preview(
				guest.getSalutation(), guest.getDisplayName(), language.name());
		WeddingPreview.EventView ceremony = event(preview, EventType.CEREMONY);
		WeddingPreview.EventView reception = event(preview, EventType.RECEPTION);
		String message = templates.render(MessageType.INVITATION, language, new MessageTemplateValues(
				guest.getSalutation(), guest.getDisplayName(), preview.coupleTitle(), signer.urlFor(guest, language),
				value(weddingContent.settingsForm().getRsvpDeadline()),
				date(ceremony), location(ceremony), date(reception), location(reception)));
		return UriComponentsBuilder.fromUriString("https://wa.me")
				.pathSegment(guest.getNormalizedWhatsappNumber().substring(1))
				.queryParam("text", message)
				.build().encode().toUri();
	}

	@Transactional
	public void confirmSent(long guestId, long version, Instant now) {
		requireOpen(settings.findSingletonForUpdate().orElseThrow());
		guests.confirmSent(guestId, version, now);
	}

	private void requireOpen() {
		requireOpen(settings.getSingleton().orElseThrow());
	}

	private void requireOpen(WeddingSettings wedding) {
		if (wedding.getPublicationState() != PublicationState.PUBLISHED) {
			throw new IllegalStateException("Initial delivery requires a published wedding.");
		}
		if (wedding.isEventClosed()) throw new IllegalStateException("Initial delivery requires an open wedding.");
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

	private String value(Object value) {
		return value == null ? null : value.toString();
	}

	private void requireActive(Guest guest) {
		if (guest.isArchived()) {
			throw new IllegalStateException("Delivery is disabled for archived guests.");
		}
	}
}
