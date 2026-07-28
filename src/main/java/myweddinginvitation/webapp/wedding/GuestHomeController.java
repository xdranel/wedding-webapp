package myweddinginvitation.webapp.wedding;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GuestHomeController {
    @GetMapping("/i/{token}")
    String home() {
        return "guest/home";
    }
}
