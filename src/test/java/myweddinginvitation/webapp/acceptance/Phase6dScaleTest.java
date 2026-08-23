package myweddinginvitation.webapp.guest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import myweddinginvitation.webapp.checkin.CheckInRepository;
import myweddinginvitation.webapp.reporting.ReportMetrics;
import myweddinginvitation.webapp.reporting.ReportService;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class Phase6dScaleTest {
	private static final int GUESTS = 2_000;
	private static final int PAGE_SIZE = 125;
	private static final int BATCH_SIZE = 200;
	private static final Instant SENT_AT = Instant.parse("2026-08-23T01:00:00Z");
	private static final ReportMetrics ALL_GUESTS = new ReportMetrics(
			2_000, 3_000, 667, 667, 666, 1_001, 500, 1_000, 500, 1,
			1_000, 1_000, 500, 1_500, 400, 1_600, 0);
	private static final ReportMetrics FIRST_CATEGORY = new ReportMetrics(
			400, 600, 134, 133, 133, 201, 100, 200, 100, 1,
			200, 200, 100, 300, 400, 0, 0);

	@Autowired GuestRepository guests;
	@Autowired GuestCategoryRepository categories;
	@Autowired GuestService guestService;
	@Autowired RsvpRepository rsvps;
	@Autowired CheckInRepository checkIns;
	@Autowired ReportService reports;
	@Autowired GuestCsvService csvs;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		cleanUp();
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	@Test
	void twoThousandGuestsRemainSearchableReportableAndExportable() throws Exception {
		List<GuestCategory> seededCategories = categories.saveAll(List.of(
				GuestCategory.create("Category 0", "category 0"),
				GuestCategory.create("Category 1", "category 1"),
				GuestCategory.create("Category 2", "category 2"),
				GuestCategory.create("Category 3", "category 3"),
				GuestCategory.create("Category 4", "category 4")));
		categories.flush();
		seedGuests(seededCategories);

		var page = guestService.search(new GuestListQuery("Scale Guest", null, false, null, null, false, null),
				PageRequest.of(0, PAGE_SIZE, Sort.by("displayName").ascending()));
		assertThat(page.getTotalElements()).isEqualTo(2_000);
		assertThat(page.getContent()).hasSize(PAGE_SIZE);
		assertThat(rsvps.count()).isEqualTo(1_334);
		assertThat(checkIns.count()).isEqualTo(500);
		assertThat(reports.snapshot(null).totals()).isEqualTo(ALL_GUESTS);
		assertThat(reports.snapshot(seededCategories.getFirst().getId()).totals()).isEqualTo(FIRST_CATEGORY);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		csvs.exportAll(output);
		try (CSVParser parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get()
				.parse(new StringReader(output.toString(UTF_8).substring(1)))) {
			List<CSVRecord> rows = parser.getRecords();
			assertThat(rows).hasSize(GUESTS);
			assertThat(rows).anySatisfy(row -> assertThat(row.get("display_name"))
					.isEqualTo("'=SUM(1,1) Scale Guest 0000"));
		}
	}

	private void seedGuests(List<GuestCategory> seededCategories) {
		long checkInAccountId = jdbc.queryForObject("select id from user_account order by id limit 1", Long.class);
		List<Guest> batch = new ArrayList<>(BATCH_SIZE);
		List<Object[]> rsvpRows = new ArrayList<>();
		List<Object[]> checkInRows = new ArrayList<>();
		for (int index = 0; index < GUESTS; index++) {
			Guest guest = Guest.create(new GuestForm(name(index), "Guest", "ID", phone(index), null,
					index % 2 == 0, index % 2 == 0 ? MessageLanguage.ID : MessageLanguage.EN, null), phone(index),
					seededCategories.get(index % seededCategories.size()));
			if (index % 2 == 0) guest.confirmSent(SENT_AT);
			if (index % 4 == 0) guest.confirmRsvpReminder(SENT_AT);
			if (index % 5 == 0) guest.confirmEventReminder(SENT_AT);
			batch.add(guest);
			if (batch.size() == BATCH_SIZE || index == GUESTS - 1) {
				for (Guest saved : guests.saveAll(batch)) {
					int savedIndex = Integer.parseInt(saved.getDisplayName().substring(saved.getDisplayName().length() - 4));
					if (savedIndex % 3 != 2) {
						boolean attending = savedIndex % 3 == 0;
						rsvpRows.add(new Object[] {saved.getId(), attending ? "HADIR" : "TIDAK_HADIR",
								attending ? savedIndex % 2 == 0 ? 2 : 1 : 0, false, "HIDDEN", "ADMIN", Timestamp.from(SENT_AT),
								Timestamp.from(SENT_AT)});
					}
					if (savedIndex % 4 == 0) {
						checkInRows.add(new Object[] {saved.getId(), 2, checkInAccountId, Timestamp.from(SENT_AT)});
					}
				}
				guests.flush();
				batch.clear();
			}
		}
		jdbc.batchUpdate("""
				insert into rsvp (guest_id, response, planned_attendee_count, greeting_public_consent,
					greeting_moderation_state, update_source, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""", rsvpRows);
		jdbc.batchUpdate("""
				insert into check_in (guest_id, actual_attendee_count, checked_in_by_account_id, checked_in_at,
					version, rsvp_auto_changed, previous_rsvp_response, previous_planned_attendee_count,
					rsvp_version_after_change)
				values (?, ?, ?, ?, 0, false, null, null, null)
				""", checkInRows);
	}

	private String name(int index) {
		return (index == 0 ? "=SUM(1,1) " : "") + "Scale Guest %04d".formatted(index);
	}

	private String phone(int index) {
		return "+62000%010d".formatted(index);
	}

	private void cleanUp() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
	}
}
