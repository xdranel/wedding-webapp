package myweddinginvitation.webapp.wedding;

import java.time.DateTimeException;
import java.time.ZoneId;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class WeddingContentController {
	private final WeddingContentService weddingContent;

	public WeddingContentController(WeddingContentService weddingContent) {
		this.weddingContent = weddingContent;
	}

	@GetMapping("/admin/wedding")
	String overview(Model model) {
		model.addAttribute("overview", weddingContent.overview());
		model.addAttribute("publication", weddingContent.checkPublication());
		return "admin/wedding/overview";
	}

	@GetMapping("/admin/wedding/settings")
	String settings(Model model) {
		model.addAttribute("form", weddingContent.settingsForm());
		model.addAttribute("fontPresets", FontPreset.values());
		return "admin/wedding/settings";
	}

	@PostMapping("/admin/wedding/settings")
	String saveSettings(@Valid @ModelAttribute("form") WeddingSettingsForm form, BindingResult result, Model model) {
		validateTimeZone(form, result);
		if (result.hasErrors()) {
			model.addAttribute("fontPresets", FontPreset.values());
			return "admin/wedding/settings";
		}
		weddingContent.saveSettings(form);
		return "redirect:/admin/wedding?settingsSaved";
	}

	@PostMapping("/admin/wedding/publish")
	String publish() {
		weddingContent.publish();
		return "redirect:/admin/wedding";
	}

	@PostMapping("/admin/wedding/return-to-draft")
	String returnToDraft() {
		weddingContent.returnToDraft();
		return "redirect:/admin/wedding";
	}

	@GetMapping("/admin/wedding/preview")
	String previewForm(Model model) {
		model.addAttribute("form", new PreviewForm());
		return "admin/wedding/preview-form";
	}

	@GetMapping("/admin/wedding/preview/render")
	String renderPreview(@Valid @ModelAttribute("form") PreviewForm form, BindingResult result, Model model,
			HttpServletResponse response) {
		if (result.hasErrors()) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return "admin/wedding/preview-form";
		}
		WeddingPreview preview = weddingContent.preview(form.getSalutation(), form.getGuestName(), form.getLanguage());
		model.addAttribute("form", form);
		model.addAttribute("preview", preview);
		model.addAttribute("accentColor", safeAccent(preview.accentColor()));
		return "admin/wedding/preview";
	}

	private void validateTimeZone(WeddingSettingsForm form, BindingResult result) {
		if (result.hasFieldErrors("timeZone")) return;
		try {
			ZoneId.of(form.getTimeZone());
		} catch (DateTimeException exception) {
			result.rejectValue("timeZone", "timeZone.invalid", "Choose a valid time zone");
		}
	}

	private String safeAccent(String accentColor) {
		return accentColor != null && accentColor.matches("#[0-9A-Fa-f]{6}") ? accentColor : "#7A5C48";
	}
}
