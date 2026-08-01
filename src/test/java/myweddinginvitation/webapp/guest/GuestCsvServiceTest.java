package myweddinginvitation.webapp.guest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.List;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class GuestCsvServiceTest {
	@Autowired GuestCsvService service;
	@Autowired GuestService guestsService;
	@Autowired GuestRepository guests;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
		jdbc.update("insert into guest_category (display_name, normalized_name) values ('Keluarga', 'keluarga')");
	}

	@AfterEach
	void resetPhoneCountry() {
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note\nSari,081234567890,Ibu,Keluarga,true,ID,\"Catatan, penting\"\n",
			"\uFEFFdisplay_name;whatsapp_number;salutation;category;plus_one_allowed;preferred_language;internal_note\nSari;081234567890;Ibu;Keluarga;true;ID;Catatan\n"
	})
	void previewsCommaSemicolonAndBom(String csv) {
		GuestCsvPreview preview = service.preview(csv.getBytes(UTF_8));

		assertThat(preview.errors()).isEmpty();
		assertThat(preview.rows()).singleElement()
				.extracting(GuestCsvRow::normalizedWhatsappNumber)
				.isEqualTo("+6281234567890");
	}

	@Test
	void unknownCategoryIsAnErrorAndDatabaseAndFileDuplicatesAreWarnings() {
		guestsService.create(new GuestForm("Existing", "Ibu", "ID", "081234567890", null, false, MessageLanguage.ID, null), false);

		GuestCsvPreview preview = service.preview(csv(
				"Sari,081234567890,Ibu,Unknown,false,ID,",
				"Rina,081234567890,Ibu,Keluarga,false,ID,"));

		assertThat(preview.errors()).extracting(GuestCsvIssue::column).contains("category");
		assertThat(preview.warnings()).extracting(GuestCsvIssue::column).contains("whatsapp_number");
	}

	@Test
	void oneInvalidRowPreventsEveryInsert() {
		GuestCsvPreview preview = service.preview(csv(
				"Sari,081234567890,Ibu,Keluarga,false,ID,",
				"Rina,not-a-number,Ibu,Keluarga,false,ID,"));

		assertThatThrownBy(() -> service.importAll(preview)).isInstanceOf(IllegalArgumentException.class);
		assertThat(guests.count()).isZero();
	}

	@Test
	void rejectsOversizedFilesAndMoreThanTwoThousandRows() {
		assertThat(service.preview("x".repeat(2 * 1024 * 1024 + 1).getBytes(UTF_8)).errors())
				.extracting(GuestCsvIssue::column).contains("file");
		String rows = "Sari,081234567890,Ibu,Keluarga,false,ID,\n".repeat(2001);
		assertThat(service.preview((header() + rows).getBytes(UTF_8)).errors())
				.extracting(GuestCsvIssue::column).contains("file");
	}

	@Test
	void templateAndExportIncludeRequiredColumnsBomAndAllGuests() throws Exception {
		Guest active = guestsService.create(new GuestForm("Active", "Ibu", "ID", "081234567890", null, false, MessageLanguage.ID, "note"), false);
		Guest archived = guestsService.create(new GuestForm("Archived", "Bapak", "ID", "081234567891", null, true, MessageLanguage.EN, null), false);
		guestsService.archive(archived.getId(), archived.getVersion());
		ByteArrayOutputStream template = new ByteArrayOutputStream();
		ByteArrayOutputStream export = new ByteArrayOutputStream();

		service.template(template);
		service.exportAll(export);

		String expectedHeader = "display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note";
		String output = export.toString(UTF_8);
		assertThat(template.toString(UTF_8)).isEqualTo("\uFEFF" + expectedHeader + "\r\n");
		assertThat(output).startsWith("\uFEFFdisplay_name,")
				.contains("Active,+6281234567890,Ibu,,false,ID,note,ACTIVE")
				.contains("Archived,+6281234567891,Bapak,,true,EN,,ARCHIVED")
				.contains("archive_state,archived_at,delivery_state,first_sent_at,last_sent_at,created_at,updated_at")
				.doesNotContain("signing-secret");
		assertThat(active.getId()).isNotNull();
	}

	@Test
	void exportAppendsRsvpFieldsAndNeutralizesWrittenContent() throws Exception {
		Guest attending = guestsService.create(new GuestForm("Attending", "Ibu", "ID", "081234567890",
				null, true, MessageLanguage.ID, null), false);
		Guest declined = guestsService.create(new GuestForm("Declined", "Bapak", "ID", "081234567891",
				null, false, MessageLanguage.EN, null), false);
		guestsService.create(new GuestForm("No RSVP", "Saudara", "ID", "081234567892",
				null, false, MessageLanguage.ID, null), false);
		jdbc.update("""
				insert into rsvp (guest_id, response, planned_attendee_count, greeting,
				    greeting_public_consent, greeting_moderation_state, private_organizer_note,
				    update_source, created_at, updated_at)
				values (?, 'HADIR', 2, '=greeting', true, 'APPROVED', '+private note',
				    'ADMIN', '2026-08-01 00:00:00', '2026-08-01 00:00:00')
				""", attending.getId());
		jdbc.update("""
				insert into rsvp (guest_id, response, planned_attendee_count, greeting,
				    greeting_public_consent, greeting_moderation_state, private_organizer_note,
				    update_source, created_at, updated_at)
				values (?, 'TIDAK_HADIR', 0, '-greeting', false, 'HIDDEN', '@private note',
				    'GUEST', '2026-08-01 01:00:00', '2026-08-01 01:00:00')
				""", declined.getId());

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		service.exportAll(output);
		try (CSVParser parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get()
				.parse(new StringReader(output.toString(UTF_8).substring(1)))) {
			assertThat(parser.getHeaderNames()).containsExactly(
					"display_name", "whatsapp_number", "salutation", "category", "plus_one_allowed",
					"preferred_language", "internal_note", "archive_state", "archived_at", "delivery_state",
					"first_sent_at", "last_sent_at", "created_at", "updated_at",
					"rsvp_status", "planned_attendee_count", "greeting", "greeting_public_consent",
					"greeting_moderation_status", "private_organizer_note", "rsvp_updated_by", "rsvp_updated_at");
			List<CSVRecord> records = parser.getRecords();
			CSVRecord attendingRow = row(records, "Attending");
			assertThat(attendingRow.get("rsvp_status")).isEqualTo("HADIR");
			assertThat(attendingRow.get("planned_attendee_count")).isEqualTo("2");
			assertThat(attendingRow.get("greeting")).isEqualTo("'=greeting");
			assertThat(attendingRow.get("greeting_public_consent")).isEqualTo("true");
			assertThat(attendingRow.get("greeting_moderation_status")).isEqualTo("APPROVED");
			assertThat(attendingRow.get("private_organizer_note")).isEqualTo("'+private note");
			assertThat(attendingRow.get("rsvp_updated_by")).isEqualTo("ADMIN");
			assertThat(attendingRow.get("rsvp_updated_at")).isEqualTo("2026-08-01T00:00:00Z");

			CSVRecord declinedRow = row(records, "Declined");
			assertThat(declinedRow.get("greeting")).isEqualTo("'-greeting");
			assertThat(declinedRow.get("private_organizer_note")).isEqualTo("'@private note");
			assertThat(declinedRow.get("rsvp_updated_by")).isEqualTo("GUEST");

			CSVRecord blankRow = row(records, "No RSVP");
			assertThat(List.of("rsvp_status", "planned_attendee_count", "greeting",
					"greeting_public_consent", "greeting_moderation_status", "private_organizer_note",
					"rsvp_updated_by", "rsvp_updated_at"))
					.allSatisfy(column -> assertThat(blankRow.get(column)).isBlank());
		}
	}

	@Test
	void internationalCsvNumberIgnoresWeddingDefaultAndExportStaysE164() throws Exception {
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
		GuestCsvPreview preview = service.preview((header()
				+ "Ada,+49 1512 3456789,Frau,,false,EN,\n").getBytes(UTF_8));

		assertThat(preview.hasErrors()).isFalse();
		service.importAll(preview);
		assertThat(guests.findAll()).singleElement()
				.extracting(Guest::getNormalizedWhatsappNumber)
				.isEqualTo("+4915123456789");

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		service.exportAll(output);
		assertThat(output.toString(UTF_8)).contains("+4915123456789");
	}

	@Test
	void nationalCsvNumberStillUsesWeddingDefault() {
		jdbc.update("update wedding_settings set default_phone_country = 'DE' where id = 1");
		GuestCsvPreview preview = service.preview((header()
				+ "Ada,01512 3456789,Frau,,false,EN,\n").getBytes(UTF_8));

		assertThat(preview.rows()).singleElement()
				.extracting(GuestCsvRow::normalizedWhatsappNumber)
				.isEqualTo("+4915123456789");
	}

	private byte[] csv(String... rows) {
		return (header() + String.join("\n", rows) + "\n").getBytes(UTF_8);
	}

	private String header() {
		return "display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note\n";
	}

	private CSVRecord row(List<CSVRecord> records, String name) {
		return records.stream().filter(record -> name.equals(record.get("display_name"))).findFirst().orElseThrow();
	}
}
