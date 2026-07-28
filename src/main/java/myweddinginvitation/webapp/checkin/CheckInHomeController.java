package myweddinginvitation.webapp.checkin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CheckInHomeController {
    @GetMapping("/check-in")
    String home() {
        return "checkin/home";
    }
}
