package myweddinginvitation.webapp.guest;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class GuestCsvController {
	static final String SESSION_CSV = GuestCsvController.class.getName() + ".csv";
	private final GuestCsvService csv;

	public GuestCsvController(GuestCsvService csv) {
		this.csv = csv;
	}

	@GetMapping("/admin/guests/import")
	String importPage(HttpSession session, Model model) {
		byte[] source = source(session);
		if (source != null) {
			model.addAttribute("preview", csv.preview(source));
		}
		return "admin/guests/import";
	}

	@PostMapping("/admin/guests/import")
	String upload(@RequestParam MultipartFile file, HttpSession session, Model model) throws IOException {
		if (file.getSize() > GuestCsvService.MAX_BYTES) {
			session.removeAttribute(SESSION_CSV);
			model.addAttribute("uploadError", "CSV files must be 2 MiB or smaller.");
			return "admin/guests/import";
		}
		byte[] source = file.getBytes();
		GuestCsvPreview preview = csv.preview(source);
		if (file.isEmpty()) {
			session.removeAttribute(SESSION_CSV);
			model.addAttribute("uploadError", "Choose a CSV file.");
		} else if (source.length > GuestCsvService.MAX_BYTES) {
			session.removeAttribute(SESSION_CSV);
		} else {
			session.setAttribute(SESSION_CSV, source.clone());
		}
		model.addAttribute("preview", preview);
		return "admin/guests/import";
	}

	@PostMapping("/admin/guests/import/confirm")
	String confirm(@RequestParam(required = false) Boolean acceptWarnings, HttpSession session, Model model) {
		byte[] source = source(session);
		if (source == null) {
			return "redirect:/admin/guests/import";
		}

		GuestCsvPreview preview = csv.preview(source);
		if (preview.hasErrors()) {
			session.removeAttribute(SESSION_CSV);
			model.addAttribute("preview", preview);
			return "admin/guests/import";
		}
		if (preview.hasWarnings() && !Boolean.TRUE.equals(acceptWarnings)) {
			model.addAttribute("preview", preview);
			model.addAttribute("acceptWarningsRequired", true);
			return "admin/guests/import";
		}

		try {
			int imported = csv.importAll(preview, Boolean.TRUE.equals(acceptWarnings));
			return "redirect:/admin/guests?imported=" + imported;
		} catch (IllegalArgumentException exception) {
			model.addAttribute("preview", csv.preview(source));
			model.addAttribute("uploadError", exception.getMessage());
			return "admin/guests/import";
		} finally {
			session.removeAttribute(SESSION_CSV);
		}
	}

	@PostMapping("/admin/guests/import/cancel")
	String cancel(HttpSession session) {
		session.removeAttribute(SESSION_CSV);
		return "redirect:/admin/guests";
	}

	@GetMapping("/admin/guests/template.csv")
	void template(HttpServletResponse response) throws IOException {
		csvResponse(response, "guests-template.csv");
		csv.template(response.getOutputStream());
	}

	@GetMapping("/admin/guests/export.csv")
	void export(HttpServletResponse response) throws IOException {
		csvResponse(response, "guests.csv");
		csv.exportAll(response.getOutputStream());
	}

	private byte[] source(HttpSession session) {
		Object value = session.getAttribute(SESSION_CSV);
		return value instanceof byte[] bytes ? bytes.clone() : null;
	}

	private void csvResponse(HttpServletResponse response, String filename) {
		response.setContentType("text/csv;charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
	}
}
