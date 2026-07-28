package myweddinginvitation.webapp.account;

import java.time.Instant;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;

    public DatabaseUserDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = accounts.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRole().name())
                .disabled(!account.isEnabled())
                .accountLocked(account.isLoginLocked(Instant.now()))
                .build();
    }
}
