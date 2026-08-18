package myweddinginvitation.webapp.reporting;

import java.util.NoSuchElementException;

import myweddinginvitation.webapp.guest.GuestCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class ReportAdminController {
	private final ReportService reports;
	private final GuestCategoryService categories;

	public ReportAdminController(ReportService reports, GuestCategoryService categories) {
		this.reports = reports;
		this.categories = categories;
	}

	@GetMapping("/admin/reports")
	String reports(@RequestParam(required = false) Long categoryId, Model model) {
		model.addAttribute("report", reports.snapshot(categoryId));
		model.addAttribute("categories", categories.findAll());
		model.addAttribute("categoryId", categoryId);
		return "admin/reports/index";
	}

	@GetMapping("/admin/reports/print")
	String print(@RequestParam(required = false) Long categoryId, Model model) {
		model.addAttribute("rows", reports.printRows(categoryId));
		model.addAttribute("categoryId", categoryId);
		return "admin/reports/print";
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void categoryNotFound() {
	}
}
