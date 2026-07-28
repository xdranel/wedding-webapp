package myweddinginvitation.webapp.wedding;

import java.util.NoSuchElementException;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class StoryController {
	private final WeddingContentService weddingContent;

	public StoryController(WeddingContentService weddingContent) {
		this.weddingContent = weddingContent;
	}

	@GetMapping("/admin/wedding/story")
	String story(Model model) {
		forms(model, new StoryEntryForm(), null);
		return "admin/wedding/story";
	}

	@PostMapping("/admin/wedding/story")
	String addStory(@Valid @ModelAttribute("form") StoryEntryForm form, BindingResult result, Model model) {
		if (result.hasErrors()) {
			forms(model, form, null);
			return "admin/wedding/story";
		}
		weddingContent.addStory(form);
		return "redirect:/admin/wedding/story?storyAdded";
	}

	@PostMapping("/admin/wedding/story/{id}")
	String updateStory(@PathVariable long id, @Valid @ModelAttribute("form") StoryEntryForm form, BindingResult result, Model model) {
		weddingContent.requireStory(id);
		if (!result.hasErrors()) {
			try {
				weddingContent.updateStory(id, form);
				return "redirect:/admin/wedding/story?storySaved";
			} catch (OptimisticLockingFailureException exception) {
				result.reject("story.conflict", "This story changed by another administrator. Reload and try again.");
			}
		}
		form.setId(id);
		forms(model, form, id);
		return "admin/wedding/story";
	}

	@PostMapping("/admin/wedding/story/{id}/delete")
	String deleteStory(@PathVariable long id) {
		weddingContent.deleteStory(id);
		return "redirect:/admin/wedding/story?storyDeleted";
	}

	@PostMapping("/admin/wedding/story/{id}/up")
	String moveStoryUp(@PathVariable long id) {
		weddingContent.moveStoryUp(id);
		return "redirect:/admin/wedding/story?storyMoved";
	}

	@PostMapping("/admin/wedding/story/{id}/down")
	String moveStoryDown(@PathVariable long id) {
		weddingContent.moveStoryDown(id);
		return "redirect:/admin/wedding/story?storyMoved";
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void storyNotFound() {
	}

	private void forms(Model model, StoryEntryForm form, Long editingId) {
		model.addAttribute("form", form);
		model.addAttribute("editingId", editingId);
		model.addAttribute("storyForms", weddingContent.storyForms().stream()
				.map(entry -> editingId != null && entry.getId().equals(editingId) ? form : entry).toList());
	}
}
