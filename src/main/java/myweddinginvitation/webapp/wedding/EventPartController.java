package myweddinginvitation.webapp.wedding;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Controller
public class EventPartController {
	private final WeddingContentService weddingContent;

	public EventPartController(WeddingContentService weddingContent) {
		this.weddingContent = weddingContent;
	}

	@GetMapping("/admin/wedding/events")
	String events(Model model) {
		forms(model, null, null, null);
		return "admin/wedding/events";
	}

	@PostMapping("/admin/wedding/events/{type}")
	String saveEvent(@PathVariable EventType type, @Valid @ModelAttribute("form") EventPartForm form, BindingResult result, Model model) {
		validateVisibleEvent(form, result);
		if (!result.hasErrors()) {
			try {
				weddingContent.saveEvent(type, form);
				return "redirect:/admin/wedding?eventsSaved";
			} catch (OptimisticLockingFailureException exception) {
				result.reject("event.conflict", "This event changed by another administrator. Reload and try again.");
			}
		}
		forms(model, type, form, result);
		return "admin/wedding/events";
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	void invalidEventType() {
	}

	private void forms(Model model, EventType editedType, EventPartForm submitted, BindingResult result) {
		model.addAttribute("ceremonyForm", editedType == EventType.CEREMONY ? submitted : weddingContent.eventForm(EventType.CEREMONY));
		model.addAttribute("receptionForm", editedType == EventType.RECEPTION ? submitted : weddingContent.eventForm(EventType.RECEPTION));
		if (result != null) model.addAttribute(BindingResult.MODEL_KEY_PREFIX + formName(editedType), result);
	}

	private void validateVisibleEvent(EventPartForm form, BindingResult result) {
		if (!form.isVisible()) return;
		required(form.getEventDate(), "eventDate", "Date is required", result);
		required(form.getStartTime(), "startTime", "Start time is required", result);
		required(form.getVenueName(), "venueName", "Venue name is required", result);
		required(form.getAddressId(), "addressId", "Indonesian address is required", result);
		if (!WeddingContentService.isHttpUrl(form.getMapUrl())) {
			result.rejectValue("mapUrl", "mapUrl.invalid", "Must use an HTTP or HTTPS URL with a host");
		}
		if (form.getStartTime() != null && form.getEndTime() != null && !form.getEndTime().isAfter(form.getStartTime())) {
			result.rejectValue("endTime", "endTime.invalid", "End time must be after start time");
		}
	}

	private void required(Object value, String field, String message, BindingResult result) {
		if (value == null || value instanceof String text && text.isBlank()) result.rejectValue(field, field + ".required", message);
	}

	private String formName(EventType type) {
		return type == EventType.CEREMONY ? "ceremonyForm" : "receptionForm";
	}
}
