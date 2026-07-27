package myweddinginvitation.webapp.account;

import static org.assertj.core.api.Assertions.assertThat;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(MySqlTestConfiguration.class)
class AdminBootstrapTest {
	@Autowired
	private AdminBootstrap bootstrap;

	@Autowired
	private UserAccountRepository repository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@DynamicPropertySource
	static void bootstrapAdminProperties(DynamicPropertyRegistry registry) {
		registry.add("app.bootstrap-admin.username", () -> "owner");
		registry.add("app.bootstrap-admin.password", () -> "Correct-Horse-2026");
	}

	@BeforeEach
	void deleteAccounts() {
		repository.deleteAll();
	}

	@Test
	void createsOneAdministratorWhenRunMoreThanOnce() {
		bootstrap.run(null);
		bootstrap.run(null);

		assertThat(repository.countByRole(AccountRole.ADMIN)).isEqualTo(1);
		UserAccount admin = repository.findByUsernameIgnoreCase("owner").orElseThrow();
		assertThat(passwordEncoder.matches("Correct-Horse-2026", admin.getPasswordHash()))
				.isTrue();
		assertThat(admin.isPasswordChangeRequired()).isTrue();
	}
}
