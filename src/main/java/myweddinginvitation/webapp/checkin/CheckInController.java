package myweddinginvitation.webapp.checkin;

import java.util.List;
import java.util.Map;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CheckInController {
	private static final String SEARCH_ERROR = "Enter at least two name characters or exactly four digits.";

	private final CheckInService checkIns;
	private final GuestRepository guests;

	public CheckInController(CheckInService checkIns, GuestRepository guests) {
		this.checkIns = checkIns;
		this.guests = guests;
	}

	@GetMapping("/check-in")
	String home(Model model) {
		homePage(model);
		return "checkin/home";
	}

	@GetMapping("/check-in/search")
	String search(@RequestParam(required = false) String q, Model model) {
		CheckInSearchForm form = new CheckInSearchForm(q);
		homePage(model);
		model.addAttribute("search", form);
		if (!form.isValid()) {
			model.addAttribute("searchError", SEARCH_ERROR);
			return "checkin/home";
		}
		List<Guest> found = guests.findActiveForCheckIn(form.query(), form.isPhoneSuffix(), PageRequest.of(0, 20));
		Map<Long, CheckInService.CheckInView> current = checkIns.currentFor(found.stream().map(Guest::getId).toList());
		model.addAttribute("searchResults", found.stream().map(guest -> new CheckInSearchResult(guest.getId(),
				guest.getDisplayName(), guest.getCategory() == null ? null : guest.getCategory().getDisplayName(),
				current.get(guest.getId()))).toList());
		return "checkin/home";
	}

	@PostMapping("/check-in/preview/qr")
	String previewQr(@RequestParam(required = false) String payload, Model model) {
		try {
			return preview(model, checkIns.previewQr(payload), new CheckInConfirmationForm(payload, null, 1, false), true);
		} catch (CheckInException exception) {
			homePage(model);
			model.addAttribute("checkInError", message(exception));
			return "checkin/home";
		}
	}

	@GetMapping("/check-in/preview/guest/{id}")
	String previewGuest(@PathVariable long id, Model model) {
		try {
			CheckInPreview preview = checkIns.previewGuest(id);
			return preview(model, preview, new CheckInConfirmationForm(null, preview.guestVersion(), 1, false), false);
		} catch (CheckInException exception) {
			homePage(model);
			model.addAttribute("checkInError", message(exception));
			return "checkin/home";
		}
	}

	@PostMapping("/check-in/confirm/qr")
	String confirmQr(@ModelAttribute CheckInConfirmationForm form, Authentication authentication,
			RedirectAttributes attributes) {
		try {
			return result(checkIns.confirmQr(form.payload(), form.actualCount(), form.acceptedRsvpChange(),
					authentication.getName()), attributes);
		} catch (CheckInException exception) {
			attributes.addFlashAttribute("checkInError", message(exception));
			return "redirect:/check-in";
		}
	}

	@PostMapping("/check-in/confirm/guest/{id}")
	String confirmGuest(@PathVariable long id, @ModelAttribute CheckInConfirmationForm form,
			Authentication authentication, RedirectAttributes attributes) {
		if (form.guestVersion() == null) {
			attributes.addFlashAttribute("checkInError", "The guest selection is incomplete. Search again.");
			return "redirect:/check-in";
		}
		try {
			return result(checkIns.confirmGuest(id, form.guestVersion(), form.actualCount(), form.acceptedRsvpChange(),
					authentication.getName()), attributes);
		} catch (CheckInException exception) {
			attributes.addFlashAttribute("checkInError", message(exception));
			return "redirect:/check-in";
		}
	}

	@GetMapping("/check-in/result")
	String result(Model model) {
		return model.containsAttribute("outcome") ? "checkin/result" : "redirect:/check-in";
	}

	private String preview(Model model, CheckInPreview preview, CheckInConfirmationForm form, boolean qr) {
		model.addAttribute("preview", preview);
		model.addAttribute("form", form);
		model.addAttribute("qr", qr);
		return "checkin/preview";
	}

	private String result(CheckInOutcome outcome, RedirectAttributes attributes) {
		attributes.addFlashAttribute("outcome", outcome);
		return "redirect:/check-in/result";
	}

	private void homePage(Model model) {
		if (!model.containsAttribute("search")) model.addAttribute("search", new CheckInSearchForm(""));
		if (!model.containsAttribute("searchResults")) model.addAttribute("searchResults", List.of());
	}

	private String message(CheckInException exception) {
		return switch (exception.failure()) {
			case INVALID_QR, EXPIRED_QR -> "The invitation code is not valid. Scan it again.";
			case INVITATION_INACTIVE -> "This invitation is not active.";
			case WEDDING_UNPUBLISHED, CHECK_IN_CLOSED -> "Check-in is not currently open.";
			case ACCOUNT_DISABLED -> "Your staff account is not active.";
			case STALE_GUEST, STALE_RSVP -> "The guest changed. Search again before confirming.";
			case ATTENDANCE_NOT_ALLOWED -> "That attendance count is not allowed.";
			case RSVP_CHANGE_NOT_ACCEPTED -> "Confirm the RSVP change before continuing.";
		};
	}

	public record CheckInSearchResult(long id, String displayName, String categoryName,
			CheckInService.CheckInView currentCheckIn) {
	}
}
