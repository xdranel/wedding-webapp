package myweddinginvitation.webapp.messaging;

import java.time.Instant;

import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GuestDeliveryController {
	private static final String CONFLICT = "This guest changed by another administrator. Reload and try again.";
	private final GuestDeliveryService delivery;
	private final GuestService guests;

	public GuestDeliveryController(GuestDeliveryService delivery, GuestService guests) {
		this.delivery = delivery;
		this.guests = guests;
	}

	@PostMapping("/admin/guests/{id}/open-whatsapp")
	String openWhatsapp(@PathVariable long id, @RequestParam MessageLanguage language,
			RedirectAttributes redirectAttributes) {
		try {
			return "redirect:" + delivery.whatsappUri(id, language);
		} catch (IllegalStateException exception) {
			deliveryRejected(redirectAttributes, exception);
			return detail(id);
		}
	}

	@PostMapping("/admin/guests/{id}/confirm-sent")
	String confirmSent(@PathVariable long id, @RequestParam long version,
			RedirectAttributes redirectAttributes) {
		try {
			delivery.confirmSent(id, version, Instant.now());
		} catch (OptimisticLockingFailureException exception) {
			redirectAttributes.addFlashAttribute("guestError", CONFLICT);
		} catch (IllegalStateException exception) {
			deliveryRejected(redirectAttributes, exception);
		}
		return detail(id);
	}

	@PostMapping("/admin/guests/{id}/regenerate-invitation")
	String regenerateInvitation(@PathVariable long id, @RequestParam long version,
			RedirectAttributes redirectAttributes) {
		try {
			guests.regenerateInvitation(id, version, Instant.now());
		} catch (OptimisticLockingFailureException exception) {
			redirectAttributes.addFlashAttribute("guestError", CONFLICT);
		} catch (IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("guestError", exception.getMessage());
		}
		return detail(id);
	}

	private String detail(long id) {
		return "redirect:/admin/guests/" + id;
	}

	private void deliveryRejected(RedirectAttributes redirectAttributes, IllegalStateException exception) {
		redirectAttributes.addFlashAttribute("guestError", exception.getMessage());
		if (exception.getMessage() != null && exception.getMessage().startsWith("Initial delivery requires")) {
			redirectAttributes.addFlashAttribute("eventStatusRequired", true);
		}
	}
}
