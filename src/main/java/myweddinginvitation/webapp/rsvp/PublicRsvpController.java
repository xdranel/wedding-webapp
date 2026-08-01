package myweddinginvitation.webapp.rsvp;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import myweddinginvitation.webapp.guest.InvitationAccessService;
import myweddinginvitation.webapp.guest.InvitationAccessService.Access;
import myweddinginvitation.webapp.guest.PublicInvitationController;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicRsvpController {
	private static final DateTimeFormatter RETRY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final InvitationAccessService invitationAccess;
	private final PublicInvitationController invitationPage;
	private final RsvpService rsvps;
	private final GuestPinService pins;
	private final GuestVerificationSession verification;

	public PublicRsvpController(InvitationAccessService invitationAccess, PublicInvitationController invitationPage,
			RsvpService rsvps, GuestPinService pins, GuestVerificationSession verification) {
		this.invitationAccess = invitationAccess;
		this.invitationPage = invitationPage;
		this.rsvps = rsvps;
		this.pins = pins;
		this.verification = verification;
	}

	@PostMapping("/i/{publicId}/{version}/{signature}/rsvp")
	String submit(@PathVariable String publicId, @PathVariable String version, @PathVariable String signature,
			@RequestParam(required = false) String language,
			@Valid @ModelAttribute("rsvpForm") GuestRsvpForm form, BindingResult errors,
			Model model, HttpServletRequest request, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		Access access = invitationAccess.resolve(publicId, version, signature, language);
		if (access == null) return invitationPage.unavailable(response);
		if (access.wedding().isEventClosed()) return invitationPage.closed(access.language(), model);
		String path = path(publicId, version, signature);

		localizeStructuralErrors(form, errors, access.language());
		validateAttendance(form, access, errors);
		if (!invitationPage.writable(access)) {
			errors.reject("rsvp.closed", access.wedding().getRsvpDeadline() == null
					? text(access, "RSVP belum dibuka", "RSVP is not open yet")
					: text(access, "Batas waktu RSVP telah lewat", "The RSVP deadline has passed"));
		}
		if (errors.hasErrors()) return invitationPage.render(access, path, form, 0, model, request.getSession());

		PinVerificationResult pin = pins.verify(access.guest().getPublicId(),
				access.guest().getInvitationTokenVersion(), form.pin());
		if (pin.status() != PinVerificationResult.Status.SUCCESS) {
			if (pin.status() == PinVerificationResult.Status.UNAVAILABLE) return invitationPage.unavailable(response);
			addPinError(pin, access, errors);
			return invitationPage.render(access, path, form, 0, model, request.getSession());
		}

		try {
			rsvps.submitGuest(access.guest().getId(), form.version(), new RsvpSubmission(form.response(),
					form.plannedAttendeeCount(), form.greeting(), form.greetingPublicConsent(),
					form.privateOrganizerNote()));
		} catch (OptimisticLockingFailureException exception) {
			errors.reject("rsvp.conflict", text(access, "RSVP telah berubah. Periksa lalu kirim kembali.",
					"RSVP has changed. Review it and submit again."));
			return invitationPage.render(access, path, form, 0, model, request.getSession());
		} catch (IllegalArgumentException exception) {
			errors.reject("rsvp.invalid", text(access, "Data RSVP tidak valid.", "RSVP data is invalid."));
			return invitationPage.render(access, path, form, 0, model, request.getSession());
		} catch (IllegalStateException exception) {
			errors.reject("rsvp.closed", text(access, "RSVP tidak dapat diubah.", "RSVP cannot be changed."));
			return invitationPage.render(access, path, form, 0, model, request.getSession());
		}

		verification.grant(request.getSession(), access.guest().getPublicId(),
				access.guest().getInvitationTokenVersion(), access.guest().getNormalizedWhatsappNumber());
		return "redirect:" + redirect(path, language, "rsvpSaved");
	}

	@PostMapping("/i/{publicId}/{version}/{signature}/verify-qr")
	String verifyQr(@PathVariable String publicId, @PathVariable String version, @PathVariable String signature,
			@RequestParam(required = false) String language, @RequestParam(defaultValue = "") String pin,
			Model model, HttpServletRequest request, HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store");
		Access access = invitationAccess.resolve(publicId, version, signature, language);
		if (access == null) return invitationPage.unavailable(response);
		if (access.wedding().isEventClosed()) return invitationPage.closed(access.language(), model);
		String path = path(publicId, version, signature);
		RsvpView rsvp = rsvps.view(access.guest().getId()).orElse(null);
		if (rsvp == null || rsvp.response() != AttendanceResponse.HADIR) {
			model.addAttribute("qrError", text(access, "QR hanya tersedia untuk RSVP Hadir.",
					"QR is available only for an accepted RSVP."));
			return invitationPage.render(access, path, null, 0, model, request.getSession());
		}
		if (!pin.matches("[0-9]{4}")) {
			model.addAttribute("qrError", text(access, "Masukkan PIN empat digit.", "Enter a four-digit PIN."));
			return invitationPage.render(access, path, null, 0, model, request.getSession());
		}

		PinVerificationResult result = pins.verify(access.guest().getPublicId(),
				access.guest().getInvitationTokenVersion(), pin);
		if (result.status() == PinVerificationResult.Status.UNAVAILABLE) return invitationPage.unavailable(response);
		if (result.status() != PinVerificationResult.Status.SUCCESS) {
			model.addAttribute("qrError", pinMessage(result, access));
			return invitationPage.render(access, path, null, 0, model, request.getSession());
		}
		verification.grant(request.getSession(), access.guest().getPublicId(),
				access.guest().getInvitationTokenVersion(), access.guest().getNormalizedWhatsappNumber());
		return "redirect:" + redirect(path, language, "qrVerified");
	}

	private void validateAttendance(GuestRsvpForm form, Access access, BindingResult errors) {
		if (form.response() != AttendanceResponse.HADIR || errors.hasFieldErrors("plannedAttendeeCount")) return;
		int count = form.plannedAttendeeCount() == null ? 1 : form.plannedAttendeeCount();
		if (count != 1 && count != 2) {
			errors.rejectValue("plannedAttendeeCount", "count.invalid",
					text(access, "Jumlah hadir harus satu atau dua.", "Attendance must be one or two."));
		} else if (count == 2 && !access.guest().isPlusOneAllowed()) {
			errors.rejectValue("plannedAttendeeCount", "count.companion",
					text(access, "Undangan ini tidak mencakup pendamping.",
							"This invitation does not include a companion."));
		}
	}

	private void localizeStructuralErrors(GuestRsvpForm form, BindingResult errors, String language) {
		if (form.response() == null && errors.hasFieldErrors("response")) {
			errors.reject("response.required", text(language, "Pilih Hadir atau Tidak hadir.",
					"Choose attending or not attending."));
		}
		if (errors.hasFieldErrors("pin")) {
			errors.reject("pin.format", text(language, "Masukkan PIN empat digit.", "Enter a four-digit PIN."));
		}
	}

	private void addPinError(PinVerificationResult result, Access access, BindingResult errors) {
		errors.rejectValue("pin", "pin.invalid", pinMessage(result, access));
	}

	private String pinMessage(PinVerificationResult result, Access access) {
		if (result.status() != PinVerificationResult.Status.LOCKED) {
			return text(access, "PIN tidak cocok.", "PIN does not match.");
		}
		String retryAt = RETRY_TIME.withZone(ZoneId.of(access.wedding().getTimeZone())).format(result.retryAt());
		return text(access, "Terlalu banyak percobaan. Coba lagi setelah " + retryAt + ".",
				"Too many attempts. Try again after " + retryAt + ".");
	}

	private String text(Access access, String indonesian, String english) {
		return text(access.language(), indonesian, english);
	}

	private String path(String publicId, String version, String signature) {
		return "/i/" + publicId + "/" + version + "/" + signature;
	}

	private String redirect(String path, String requestedLanguage, String flag) {
		if (requestedLanguage == null) return path + "?" + flag;
		return path + "?language=" + ("EN".equals(requestedLanguage) ? "EN" : "ID") + "&" + flag;
	}

	private String text(String language, String indonesian, String english) {
		return "EN".equals(language) ? english : indonesian;
	}
}
