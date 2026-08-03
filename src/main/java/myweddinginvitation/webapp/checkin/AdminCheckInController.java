package myweddinginvitation.webapp.checkin;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminCheckInController {
	private static final String INVALID = "Review the check-in values and enter a reason of 500 characters or fewer.";
	private static final String CONFLICT = "This check-in changed. Reload and try again.";
	private static final String RSVP_WARNING = "RSVP changed after check-in, so it was not restored. Correct RSVP separately if needed.";

	private final CheckInService checkIns;

	public AdminCheckInController(CheckInService checkIns) {
		this.checkIns = checkIns;
	}

	@PostMapping("/admin/guests/{id}/check-in/correct")
	String correct(@PathVariable long id,
			@Valid @ModelAttribute("checkInForm") CheckInCorrectionForm form, BindingResult result,
			Authentication authentication, RedirectAttributes attributes) {
		if (result.hasErrors()) {
			attributes.addFlashAttribute("checkInError", INVALID);
			return redirect(id);
		}
		try {
			checkIns.correct(id, form.checkInVersion(), form.actualCount(), form.reason(), authentication.getName());
		} catch (OptimisticLockingFailureException exception) {
			attributes.addFlashAttribute("checkInError", CONFLICT);
		} catch (CheckInException | IllegalArgumentException exception) {
			attributes.addFlashAttribute("checkInError", INVALID);
		}
		return redirect(id);
	}

	@PostMapping("/admin/guests/{id}/check-in/cancel")
	String cancel(@PathVariable long id,
			@Valid @ModelAttribute("checkInForm") CheckInCancellationForm form, BindingResult result,
			Authentication authentication, RedirectAttributes attributes) {
		if (result.hasErrors()) {
			attributes.addFlashAttribute("checkInError", INVALID);
			return redirect(id);
		}
		try {
			if (checkIns.cancel(id, form.checkInVersion(), form.reason(), authentication.getName())
					.rsvpRestorationSkipped()) {
				attributes.addFlashAttribute("checkInWarning", RSVP_WARNING);
			}
		} catch (OptimisticLockingFailureException exception) {
			attributes.addFlashAttribute("checkInError", CONFLICT);
		} catch (CheckInException | IllegalArgumentException exception) {
			attributes.addFlashAttribute("checkInError", INVALID);
		}
		return redirect(id);
	}

	private String redirect(long guestId) {
		return "redirect:/admin/guests/" + guestId;
	}
}
