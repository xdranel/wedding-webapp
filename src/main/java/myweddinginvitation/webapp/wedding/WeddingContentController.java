package myweddinginvitation.webapp.wedding;

import java.time.DateTimeException;
import java.time.ZoneId;

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

	private void validateTimeZone(WeddingSettingsForm form, BindingResult result) {
		if (result.hasFieldErrors("timeZone")) return;
		try {
			ZoneId.of(form.getTimeZone());
		} catch (DateTimeException exception) {
			result.rejectValue("timeZone", "timeZone.invalid", "Choose a valid time zone");
		}
	}
}
