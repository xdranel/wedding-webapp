package myweddinginvitation.webapp.guest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
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
		jdbc.update("insert into guest_category (display_name, normalized_name) values ('Keluarga', 'keluarga')");
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
		guestsService.create(new GuestForm("Existing", "Ibu", "081234567890", null, false, MessageLanguage.ID, null), false);

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
		Guest active = guestsService.create(new GuestForm("Active", "Ibu", "081234567890", null, false, MessageLanguage.ID, "note"), false);
		Guest archived = guestsService.create(new GuestForm("Archived", "Bapak", "081234567891", null, true, MessageLanguage.EN, null), false);
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

	private byte[] csv(String... rows) {
		return (header() + String.join("\n", rows) + "\n").getBytes(UTF_8);
	}

	private String header() {
		return "display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note\n";
	}
}
