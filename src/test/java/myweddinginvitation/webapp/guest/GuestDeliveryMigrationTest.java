package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;

import myweddinginvitation.webapp.messaging.MessageTemplateRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"app.media-directory=target/test-media",
		"app.invitation.base-url=https://invite.example/i",
		"app.invitation.signing-secret=0123456789abcdef0123456789abcdef"
})
@Import(MySqlTestConfiguration.class)
class GuestDeliveryMigrationTest {
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	GuestCategoryRepository categories;

	@Autowired
	GuestRepository guests;

	@Autowired
	MessageTemplateRepository templates;

	@Test
	void flywayCreatesGuestDeliveryTablesAndSixTemplates() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '8' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(categories.count()).isZero();
		assertThat(guests.count()).isZero();
		assertThat(templates.count()).isEqualTo(6);
	}
}
