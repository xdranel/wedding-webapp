package myweddinginvitation.webapp.guest;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GuestCategoryController {
	private static final String CONFLICT = "This category changed by another administrator. Reload and try again.";
	private final GuestCategoryService guestCategories;

	public GuestCategoryController(GuestCategoryService guestCategories) {
		this.guestCategories = guestCategories;
	}

	@GetMapping("/admin/guest-categories")
	String categories(Model model) {
		page(model, new GuestCategoryForm(), null);
		return "admin/guest-categories/list";
	}

	@PostMapping("/admin/guest-categories")
	String create(@Valid @ModelAttribute("form") GuestCategoryForm form, BindingResult result, Model model) {
		if (!result.hasErrors()) {
			try {
				guestCategories.create(form.getName());
				return "redirect:/admin/guest-categories";
			} catch (IllegalArgumentException exception) {
				result.rejectValue("name", "category.duplicate", exception.getMessage());
			}
		}
		page(model, form, null);
		return "admin/guest-categories/list";
	}

	@PostMapping("/admin/guest-categories/{id}")
	String rename(@PathVariable long id, @Valid @ModelAttribute("form") GuestCategoryForm form,
			BindingResult result, Model model) {
		if (!result.hasErrors()) {
			try {
				guestCategories.rename(id, version(form), form.getName());
				return "redirect:/admin/guest-categories";
			} catch (IllegalArgumentException exception) {
				result.rejectValue("name", "category.duplicate", exception.getMessage());
			} catch (OptimisticLockingFailureException exception) {
				result.reject("category.conflict", CONFLICT);
			}
		}
		page(model, form, id);
		return "admin/guest-categories/list";
	}

	@PostMapping("/admin/guest-categories/{id}/delete")
	String delete(@PathVariable long id, GuestCategoryForm form, RedirectAttributes redirectAttributes) {
		try {
			guestCategories.delete(id, version(form));
		} catch (OptimisticLockingFailureException exception) {
			redirectAttributes.addFlashAttribute("categoryError", CONFLICT);
		}
		return "redirect:/admin/guest-categories";
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void categoryNotFound() {
	}

	private void page(Model model, GuestCategoryForm form, Long editingId) {
		model.addAttribute("form", form);
		model.addAttribute("editingId", editingId);
		model.addAttribute("categories", guestCategories.findAll());
		model.addAttribute("withoutCategoryCount", guestCategories.withoutCategoryCount());
	}

	private long version(GuestCategoryForm form) {
		if (form.getVersion() == null) {
			throw new OptimisticLockingFailureException("Guest category has changed");
		}
		return form.getVersion();
	}
}
