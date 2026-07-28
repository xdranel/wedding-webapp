package myweddinginvitation.webapp.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class UserAccountSecurityTest {
	private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");

	@Test
	void fifthConsecutiveFailureLocksLoginForFifteenMinutes() {
		UserAccount account = new UserAccount("staff", "hash", AccountRole.STAFF);

		for (int attempt = 0; attempt < 5; attempt++) {
			account.loginFailed(NOW);
		}

		assertThat(account.getFailedLoginCount()).isEqualTo(5);
		assertThat(account.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
		assertThat(account.isLoginLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
		assertThat(account.isLoginLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
	}

	@Test
	void successfulLoginClearsFailureState() {
		UserAccount account = new UserAccount("staff", "hash", AccountRole.STAFF);
		account.loginFailed(NOW);

		account.loginSucceeded();

		assertThat(account.getFailedLoginCount()).isZero();
		assertThat(account.getLockedUntil()).isNull();
	}

	@Test
	void lockedAccountIsRejectedByAuthentication() {
		UserAccount account = new UserAccount("staff", "hash", AccountRole.STAFF);
		for (int attempt = 0; attempt < 5; attempt++) {
			account.loginFailed(Instant.now());
		}
		UserAccountRepository repository = (UserAccountRepository) Proxy.newProxyInstance(
				UserAccountRepository.class.getClassLoader(),
				new Class<?>[] {UserAccountRepository.class},
				(proxy, method, arguments) -> Optional.of(account));

		var details = new DatabaseUserDetailsService(repository).loadUserByUsername("staff");

		assertThat(details.isAccountNonLocked()).isFalse();
	}

	@Test
	void passwordChangeAndDisableRevokeExistingSessions() {
		UserAccount account = new UserAccount("staff", "old", AccountRole.STAFF);

		account.changePassword("new");
		assertThat(account.getPasswordHash()).isEqualTo("new");
		assertThat(account.isPasswordChangeRequired()).isFalse();
		assertThat(account.getSessionVersion()).isEqualTo(1);

		account.disable();
		assertThat(account.isEnabled()).isFalse();
		assertThat(account.getSessionVersion()).isEqualTo(2);
	}
}
