package myweddinginvitation.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseMigrationTest extends MySqlContainerTest {
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserAccountRepository accounts;

	@Test
	void flywayCreatesUserAccountTable() {
		Integer count = jdbc.queryForObject(
				"select count(*) from information_schema.tables "
						+ "where table_schema = database() and table_name = 'user_account'",
				Integer.class);

		assertThat(count).isEqualTo(1);
	}

	@Test
	void savesAndFindsStaffAccounts() {
		accounts.save(new UserAccount("checkin", "{noop}secret", AccountRole.STAFF));

		assertThat(accounts.findByUsernameIgnoreCase("CHECKIN"))
				.isPresent()
				.get()
				.extracting(UserAccount::getRole, UserAccount::isEnabled)
				.containsExactly(AccountRole.STAFF, true);
		assertThat(accounts.countByRole(AccountRole.STAFF)).isEqualTo(1);
	}
}
