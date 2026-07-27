package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;

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
class WeddingContentMigrationTest {
	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	WeddingSettingsRepository settings;

	@Autowired
	PartnerRepository partners;

	@Test
	void flywayCreatesWeddingContentAndBootstrapCreatesFixedRows() {
		assertThat(jdbc.queryForObject("""
				select count(*) from flyway_schema_history
				where version = '2' and script = 'V2__wedding_content.sql' and success = true
				""", Integer.class)).isEqualTo(1);
		assertThat(settings.getSingleton()).get()
				.extracting(WeddingSettings::getPublicationState,
						WeddingSettings::getTimeZone,
						WeddingSettings::getDefaultPhoneCountry)
				.containsExactly(PublicationState.DRAFT, "Asia/Jakarta", "ID");
		assertThat(partners.findAllByOrderByDisplayOrderAsc())
				.extracting(Partner::getDisplayOrder)
				.containsExactly(1, 2);
	}
}
