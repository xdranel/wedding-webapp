package myweddinginvitation.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class DatabaseMigrationTest {
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

		Integer successfulMigration = jdbc.queryForObject(
				"select count(*) from flyway_schema_history "
						+ "where version = '1' and script = 'V1__accounts.sql' and success = true",
				Integer.class);
		assertThat(successfulMigration).isEqualTo(1);
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
