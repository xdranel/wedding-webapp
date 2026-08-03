package myweddinginvitation.webapp.wedding;

import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.rsvp.RsvpService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminHomeController {
	private final RsvpService rsvps;
	private final CheckInService checkIns;

	public AdminHomeController(RsvpService rsvps, CheckInService checkIns) {
		this.rsvps = rsvps;
		this.checkIns = checkIns;
	}

	@GetMapping("/admin")
	String home(Model model) {
		model.addAttribute("rsvpSummary", rsvps.summary());
		model.addAttribute("checkInSummary", checkIns.summary());
		return "admin/home";
	}
}
