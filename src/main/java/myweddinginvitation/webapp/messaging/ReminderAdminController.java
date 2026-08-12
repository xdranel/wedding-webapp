package myweddinginvitation.webapp.messaging;

import java.time.Clock;
import java.util.NoSuchElementException;

import myweddinginvitation.webapp.guest.GuestCategoryService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReminderAdminController {
	private static final String PAGE = "admin/reminders/list";
	private static final String ACTION_ERROR = "This reminder changed or is no longer eligible. Reload and try again.";
	private final ReminderService reminders;
	private final GuestCategoryService categories;
	private final Clock clock;

	public ReminderAdminController(ReminderService reminders, GuestCategoryService categories, Clock clock) {
		this.reminders = reminders;
		this.categories = categories;
		this.clock = clock;
	}

	@GetMapping("/admin/reminders")
	String list(@RequestParam(defaultValue = "RSVP") ReminderKind kind,
			@RequestParam(required = false) Long categoryId, Model model) {
		model.addAttribute("kind", kind);
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("categories", categories.findAll());
		try {
			model.addAttribute("queue", reminders.queue(kind, categoryId));
		} catch (IllegalStateException exception) {
			model.addAttribute("queue", java.util.List.of());
			model.addAttribute("reminderError", exception.getMessage());
		}
		return PAGE;
	}

	@PostMapping("/admin/reminders/{kind}/{guestId}/open-whatsapp")
	String openWhatsapp(@PathVariable ReminderKind kind, @PathVariable long guestId,
			@RequestParam MessageLanguage language, @RequestParam(required = false) Long categoryId,
			RedirectAttributes attributes) {
		try {
			return "redirect:" + reminders.whatsappUri(guestId, kind, language);
		} catch (NoSuchElementException | IllegalStateException exception) {
			attributes.addFlashAttribute("reminderError", ACTION_ERROR);
			return redirect(kind, categoryId);
		}
	}

	@PostMapping("/admin/reminders/{kind}/{guestId}/confirm-sent")
	String confirmSent(@PathVariable ReminderKind kind, @PathVariable long guestId, @RequestParam long version,
			@RequestParam(required = false) Long categoryId, RedirectAttributes attributes) {
		try {
			Long nextGuestId = reminders.confirmSent(guestId, version, kind, categoryId, clock.instant());
			if (nextGuestId == null) {
				attributes.addFlashAttribute("reminderCompletion", "Reminder queue complete.");
				return redirect(kind, categoryId);
			}
			return redirect(kind, categoryId) + "#guest-" + nextGuestId;
		} catch (NoSuchElementException | OptimisticLockingFailureException | IllegalStateException exception) {
			attributes.addFlashAttribute("reminderError", ACTION_ERROR);
			return redirect(kind, categoryId);
		}
	}

	private String redirect(ReminderKind kind, Long categoryId) {
		return "redirect:/admin/reminders?kind=" + kind + (categoryId == null ? "" : "&categoryId=" + categoryId);
	}
}
