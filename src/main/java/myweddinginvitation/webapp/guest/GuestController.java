package myweddinginvitation.webapp.guest;

import java.time.Clock;
import java.util.NoSuchElementException;

import jakarta.validation.Valid;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GuestController {
	private static final String CONFLICT = "This guest changed by another administrator. Reload and try again.";
	private final GuestService guests;
	private final GuestCategoryService categories;
	private final WhatsappNumberService numbers;
	private final WeddingSettingsRepository settings;
	private final RsvpService rsvps;
	private final CheckInService checkIns;
	private final Clock clock;

	public GuestController(GuestService guests, GuestCategoryService categories, WhatsappNumberService numbers,
			WeddingSettingsRepository settings, RsvpService rsvps, CheckInService checkIns, Clock clock) {
		this.guests = guests;
		this.categories = categories;
		this.numbers = numbers;
		this.settings = settings;
		this.rsvps = rsvps;
		this.checkIns = checkIns;
		this.clock = clock;
	}

	@GetMapping("/admin/guests")
	String list(@RequestParam(required = false) String query,
			@RequestParam(required = false) DeliveryState delivery,
			@RequestParam(required = false) Boolean archived,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) String rsvpStatus,
			@RequestParam(defaultValue = "updatedAt,desc") String sort,
			@RequestParam(defaultValue = "0") int page, Model model) {
		AttendanceResponse rsvp = attendance(rsvpStatus);
		GuestListQuery filters = new GuestListQuery(query, delivery, archived, categoryId,
				rsvp, "NONE".equals(rsvpStatus));
		var pageOfGuests = guests.search(filters, PageRequest.of(Math.max(page, 0), 50, sort(sort)));
		model.addAttribute("filters", filters);
		model.addAttribute("sort", sort);
		model.addAttribute("page", pageOfGuests);
		model.addAttribute("rsvps", rsvps.views(pageOfGuests.getContent().stream().map(Guest::getId).toList()));
		model.addAttribute("categories", categories.findAll());
		return "admin/guests/list";
	}

	@GetMapping("/admin/guests/new")
	String newGuest(Model model) {
		formPage(model, new GuestForm("", "", defaultPhoneCountry(), "", null, false, MessageLanguage.ID, null), null, false);
		return "admin/guests/form";
	}

	@PostMapping("/admin/guests")
	String create(@Valid @ModelAttribute("form") GuestForm form, BindingResult result,
			@RequestParam(required = false) Boolean acceptDuplicate, Model model) {
		boolean duplicateAccepted = Boolean.TRUE.equals(acceptDuplicate);
		try {
			if (!duplicateAccepted && guests.requiresDuplicateConfirmation(form)) {
				formPage(model, form, null, true);
				model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "form", result);
				return "admin/guests/form";
			}
		} catch (IllegalArgumentException exception) {
			rejectInvalidPhoneInput(result, form, exception);
		}
		if (!result.hasErrors()) {
			try {
				Guest guest = guests.create(form, duplicateAccepted);
				return "redirect:/admin/guests/" + guest.getId();
			} catch (GuestService.DuplicateWhatsappNumberException exception) {
				formPage(model, form, null, true);
				return "admin/guests/form";
			} catch (IllegalArgumentException exception) {
				rejectInvalidPhoneInput(result, form, exception);
			}
		}
		formPage(model, form, null, false);
		model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "form", result);
		return "admin/guests/form";
	}

	@GetMapping("/admin/guests/{id}")
	String detail(@PathVariable long id, Model model) {
		Guest guest = guests.get(id);
		model.addAttribute("guest", guest);
		model.addAttribute("rsvp", rsvps.view(id).orElse(null));
		model.addAttribute("checkIn", checkIns.current(id).orElse(null));
		model.addAttribute("checkInHistory", checkIns.history(id));
		model.addAttribute("pinLocked", guest.getPinLockedUntil() != null
				&& clock.instant().isBefore(guest.getPinLockedUntil()));
		return "admin/guests/detail";
	}

	@GetMapping("/admin/guests/{id}/edit")
	String edit(@PathVariable long id, Model model) {
		Guest guest = guests.get(id);
		formPage(model, new GuestForm(guest.getDisplayName(), guest.getSalutation(),
				numbers.regionFor(guest.getNormalizedWhatsappNumber(), defaultPhoneCountry()), guest.getNormalizedWhatsappNumber(),
				guest.getCategory() == null ? null : guest.getCategory().getId(),
				guest.isPlusOneAllowed(), guest.getPreferredLanguage(), guest.getInternalNote()), guest, false);
		return "admin/guests/form";
	}

	@PostMapping("/admin/guests/{id}")
	String update(@PathVariable long id, @RequestParam long version, @Valid @ModelAttribute("form") GuestForm form,
			BindingResult result, @RequestParam(required = false) Boolean acceptDuplicate,
			@RequestParam(required = false) Boolean reducePlannedAttendance,
			Authentication authentication, Model model) {
		Guest existing = guests.get(id);
		boolean duplicateAccepted = Boolean.TRUE.equals(acceptDuplicate);
		if (!result.hasErrors()) {
			try {
				Guest guest = guests.update(id, version, form, duplicateAccepted,
						Boolean.TRUE.equals(reducePlannedAttendance), authentication.getName());
				return "redirect:/admin/guests/" + guest.getId();
			} catch (GuestService.DuplicateWhatsappNumberException exception) {
				formPage(model, form, existing, true);
				model.addAttribute("reducePlannedAttendanceAccepted", Boolean.TRUE.equals(reducePlannedAttendance));
				return "admin/guests/form";
			} catch (GuestService.PlannedAttendanceReductionRequiredException exception) {
				formPage(model, form, existing, false, true);
				model.addAttribute("duplicateAccepted", duplicateAccepted);
				return "admin/guests/form";
			} catch (IllegalArgumentException exception) {
				rejectInvalidPhoneInput(result, form, exception);
			} catch (OptimisticLockingFailureException exception) {
				result.reject("guest.conflict", CONFLICT);
			}
		}
		formPage(model, form, existing, false);
		model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "form", result);
		return "admin/guests/form";
	}

	@PostMapping("/admin/guests/{id}/archive")
	String archive(@PathVariable long id, @RequestParam long version, RedirectAttributes redirectAttributes) {
		return action(id, version, redirectAttributes, () -> guests.archive(id, version));
	}

	@PostMapping("/admin/guests/{id}/restore")
	String restore(@PathVariable long id, @RequestParam long version, RedirectAttributes redirectAttributes) {
		return action(id, version, redirectAttributes, () -> guests.restore(id, version));
	}

	@PostMapping("/admin/guests/{id}/delete")
	String delete(@PathVariable long id, @RequestParam long version, RedirectAttributes redirectAttributes) {
		try {
			guests.deleteInactive(id, version);
		} catch (OptimisticLockingFailureException exception) {
			redirectAttributes.addFlashAttribute("guestError", CONFLICT);
		} catch (IllegalStateException exception) {
			redirectAttributes.addFlashAttribute("guestError", exception.getMessage());
		}
		return "redirect:/admin/guests";
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void guestNotFound() {
	}

	private String action(long id, long version, RedirectAttributes redirectAttributes, Runnable action) {
		try {
			action.run();
		} catch (OptimisticLockingFailureException exception) {
			redirectAttributes.addFlashAttribute("guestError", CONFLICT);
		}
		return "redirect:/admin/guests/" + id;
	}

	private void formPage(Model model, GuestForm form, Guest guest, boolean duplicateWarning) {
		formPage(model, form, guest, duplicateWarning, false);
	}

	private void formPage(Model model, GuestForm form, Guest guest, boolean duplicateWarning,
			boolean reductionWarning) {
		model.addAttribute("form", form);
		model.addAttribute("guest", guest);
		model.addAttribute("categories", categories.findAll());
		model.addAttribute("phoneRegions", numbers.supportedRegions());
		model.addAttribute("duplicateWarning", duplicateWarning);
		model.addAttribute("reducePlannedAttendanceWarning", reductionWarning);
	}

	private AttendanceResponse attendance(String value) {
		if (value == null || value.equals("NONE")) return null;
		try {
			return AttendanceResponse.valueOf(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private String defaultPhoneCountry() {
		return settings.getSingleton().orElseThrow(NoSuchElementException::new).getDefaultPhoneCountry();
	}

	private void rejectInvalidPhoneInput(BindingResult result, GuestForm form, IllegalArgumentException exception) {
		String field = numbers.supportedRegions().stream().anyMatch(region -> region.code().equals(form.phoneRegion()))
				? "whatsappNumber" : "phoneRegion";
		Object rejectedValue = field.equals("phoneRegion") ? form.phoneRegion() : form.whatsappNumber();
		result.addError(new FieldError("form", field, rejectedValue, false,
				null, null, exception.getMessage()));
	}

	private Sort sort(String value) {
		return switch (value) {
			case "name,asc" -> Sort.by("displayName").ascending();
			case "name,desc" -> Sort.by("displayName").descending();
			case "updatedAt,asc" -> Sort.by("updatedAt").ascending();
			default -> Sort.by("updatedAt").descending();
		};
	}
}
