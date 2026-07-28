package myweddinginvitation.webapp.guest;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingContentService;
import myweddinginvitation.webapp.wedding.WeddingPreview;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicInvitationController {
	private final InvitationLinkSigner signer;
	private final GuestRepository guests;
	private final WeddingContentService weddingContent;

	public PublicInvitationController(InvitationLinkSigner signer, GuestRepository guests,
			WeddingContentService weddingContent) {
		this.signer = signer;
		this.guests = guests;
		this.weddingContent = weddingContent;
	}

	@GetMapping("/i/{publicId}/{version}/{signature}")
	String invitation(@PathVariable String publicId, @PathVariable String version, @PathVariable String signature,
			@RequestParam(required = false) String language, Model model, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		UUID guestPublicId;
		long tokenVersion;
		try {
			guestPublicId = UUID.fromString(publicId);
			tokenVersion = Long.parseLong(version);
		} catch (IllegalArgumentException exception) {
			return unavailable(response);
		}
		if (!signer.verify(guestPublicId, tokenVersion, signature)) {
			return unavailable(response);
		}
		Guest guest = guests.findByPublicId(guestPublicId).orElse(null);
		if (guest == null || guest.isArchived() || guest.getInvitationTokenVersion() != tokenVersion) {
			return unavailable(response);
		}
		String selectedLanguage = language == null ? guest.getPreferredLanguage().name()
				: ("EN".equals(language) ? "EN" : "ID");
		WeddingPreview preview = weddingContent.preview(guest.getSalutation(), guest.getDisplayName(), selectedLanguage);
		if (preview.publicationState() != PublicationState.PUBLISHED) {
			return unavailable(response);
		}
		model.addAttribute("preview", preview);
		model.addAttribute("language", selectedLanguage);
		model.addAttribute("accentColor", safeAccent(preview.accentColor()));
		return "guest/invitation";
	}

	private String unavailable(HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		return "guest/unavailable";
	}

	private String safeAccent(String accentColor) {
		return accentColor != null && accentColor.matches("#[0-9A-Fa-f]{6}") ? accentColor : "#7A5C48";
	}
}
