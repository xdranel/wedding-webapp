package myweddinginvitation.webapp.account;

import java.util.NoSuchElementException;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class StaffAccountController {
    private final StaffAccountService staffAccounts;

    public StaffAccountController(StaffAccountService staffAccounts) {
        this.staffAccounts = staffAccounts;
    }

    @GetMapping("/admin/accounts")
    String list(Model model) {
        model.addAttribute("accounts", staffAccounts.staff());
        return "admin/accounts/list";
    }

    @GetMapping("/admin/accounts/new")
    String createForm(Model model) {
        form(model, "/admin/accounts", "Create staff account", new StaffAccountForm("", ""));
        return "admin/accounts/form";
    }

    @PostMapping("/admin/accounts")
    String create(@Valid @ModelAttribute("form") StaffAccountForm form, BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try {
                staffAccounts.createStaff(form.username(), form.temporaryPassword());
                return "redirect:/admin/accounts";
            } catch (IllegalArgumentException exception) {
                errors.rejectValue("username", "account.invalid", exception.getMessage());
            }
        }
        form(model, "/admin/accounts", "Create staff account", form);
        return "admin/accounts/form";
    }

    @GetMapping("/admin/accounts/{id}/reset")
    String resetForm(@PathVariable long id, Model model) {
        UserAccount account = staff(id);
        form(model, "/admin/accounts/" + id + "/reset", "Reset password for " + account.getUsername(),
                new StaffAccountForm(account.getUsername(), ""));
        return "admin/accounts/form";
    }

    @PostMapping("/admin/accounts/{id}/reset")
    String reset(@PathVariable long id, @ModelAttribute("form") StaffAccountForm form,
                 BindingResult errors, Model model) {
        try {
            staffAccounts.resetPassword(id, form.temporaryPassword());
            return "redirect:/admin/accounts";
        } catch (IllegalArgumentException exception) {
            errors.rejectValue("temporaryPassword", "password.invalid", exception.getMessage());
            UserAccount account = staff(id);
            form(model, "/admin/accounts/" + id + "/reset", "Reset password for " + account.getUsername(),
                    new StaffAccountForm(account.getUsername(), ""));
            return "admin/accounts/form";
        }
    }

    @PostMapping("/admin/accounts/{id}/enable")
    String enable(@PathVariable long id) {
        staffAccounts.enable(id);
        return "redirect:/admin/accounts";
    }

    @PostMapping("/admin/accounts/{id}/disable")
    String disable(@PathVariable long id) {
        staffAccounts.disable(id);
        return "redirect:/admin/accounts";
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void accountNotFound() {
    }

    private UserAccount staff(long id) {
        return staffAccounts.staff().stream()
                .filter(account -> account.getId() == id)
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
    }

    private void form(Model model, String action, String heading, StaffAccountForm form) {
        model.addAttribute("action", action);
        model.addAttribute("heading", heading);
        model.addAttribute("form", form);
    }
}
