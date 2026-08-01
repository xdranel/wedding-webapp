package myweddinginvitation.webapp.guest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import myweddinginvitation.webapp.guest.InvitationAccessService.Access;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.GuestRsvpForm;
import myweddinginvitation.webapp.rsvp.GuestVerificationSession;
import myweddinginvitation.webapp.rsvp.PublicGreetingView;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpView;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicInvitationController {
	private final InvitationAccessService access;
	private final RsvpService rsvps;
	private final GuestVerificationSession verification;
	private final Clock clock;

	public PublicInvitationController(InvitationAccessService access, RsvpService rsvps,
			GuestVerificationSession verification, Clock clock) {
		this.access = access;
		this.rsvps = rsvps;
		this.verification = verification;
		this.clock = clock;
	}

	@GetMapping("/i/{publicId}/{version}/{signature}")
	String invitation(@PathVariable String publicId, @PathVariable String version, @PathVariable String signature,
			@RequestParam(required = false) String language, @RequestParam(defaultValue = "0") int page,
			Model model, HttpSession session, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		Access resolved = access.resolve(publicId, version, signature, language);
		if (resolved == null) return unavailable(response);
		if (resolved.wedding().isEventClosed()) return closed(resolved.language(), model);
		return render(resolved, invitationPath(publicId, version, signature), null, page, model, session);
	}

	public String render(Access access, String invitationPath, GuestRsvpForm submitted, int page,
			Model model, HttpSession session) {
		RsvpView rsvp = rsvps.view(access.guest().getId()).orElse(null);
		GuestRsvpForm form = submitted == null ? form(rsvp) : submitted;
		Page<PublicGreetingView> greetings = access.wedding().isGreetingsEnabled()
				? rsvps.approvedGreetings(page) : Page.empty();
		model.addAttribute("preview", access.preview());
		model.addAttribute("language", access.language());
		model.addAttribute("accentColor", safeAccent(access.preview().accentColor()));
		model.addAttribute("invitationPath", invitationPath);
		model.addAttribute("plusOneAllowed", access.guest().isPlusOneAllowed());
		model.addAttribute("greetingsEnabled", access.wedding().isGreetingsEnabled());
		model.addAttribute("privateNoteEnabled", access.wedding().isPrivateOrganizerNoteEnabled());
		model.addAttribute("rsvp", rsvp);
		if (!model.containsAttribute("rsvpForm")) model.addAttribute("rsvpForm", form);
		model.addAttribute("rsvpWritable", writable(access));
		model.addAttribute("rsvpClosedReason", closedReason(access));
		model.addAttribute("qrVerified", rsvp != null && rsvp.response() == AttendanceResponse.HADIR
				&& verification.verified(session, access.guest().getPublicId(),
				access.guest().getInvitationTokenVersion(), access.guest().getNormalizedWhatsappNumber()));
		model.addAttribute("greetings", greetings.getContent());
		model.addAttribute("greetingPage", greetings.getNumber());
		model.addAttribute("greetingHasNext", greetings.hasNext());
		return "guest/invitation";
	}

	public boolean writable(Access access) {
		if (access.wedding().getRsvpDeadline() == null) return false;
		Instant deadline = access.wedding().getRsvpDeadline()
				.atZone(ZoneId.of(access.wedding().getTimeZone())).toInstant();
		return clock.instant().isBefore(deadline);
	}

	public String unavailable(HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		return "guest/unavailable";
	}

	public String closed(String language, Model model) {
		model.addAttribute("language", language);
		return "guest/closed";
	}

	private GuestRsvpForm form(RsvpView rsvp) {
		if (rsvp == null) return new GuestRsvpForm(null, null, null, false, null, null, -1);
		return new GuestRsvpForm(rsvp.response(), rsvp.plannedAttendeeCount(), rsvp.greeting(),
				rsvp.greetingPublicConsent(), rsvp.privateOrganizerNote(), null, rsvp.version());
	}

	private String closedReason(Access access) {
		if (access.wedding().getRsvpDeadline() == null) {
			return "EN".equals(access.language()) ? "RSVP is not open yet" : "RSVP belum dibuka";
		}
		return writable(access) ? null
				: ("EN".equals(access.language()) ? "The RSVP deadline has passed" : "Batas waktu RSVP telah lewat");
	}

	private String invitationPath(String publicId, String version, String signature) {
		return "/i/" + publicId + "/" + version + "/" + signature;
	}

	private String safeAccent(String accentColor) {
		return accentColor != null && accentColor.matches("#[0-9A-Fa-f]{6}") ? accentColor : "#7A5C48";
	}
}
