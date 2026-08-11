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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.OptimisticLockingFailureException;

@Controller
public class WeddingContentController {
    private final WeddingContentService weddingContent;
    private final WeddingMediaService weddingMedia;

    public WeddingContentController(WeddingContentService weddingContent, WeddingMediaService weddingMedia) {
        this.weddingContent = weddingContent;
        this.weddingMedia = weddingMedia;
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
        validateAccent(form, result);
        if (result.hasErrors()) {
            model.addAttribute("fontPresets", FontPreset.values());
            return "admin/wedding/settings";
        }
        try {
            weddingContent.saveSettings(form);
        } catch (OptimisticLockingFailureException exception) {
            result.reject("settings.conflict", "Wedding settings changed by another administrator. Reload and try again.");
            form.setVersion(weddingContent.settingsForm().getVersion());
            model.addAttribute("fontPresets", FontPreset.values());
            return "admin/wedding/settings";
        }
        return "redirect:/admin/wedding?settingsSaved";
    }

    @PostMapping("/admin/wedding/publish")
    String publish(@RequestParam long version, RedirectAttributes redirectAttributes) {
        try {
            weddingContent.publish(version);
        } catch (OptimisticLockingFailureException exception) {
            redirectAttributes.addFlashAttribute("publicationConflict", "Wedding settings changed by another administrator. Reload and try again.");
        }
        return "redirect:/admin/wedding";
    }

    @PostMapping("/admin/wedding/return-to-draft")
    String returnToDraft(@RequestParam long version, RedirectAttributes redirectAttributes) {
        try {
            weddingContent.returnToDraft(version);
        } catch (OptimisticLockingFailureException exception) {
            redirectAttributes.addFlashAttribute("publicationConflict", "Wedding settings changed by another administrator. Reload and try again.");
        }
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
        model.addAttribute("media", weddingMedia.publicView(form.getLanguage()));
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

    private void validateAccent(WeddingSettingsForm form, BindingResult result) {
        if (!result.hasFieldErrors("accentColor") && !isSafeAccent(form.getAccentColor())) {
            result.rejectValue("accentColor", "accentColor.lowContrast", "Choose a darker accent color");
        }
    }

    private String safeAccent(String accentColor) {
        return isSafeAccent(accentColor) ? accentColor : "#7A5C48";
    }

    private static boolean isSafeAccent(String accentColor) {
        if (accentColor == null || !accentColor.matches("#[0-9A-Fa-f]{6}")) return false;
        int color = Integer.parseInt(accentColor.substring(1), 16);
        return contrast(color, 0xfffdf9) >= 4.5 && contrast(color, 0xffffff) >= 4.5;
    }

    private static double contrast(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel(color >> 16) + 0.7152 * channel(color >> 8) + 0.0722 * channel(color);
    }

    private static double channel(int color) {
        double channel = (color & 0xff) / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
