package myweddinginvitation.webapp.wedding;

import myweddinginvitation.webapp.rsvp.RsvpService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminHomeController {
	private final RsvpService rsvps;

	public AdminHomeController(RsvpService rsvps) {
		this.rsvps = rsvps;
	}

	@GetMapping("/admin")
	String home(Model model) {
		model.addAttribute("rsvpSummary", rsvps.summary());
		return "admin/home";
	}
}
