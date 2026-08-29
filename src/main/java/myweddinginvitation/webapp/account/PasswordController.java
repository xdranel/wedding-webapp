package myweddinginvitation.webapp.account;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class PasswordController {
    private final AccountSecurityService accountSecurity;

    public PasswordController(AccountSecurityService accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    @GetMapping("/account/password")
    String form(Model model, Authentication authentication) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm("", "", ""));
        page(model, authentication);
        return "account/password";
    }

    @PostMapping("/account/password")
    String change(@Valid @ModelAttribute PasswordChangeForm form, BindingResult errors,
                  Authentication authentication, Model model, HttpServletRequest request) {
        if (!Objects.equals(form.newPassword(), form.confirmPassword())) {
            errors.rejectValue("confirmPassword", "password.mismatch",
                    "Passwords do not match.");
        }
        if (!errors.hasErrors()
                && !accountSecurity.changePassword(authentication.getName(),
                form.currentPassword(), form.newPassword())) {
            errors.rejectValue("currentPassword", "password.invalid",
                    "Current password is incorrect.");
        }
        if (errors.hasErrors()) {
            page(model, authentication);
            return "account/password";
        }

        SecurityContextHolder.clearContext();
        request.getSession(false).invalidate();
        return "redirect:/login?passwordChanged";
    }

    private void page(Model model, Authentication authentication) {
        model.addAttribute("accountRole", authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))
                ? AccountRole.ADMIN : AccountRole.STAFF);
    }
}
