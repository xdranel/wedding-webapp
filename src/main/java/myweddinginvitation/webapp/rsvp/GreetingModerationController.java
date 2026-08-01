package myweddinginvitation.webapp.rsvp;

import java.util.NoSuchElementException;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GreetingModerationController {
	private static final String CONFLICT = "This RSVP changed. Reload and try again.";
	private final RsvpService rsvps;

	public GreetingModerationController(RsvpService rsvps) {
		this.rsvps = rsvps;
	}

	@GetMapping("/admin/greetings")
	String list(@RequestParam(defaultValue = "PENDING") GreetingModerationState state,
			@RequestParam(defaultValue = "0") int page, Model model) {
		model.addAttribute("state", state);
		model.addAttribute("page", rsvps.moderation(state, page));
		return "admin/greetings/list";
	}

	@PostMapping("/admin/greetings/{id}/approve")
	String approve(@PathVariable long id, @RequestParam long version, RedirectAttributes redirectAttributes) {
		return action(redirectAttributes, () -> rsvps.approveGreeting(id, version));
	}

	@PostMapping("/admin/greetings/{id}/hide")
	String hide(@PathVariable long id, @RequestParam long version, RedirectAttributes redirectAttributes) {
		return action(redirectAttributes, () -> rsvps.hideGreeting(id, version));
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void notFound() {
	}

	private String action(RedirectAttributes redirectAttributes, Runnable action) {
		try {
			action.run();
		} catch (OptimisticLockingFailureException exception) {
			redirectAttributes.addFlashAttribute("greetingError", CONFLICT);
		} catch (IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("greetingError", exception.getMessage());
		}
		return "redirect:/admin/greetings?state=PENDING";
	}
}
