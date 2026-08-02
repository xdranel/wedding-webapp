package myweddinginvitation.webapp.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class StaffAccountServiceTest {
	private static final String TEMPORARY_PASSWORD = "Temporary-Password-2026";

	@Autowired
	private StaffAccountService staffAccounts;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void clearAccounts() {
		accounts.deleteAll();
	}

	@Test
	void createsOnlyStaffWithAHashedTemporaryPasswordAndFirstLoginChange() {
		UserAccount created = staffAccounts.createStaff("Check-In", TEMPORARY_PASSWORD);

		assertThat(created.getRole()).isEqualTo(AccountRole.STAFF);
		assertThat(created.isPasswordChangeRequired()).isTrue();
		assertThat(created.getPasswordHash()).isNotEqualTo(TEMPORARY_PASSWORD);
		assertThat(passwordEncoder.matches(TEMPORARY_PASSWORD, created.getPasswordHash())).isTrue();
		assertThat(staffAccounts.staff()).extracting(UserAccount::getUsername).containsExactly("Check-In");

		assertThatIllegalArgumentException()
				.isThrownBy(() -> staffAccounts.createStaff("check-in", TEMPORARY_PASSWORD));
	}

	@Test
	void rejectsTemporaryPasswordsOutsideTheRequiredRange() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> staffAccounts.createStaff("staff", "x".repeat(11)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> staffAccounts.createStaff("staff", "x".repeat(201)));
	}

	@Test
	void resettingAndDisablingStaffRevokeExistingSessions() {
		UserAccount staff = staffAccounts.createStaff("staff", TEMPORARY_PASSWORD);
		staff.changePassword(passwordEncoder.encode("Changed-Password-2026"));
		staff = accounts.save(staff);
		long afterFirstChange = staff.getSessionVersion();

		staffAccounts.resetPassword(staff.getId(), "Reset-Password-2026");
		staff = accounts.findById(staff.getId()).orElseThrow();
		assertThat(staff.isPasswordChangeRequired()).isTrue();
		assertThat(staff.getSessionVersion()).isEqualTo(afterFirstChange + 1);
		assertThat(passwordEncoder.matches("Reset-Password-2026", staff.getPasswordHash())).isTrue();

		staffAccounts.disable(staff.getId());
		staff = accounts.findById(staff.getId()).orElseThrow();
		assertThat(staff.isEnabled()).isFalse();
		assertThat(staff.getSessionVersion()).isEqualTo(afterFirstChange + 2);

		staffAccounts.enable(staff.getId());
		staff = accounts.findById(staff.getId()).orElseThrow();
		assertThat(staff.isEnabled()).isTrue();
	}

	@Test
	void administrativeAccountsAreNotLifecycleTargets() {
		UserAccount admin = accounts.save(new UserAccount("admin", "hash", AccountRole.ADMIN));

		assertThatThrownBy(() -> staffAccounts.resetPassword(admin.getId(), TEMPORARY_PASSWORD))
				.isInstanceOf(NoSuchElementException.class);
		assertThatThrownBy(() -> staffAccounts.enable(admin.getId()))
				.isInstanceOf(NoSuchElementException.class);
		assertThatThrownBy(() -> staffAccounts.disable(admin.getId()))
				.isInstanceOf(NoSuchElementException.class);
	}
}
