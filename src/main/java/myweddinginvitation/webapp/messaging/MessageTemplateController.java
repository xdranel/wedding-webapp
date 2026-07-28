package myweddinginvitation.webapp.messaging;

import java.util.NoSuchElementException;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class MessageTemplateController {
	private static final String CONFLICT = "This message template changed by another administrator. Reload and try again.";
	private static final MessageTemplateValues PREVIEW_VALUES = new MessageTemplateValues(
			"Ms", "Alex", "Rama & Shinta", "https://example.com/invitation",
			"1 May 2027", "1 May 2027", "Ceremony venue", "1 May 2027", "Reception venue");
	private final MessageTemplateService templates;

	public MessageTemplateController(MessageTemplateService templates) {
		this.templates = templates;
	}

	@GetMapping("/admin/message-templates")
	String list(Model model) {
		model.addAttribute("templates", templates.findAll());
		return "admin/message-templates/list";
	}

	@GetMapping("/admin/message-templates/{id}")
	String edit(@PathVariable long id, Model model) {
		MessageTemplate template = templates.get(id);
		page(model, template, form(template));
		return "admin/message-templates/edit";
	}

	@PostMapping("/admin/message-templates/{id}")
	String update(@PathVariable long id, @Valid @ModelAttribute("form") MessageTemplateForm form,
			BindingResult result, Model model) {
		MessageTemplate template = templates.get(id);
		if (!result.hasErrors()) {
			try {
				templates.update(id, version(form), form.getBody());
				return "redirect:/admin/message-templates";
			} catch (IllegalArgumentException exception) {
				result.rejectValue("body", "template.invalid", exception.getMessage());
			} catch (OptimisticLockingFailureException exception) {
				result.reject("template.conflict", CONFLICT);
				template = templates.get(id);
			}
		}
		page(model, template, form);
		return "admin/message-templates/edit";
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void templateNotFound() {
	}

	private void page(Model model, MessageTemplate template, MessageTemplateForm form) {
		model.addAttribute("template", template);
		model.addAttribute("form", form);
		model.addAttribute("preview", preview(form.getBody()));
	}

	private MessageTemplateForm form(MessageTemplate template) {
		MessageTemplateForm form = new MessageTemplateForm();
		form.setVersion(template.getVersion());
		form.setBody(template.getBody());
		return form;
	}

	private long version(MessageTemplateForm form) {
		if (form.getVersion() == null) {
			throw new OptimisticLockingFailureException("Message template has changed");
		}
		return form.getVersion();
	}

	private String preview(String body) {
		try {
			return templates.renderBody(body, PREVIEW_VALUES);
		} catch (IllegalArgumentException exception) {
			return body;
		}
	}
}
