package myweddinginvitation.webapp.config;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SystemStatusAdminController {
	private final SystemStatusService status;

	public SystemStatusAdminController(SystemStatusService status) {
		this.status = status;
	}

	@GetMapping("/admin/system-status")
	String systemStatus(Model model) {
		model.addAttribute("status", status.check());
		return "admin/system-status";
	}
}
