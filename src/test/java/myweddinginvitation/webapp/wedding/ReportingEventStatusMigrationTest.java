package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class ReportingEventStatusMigrationTest {
	private static final String STAGED_SCHEMA = "task_1_reporting_event_status";

	@Autowired
	MySQLContainer<?> mysql;

	@AfterEach
	void dropStagedSchema() {
		migrationJdbc().execute("drop database if exists " + STAGED_SCHEMA);
	}

	@Test
	void migrationPreservesV9ClosureAndAddsNullableStatusMetadataAndCopy() {
		migrationJdbc().execute("create database " + STAGED_SCHEMA);
		flyway("12").migrate();
		migrationJdbc().update("""
				insert into %s.wedding_settings (id, publication_state, time_zone,
				default_phone_country, accent_color, font_preset, event_closed)
				values (1, 'DRAFT', 'Asia/Jakarta', 'ID', '#7A5C48', 'CLASSIC', true)
				""".formatted(STAGED_SCHEMA));

		flyway(null).migrate();

		assertThat(migrationJdbc().queryForList("""
				select version from %s.flyway_schema_history where success = true order by installed_rank
				""".formatted(STAGED_SCHEMA), String.class))
				.containsExactlyElementsOf(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"));
		assertThat(migrationJdbc().queryForMap("""
				select event_closed, event_status_changed_at, event_status_changed_by,
				closed_title_id, closed_title_en, closed_message_id, closed_message_en
				from %s.wedding_settings where id = 1
				""".formatted(STAGED_SCHEMA)))
				.containsEntry("event_closed", true)
				.containsEntry("event_status_changed_at", null)
				.containsEntry("event_status_changed_by", null)
				.containsEntry("closed_title_id", null)
				.containsEntry("closed_title_en", null)
				.containsEntry("closed_message_id", null)
				.containsEntry("closed_message_en", null);
	}

	private Flyway flyway(String target) {
		return Flyway.configure().dataSource(migrationJdbc().getDataSource()).schemas(STAGED_SCHEMA).defaultSchema(STAGED_SCHEMA)
				.target(target == null ? "latest" : target).load();
	}

	private JdbcTemplate migrationJdbc() {
		return new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), "root", mysql.getPassword()));
	}
}
