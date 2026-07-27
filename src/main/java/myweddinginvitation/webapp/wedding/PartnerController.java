package myweddinginvitation.webapp.wedding;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class PartnerController {
	private final WeddingContentService weddingContent;

	public PartnerController(WeddingContentService weddingContent) {
		this.weddingContent = weddingContent;
	}

	@GetMapping("/admin/wedding/partners")
	String partners(Model model) {
		model.addAttribute("partnerForms", weddingContent.partnerForms());
		return "admin/wedding/partners";
	}

	@PostMapping("/admin/wedding/partners/{id}")
	String savePartner(@PathVariable long id, @Valid @ModelAttribute("form") PartnerForm form, BindingResult result,
			@RequestParam(required = false) MultipartFile photo, Model model) {
		validateInstagram(form, result);
		if (!result.hasErrors()) {
			try {
				weddingContent.savePartner(id, form, photo);
				return "redirect:/admin/wedding/partners?partnerSaved";
			} catch (IllegalArgumentException exception) {
				result.rejectValue("photo", "photo.invalid", exception.getMessage());
			}
		}
		form.setId(id);
		model.addAttribute("partnerForms", weddingContent.partnerForms().stream()
				.map(partner -> partner.getId() == id ? form : partner).toList());
		return "admin/wedding/partners";
	}

	@PostMapping("/admin/wedding/partners/swap")
	String swapPartners() {
		weddingContent.swapPartners();
		return "redirect:/admin/wedding/partners?partnersSwapped";
	}

	private void validateInstagram(PartnerForm form, BindingResult result) {
		if (result.hasFieldErrors("instagramUrl") || form.getInstagramUrl() == null || form.getInstagramUrl().isBlank()) return;
		try {
			URI uri = URI.create(form.getInstagramUrl());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
		} catch (IllegalArgumentException exception) {
			result.rejectValue("instagramUrl", "instagramUrl.invalid", "Must use an HTTPS URL");
		}
	}
}
