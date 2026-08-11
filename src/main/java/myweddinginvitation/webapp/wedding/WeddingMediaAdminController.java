package myweddinginvitation.webapp.wedding;

import java.util.NoSuchElementException;

import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class WeddingMediaAdminController {
	private static final String PAGE = "admin/wedding/media";
	private final WeddingMediaService media;

	public WeddingMediaAdminController(WeddingMediaService media) {
		this.media = media;
	}

	@GetMapping("/admin/wedding/media")
	String media(Model model) {
		return page(model, emptyForm(), null);
	}

	@PostMapping("/admin/wedding/media/photos")
	String addPhoto(@RequestParam("image") MultipartFile image, @Valid @ModelAttribute("photoForm") GalleryPhotoForm photoForm,
			BindingResult result, Model model, HttpServletResponse response) {
		if (result.hasErrors()) return badRequest(response, model, photoForm, null);
		try {
			media.addPhoto(image, photoForm);
			return "redirect:/admin/wedding/media?photoAdded";
		} catch (IllegalArgumentException | IllegalStateException exception) {
			result.reject("photo.invalid", exception.getMessage());
			return badRequest(response, model, photoForm, null);
		}
	}

	@PostMapping("/admin/wedding/media/photos/{id}")
	String updatePhoto(@PathVariable long id, @Valid @ModelAttribute("photoForm") GalleryPhotoForm photoForm,
			BindingResult result, Model model, HttpServletResponse response) {
		if (result.hasErrors()) return badRequest(response, model, photoForm, id);
		try {
			media.updatePhoto(id, photoForm);
			return "redirect:/admin/wedding/media?photoUpdated";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, photoForm, id);
		} catch (IllegalArgumentException exception) {
			result.reject("photo.invalid", exception.getMessage());
			return badRequest(response, model, photoForm, id);
		}
	}

	@PostMapping("/admin/wedding/media/photos/{id}/replace")
	String replacePhoto(@PathVariable long id, @RequestParam long version, @RequestParam("image") MultipartFile image, Model model,
			HttpServletResponse response) {
		try {
			media.replacePhoto(id, version, image);
			return "redirect:/admin/wedding/media?photoReplaced";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, emptyForm(), id);
		} catch (IllegalArgumentException exception) {
			return badRequest(response, model, exception.getMessage());
		}
	}

	@PostMapping("/admin/wedding/media/photos/{id}/move")
	String movePhoto(@PathVariable long id, @RequestParam long version, @RequestParam int direction, Model model) {
		try {
			media.movePhoto(id, version, direction);
			return "redirect:/admin/wedding/media?photoMoved";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, emptyForm(), id);
		}
	}

	@PostMapping("/admin/wedding/media/photos/{id}/delete")
	String deletePhoto(@PathVariable long id, @RequestParam long version,
			@RequestParam(defaultValue = "false") boolean confirm, Model model, HttpServletResponse response) {
		if (!confirm) return badRequest(response, model, "Confirm deletion before removing this photo.");
		try {
			media.deletePhoto(id, version);
			return "redirect:/admin/wedding/media?photoDeleted";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, emptyForm(), id);
		}
	}

	@PostMapping("/admin/wedding/media/gallery-enabled")
	String setGalleryEnabled(@RequestParam long version, @RequestParam boolean enabled, Model model,
			HttpServletResponse response) {
		try {
			media.setGalleryEnabled(version, enabled);
			return "redirect:/admin/wedding/media?galleryVisibilityChanged";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, emptyForm(), null);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return badRequest(response, model, exception.getMessage());
		}
	}

	@PostMapping("/admin/wedding/media/audio")
	String replaceAudio(@RequestParam("audio") MultipartFile audio, Model model, HttpServletResponse response) {
		try {
			media.replaceAudio(audio);
			return "redirect:/admin/wedding/media?audioReplaced";
		} catch (IllegalArgumentException exception) {
			return badRequest(response, model, exception.getMessage());
		}
	}

	@PostMapping("/admin/wedding/media/audio/delete")
	String deleteAudio(@RequestParam long version, @RequestParam(defaultValue = "false") boolean confirm, Model model,
			HttpServletResponse response) {
		if (!confirm) return badRequest(response, model, "Confirm deletion before removing the audio.");
		try {
			media.deleteAudio(version);
			return "redirect:/admin/wedding/media?audioDeleted";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, emptyForm(), null);
		}
	}

	@PostMapping("/admin/wedding/media/audio-enabled")
	String setAudioEnabled(@RequestParam long version, @RequestParam boolean enabled, Model model,
			HttpServletResponse response) {
		try {
			media.setAudioEnabled(version, enabled);
			return "redirect:/admin/wedding/media?audioVisibilityChanged";
		} catch (OptimisticLockingFailureException exception) {
			return conflict(model, emptyForm(), null);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return badRequest(response, model, exception.getMessage());
		}
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void mediaNotFound() {
	}

	private String conflict(Model model, GalleryPhotoForm form, Long editingId) {
		model.addAttribute("mediaError", "This media changed by another administrator. Reload and try again.");
		return page(model, form, editingId);
	}

	private String badRequest(HttpServletResponse response, Model model, GalleryPhotoForm form, Long editingId) {
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return page(model, form, editingId);
	}

	private String badRequest(HttpServletResponse response, Model model, String message) {
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		model.addAttribute("mediaError", message);
		return page(model, emptyForm(), null);
	}

	private String page(Model model, GalleryPhotoForm form, Long editingId) {
		model.addAttribute("media", media.adminView());
		if (!model.containsAttribute("photoForm")) model.addAttribute("photoForm", form);
		model.addAttribute("editingId", editingId);
		return PAGE;
	}

	private static GalleryPhotoForm emptyForm() {
		return new GalleryPhotoForm("", "", "", 0);
	}
}
