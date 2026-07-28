package myweddinginvitation.webapp.wedding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminHomeController {
    @GetMapping("/admin")
    String home() {
        return "admin/home";
    }
}
