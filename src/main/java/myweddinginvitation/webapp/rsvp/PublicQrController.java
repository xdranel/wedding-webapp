package myweddinginvitation.webapp.rsvp;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import myweddinginvitation.webapp.guest.InvitationAccessService;
import myweddinginvitation.webapp.guest.InvitationAccessService.Access;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicQrController {
	private final InvitationAccessService invitations;
	private final RsvpRepository rsvps;
	private final GuestVerificationSession verification;
	private final CheckInQrSigner signer;
	private final QrImageService images;

	public PublicQrController(InvitationAccessService invitations, RsvpRepository rsvps,
			GuestVerificationSession verification, CheckInQrSigner signer, QrImageService images) {
		this.invitations = invitations;
		this.rsvps = rsvps;
		this.verification = verification;
		this.signer = signer;
		this.images = images;
	}

	@GetMapping(value = "/i/{publicId}/{version}/{signature}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
	ResponseEntity<byte[]> display(@PathVariable String publicId, @PathVariable String version,
			@PathVariable String signature, @RequestParam(required = false) String language,
			HttpServletRequest request) {
		return image(publicId, version, signature, language, request, 320, false);
	}

	@GetMapping(value = "/i/{publicId}/{version}/{signature}/qr-download.png",
			produces = MediaType.IMAGE_PNG_VALUE)
	ResponseEntity<byte[]> download(@PathVariable String publicId, @PathVariable String version,
			@PathVariable String signature, @RequestParam(required = false) String language,
			HttpServletRequest request) {
		return image(publicId, version, signature, language, request, 1024, true);
	}

	private ResponseEntity<byte[]> image(String publicId, String version, String signature, String language,
			HttpServletRequest request, int size, boolean download) {
		HttpHeaders headers = new HttpHeaders();
		headers.setCacheControl(CacheControl.noStore());
		Access access = invitations.resolve(publicId, version, signature, language);
		if (access == null || access.wedding().isEventClosed()) return denied(headers);
		Rsvp rsvp = rsvps.findByGuestId(access.guest().getId()).orElse(null);
		if (rsvp == null || rsvp.getResponse() != AttendanceResponse.HADIR) return denied(headers);

		HttpSession session = request.getSession(false);
		if (session == null || !verification.verified(session, access.guest().getPublicId(),
				access.guest().getInvitationTokenVersion(), access.guest().getNormalizedWhatsappNumber())) {
			headers.setLocation(URI.create(pinRedirect(publicId, version, signature,
					language == null ? null : access.language())));
			return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
		}

		headers.setContentType(MediaType.IMAGE_PNG);
		if (download) headers.set(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"wedding-check-in-qr.png\"");
		String payload = signer.payload(access.guest().getPublicId(), access.guest().getInvitationTokenVersion());
		return ResponseEntity.ok().headers(headers).body(images.png(payload, size));
	}

	private ResponseEntity<byte[]> denied(HttpHeaders headers) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).headers(headers).build();
	}

	private String pinRedirect(String publicId, String version, String signature, String language) {
		String path = "/i/" + publicId + "/" + version + "/" + signature;
		return path + (language == null ? "?qrPinRequired" : "?language=" + language + "&qrPinRequired");
	}
}
