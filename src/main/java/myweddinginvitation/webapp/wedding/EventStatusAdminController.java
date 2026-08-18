package myweddinginvitation.webapp.wedding;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class EventStatusAdminController {
	private static final String PAGE = "admin/wedding/event-status";
	private static final String CONFIRMATION_PAGE = "admin/wedding/event-status-confirm";
	private final EventStatusService status;

	public EventStatusAdminController(EventStatusService status) {
		this.status = status;
	}

	@GetMapping("/admin/wedding/event-status")
	String eventStatus(Model model) {
		EventStatusView current = status.view();
		page(model, current, messageForm(current));
		return PAGE;
	}

	@PostMapping("/admin/wedding/event-status/messages")
	String saveMessages(@Valid @ModelAttribute("form") EventStatusMessageForm form, BindingResult result, Model model) {
		EventStatusView current = null;
		if (!result.hasErrors()) {
			try {
				status.saveMessages(form);
				return "redirect:/admin/wedding/event-status?messagesSaved";
			} catch (OptimisticLockingFailureException exception) {
				current = status.view();
				rejectConflict(result, form, current);
			} catch (IllegalArgumentException exception) {
				result.reject("eventStatus.invalid", exception.getMessage());
			}
		}
		page(model, current == null ? status.view() : current, form);
		return PAGE;
	}

	@GetMapping("/admin/wedding/event-status/close")
	String closeConfirmation(Model model) {
		EventStatusView current = status.view();
		return confirmation(model, current, changeForm(current));
	}

	@PostMapping("/admin/wedding/event-status/close")
	String close(@Valid @ModelAttribute("form") EventStatusChangeForm form, BindingResult result, Principal principal, Model model) {
		return change(true, form, result, principal, model);
	}

	@GetMapping("/admin/wedding/event-status/reopen")
	String reopenConfirmation(Model model) {
		EventStatusView current = status.view();
		return confirmation(model, current, changeForm(current));
	}

	@PostMapping("/admin/wedding/event-status/reopen")
	String reopen(@Valid @ModelAttribute("form") EventStatusChangeForm form, BindingResult result, Principal principal, Model model) {
		return change(false, form, result, principal, model);
	}

	private String change(boolean closed, EventStatusChangeForm form, BindingResult result, Principal principal, Model model) {
		EventStatusView current = null;
		if (!result.hasErrors()) {
			try {
				status.change(closed, form.getVersion(), form.isConfirmed(), principal == null ? null : principal.getName());
				return "redirect:/admin/wedding/event-status?statusChanged";
			} catch (OptimisticLockingFailureException exception) {
				current = status.view();
				rejectConflict(result, form, current);
			} catch (IllegalArgumentException | IllegalStateException exception) {
				result.reject("eventStatus.invalid", exception.getMessage());
			}
		}
		return confirmation(model, current == null ? status.view() : current, form);
	}

	private void page(Model model, EventStatusView current, EventStatusMessageForm form) {
		model.addAttribute("status", current);
		if (!model.containsAttribute("form")) model.addAttribute("form", form);
	}

	private String confirmation(Model model, EventStatusView current, EventStatusChangeForm form) {
		model.addAttribute("status", current);
		if (!model.containsAttribute("form")) model.addAttribute("form", form);
		return CONFIRMATION_PAGE;
	}

	private void rejectConflict(BindingResult result, EventStatusMessageForm form, EventStatusView current) {
		form.setVersion(current.version());
		result.reject("eventStatus.conflict", "Wedding settings changed by another administrator. Reload and try again.");
	}

	private void rejectConflict(BindingResult result, EventStatusChangeForm form, EventStatusView current) {
		form.setVersion(current.version());
		result.reject("eventStatus.conflict", "Wedding settings changed by another administrator. Reload and try again.");
	}

	private static EventStatusMessageForm messageForm(EventStatusView status) {
		EventStatusMessageForm form = new EventStatusMessageForm();
		form.setVersion(status.version());
		form.setTitleId(status.titleId());
		form.setTitleEn(status.titleEn());
		form.setMessageId(status.messageId());
		form.setMessageEn(status.messageEn());
		return form;
	}

	private static EventStatusChangeForm changeForm(EventStatusView status) {
		EventStatusChangeForm form = new EventStatusChangeForm();
		form.setVersion(status.version());
		return form;
	}
}
