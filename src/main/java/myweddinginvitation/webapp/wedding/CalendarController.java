package myweddinginvitation.webapp.wedding;

import myweddinginvitation.webapp.guest.InvitationAccessService;
import myweddinginvitation.webapp.guest.InvitationAccessService.Access;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
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
public class CalendarController {
	private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");
	private final InvitationAccessService invitations;
	private final InvitationLinkSigner signer;
	private final CalendarService calendars;

	public CalendarController(InvitationAccessService invitations, InvitationLinkSigner signer,
			CalendarService calendars) {
		this.invitations = invitations;
		this.signer = signer;
		this.calendars = calendars;
	}

	@GetMapping("/i/{publicId}/{version}/{signature}/calendar/{eventType}.ics")
	ResponseEntity<byte[]> download(@PathVariable String publicId, @PathVariable String version,
			@PathVariable String signature, @PathVariable String eventType,
			@RequestParam(required = false) String language) {
		HttpHeaders headers = new HttpHeaders();
		headers.setCacheControl(CacheControl.noStore());
		Access access = invitations.resolve(publicId, version, signature, language);
		if (access == null || access.wedding().isEventClosed()
				|| !access.wedding().isCalendarDownloadsEnabled()) return unavailable(headers);
		EventType type;
		try {
			type = EventType.valueOf(eventType);
		} catch (IllegalArgumentException exception) {
			return unavailable(headers);
		}
		CalendarFile file = calendars.create(access.preview(), type, access.language(), signer.urlFor(access.guest()),
				access.wedding().getTimeZone()).orElse(null);
		if (file == null) return unavailable(headers);
		headers.setContentType(CALENDAR);
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"");
		return ResponseEntity.ok().headers(headers).body(file.content());
	}

	private ResponseEntity<byte[]> unavailable(HttpHeaders headers) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).headers(headers).build();
	}
}
