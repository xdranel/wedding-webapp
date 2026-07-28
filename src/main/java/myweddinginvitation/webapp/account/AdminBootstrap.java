package myweddinginvitation.webapp.account;

import myweddinginvitation.webapp.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountRepository accounts;
    private final AppProperties.BootstrapAdmin bootstrapAdmin;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(UserAccountRepository accounts, AppProperties properties,
                          PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.bootstrapAdmin = properties.bootstrapAdmin();
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.countByRole(AccountRole.ADMIN) == 0) {
            accounts.save(new UserAccount(
                    bootstrapAdmin.username(),
                    passwordEncoder.encode(bootstrapAdmin.password()),
                    AccountRole.ADMIN));
        }
    }
}
