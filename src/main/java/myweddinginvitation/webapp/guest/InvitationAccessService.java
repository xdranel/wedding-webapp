package myweddinginvitation.webapp.guest;

import java.util.UUID;

import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingContentService;
import myweddinginvitation.webapp.wedding.WeddingPreview;
import myweddinginvitation.webapp.wedding.WeddingSettings;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationAccessService {
	private final InvitationLinkSigner signer;
	private final GuestRepository guests;
	private final WeddingSettingsRepository settings;
	private final WeddingContentService weddingContent;

	public InvitationAccessService(InvitationLinkSigner signer, GuestRepository guests,
			WeddingSettingsRepository settings, WeddingContentService weddingContent) {
		this.signer = signer;
		this.guests = guests;
		this.settings = settings;
		this.weddingContent = weddingContent;
	}

	@Transactional(readOnly = true)
	public Access resolve(String publicId, String version, String signature, String requestedLanguage) {
		UUID guestPublicId;
		long tokenVersion;
		try {
			guestPublicId = UUID.fromString(publicId);
			tokenVersion = Long.parseLong(version);
		} catch (IllegalArgumentException exception) {
			return null;
		}
		if (!signer.verify(guestPublicId, tokenVersion, signature)) return null;
		Guest guest = guests.findByPublicId(guestPublicId).orElse(null);
		if (guest == null || guest.isArchived() || guest.getInvitationTokenVersion() != tokenVersion) return null;
		WeddingSettings wedding = settings.getSingleton().orElseThrow();
		if (wedding.getPublicationState() != PublicationState.PUBLISHED) return null;
		String language = requestedLanguage == null ? guest.getPreferredLanguage().name()
				: ("EN".equals(requestedLanguage) ? "EN" : "ID");
		WeddingPreview preview = weddingContent.preview(guest.getSalutation(), guest.getDisplayName(), language);
		return new Access(guest, wedding, preview, language);
	}

	public record Access(Guest guest, WeddingSettings wedding, WeddingPreview preview, String language) {
	}
}
