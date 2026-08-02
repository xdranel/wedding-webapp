package myweddinginvitation.webapp.account;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccountService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> staff() {
        return accounts.findAllByRoleOrderByUsernameAsc(AccountRole.STAFF);
    }

    @Transactional
    public UserAccount createStaff(String username, String temporaryPassword) {
        validateUsername(username);
        validatePassword(temporaryPassword);
        if (accounts.findByUsernameIgnoreCase(username).isPresent()) {
            throw new IllegalArgumentException("Username is already in use");
        }
        return accounts.save(new UserAccount(username, passwordEncoder.encode(temporaryPassword), AccountRole.STAFF));
    }

    @Transactional
    public void resetPassword(long id, String temporaryPassword) {
        validatePassword(temporaryPassword);
        staff(id).resetPassword(passwordEncoder.encode(temporaryPassword));
    }

    @Transactional
    public void enable(long id) {
        staff(id).enable();
    }

    @Transactional
    public void disable(long id) {
        staff(id).disable();
    }

    private UserAccount staff(long id) {
        return accounts.findById(id)
                .filter(account -> account.getRole() == AccountRole.STAFF)
                .orElseThrow(NoSuchElementException::new);
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 100) {
            throw new IllegalArgumentException("Username must contain 1 to 100 characters");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 200) {
            throw new IllegalArgumentException("Password must contain 12 to 200 characters");
        }
    }
}
