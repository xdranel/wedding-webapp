package myweddinginvitation.webapp.account;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSecurityService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AccountSecurityService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount loginSucceeded(String username) {
        UserAccount account = accounts.findByUsernameForUpdate(username).orElseThrow();
        account.loginSucceeded();
        return account;
    }

    @Transactional
    public void loginFailed(String username) {
        if (username == null || username.length() > 100) {
            return;
        }
        accounts.findByUsernameForUpdate(username)
                .ifPresent(account -> account.loginFailed(Instant.now()));
    }

    @Transactional
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 200) {
            throw new IllegalArgumentException("Password must contain 12 to 200 characters");
        }
        UserAccount account = accounts.findByUsernameForUpdate(username).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            return false;
        }
        account.changePassword(passwordEncoder.encode(newPassword));
        return true;
    }
}
