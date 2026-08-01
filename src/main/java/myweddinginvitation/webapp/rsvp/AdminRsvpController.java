package myweddinginvitation.webapp.rsvp;

import java.util.NoSuchElementException;

import jakarta.validation.Valid;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class AdminRsvpController {
	private static final String CONFLICT = "This RSVP changed by another administrator. Review the current values and try again.";
	private final GuestService guests;
	private final RsvpService rsvps;
	private final GuestPinService pins;

	public AdminRsvpController(GuestService guests, RsvpService rsvps, GuestPinService pins) {
		this.guests = guests;
		this.rsvps = rsvps;
		this.pins = pins;
	}

	@GetMapping("/admin/guests/{id}/rsvp")
	String edit(@PathVariable long id, Model model) {
		Guest guest = guests.get(id);
		RsvpView rsvp = rsvps.view(id).orElse(null);
		page(model, guest, rsvp, form(rsvp));
		return "admin/guests/rsvp";
	}

	@PostMapping("/admin/guests/{id}/rsvp")
	String update(@PathVariable long id, @Valid @ModelAttribute("form") AdminRsvpForm form,
			BindingResult result, Authentication authentication, Model model) {
		Guest guest = guests.get(id);
		if (!result.hasErrors()) {
			try {
				rsvps.correctByAdmin(id, form.version(),
						new RsvpSubmission(form.response(), form.plannedAttendeeCount(), null, false, null),
						authentication.getName());
				return "redirect:/admin/guests/" + id;
			} catch (OptimisticLockingFailureException exception) {
				RsvpView current = rsvps.view(id).orElse(null);
				AdminRsvpForm currentForm = form(current);
				BeanPropertyBindingResult conflict = new BeanPropertyBindingResult(currentForm, "form");
				conflict.reject("rsvp.conflict", CONFLICT);
				model.addAttribute("form", currentForm);
				model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "form", conflict);
				page(model, guest, current, currentForm);
				return "admin/guests/rsvp";
			} catch (IllegalArgumentException | IllegalStateException exception) {
				result.reject("rsvp.invalid", exception.getMessage());
			}
		}
		page(model, guest, rsvps.view(id).orElse(null), form);
		return "admin/guests/rsvp";
	}

	@PostMapping("/admin/guests/{id}/clear-pin-lock")
	String clearPinLock(@PathVariable long id) {
		pins.clear(id);
		return "redirect:/admin/guests/" + id;
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void notFound() {
	}

	private AdminRsvpForm form(RsvpView rsvp) {
		return rsvp == null
				? new AdminRsvpForm(AttendanceResponse.HADIR, 1, -1)
				: new AdminRsvpForm(rsvp.response(), rsvp.plannedAttendeeCount(), rsvp.version());
	}

	private void page(Model model, Guest guest, RsvpView rsvp, AdminRsvpForm form) {
		if (!model.containsAttribute("form")) model.addAttribute("form", form);
		model.addAttribute("guest", guest);
		model.addAttribute("rsvp", rsvp);
	}
}
