package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class WeddingContentServiceTest {
	@Autowired
	WeddingContentService service;

	@Autowired
	WeddingSettingsRepository settings;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void resetContent() {
		jdbc.update("delete from story_entry");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update partner set full_name = null, nickname = null, photo_path = null,
				child_of_label_id = null, child_of_label_en = null,
				parents_names_id = null, parents_names_en = null, instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set publication_state = 'DRAFT', couple_title = null,
				opening_text_id = null, opening_text_en = null, closing_text_id = null,
				closing_text_en = null, time_zone = 'Asia/Jakarta', calendar_downloads_enabled = false
				where id = 1
				""");
	}

	@Test
	void publicationRequiresTwoPartnersAndOneCompleteVisibleEvent() {
		PublicationCheck publication = service.publish();

		assertThat(publication.published()).isFalse();
		assertThat(publication.errors())
				.contains("Partner 1: full name is required",
						"Partner 2: full name is required",
						"At least one complete event must be visible");
		assertThat(settings.getSingleton().orElseThrow().getPublicationState())
				.isEqualTo(PublicationState.DRAFT);
	}

	@Test
	void englishFallsBackToIndonesian() {
		assertThat(WeddingContentService.localized("Selamat datang", "", "EN"))
				.isEqualTo("Selamat datang");
		assertThat(WeddingContentService.localized("Selamat datang", "Welcome", "EN"))
				.isEqualTo("Welcome");
	}

	@Test
	void previewUsesEarliestVisibleEventDate() {
		completePartners("Rama", "Shinta");
		visibleEvent(EventType.CEREMONY, LocalDate.of(2027, 5, 2), null);
		visibleEvent(EventType.RECEPTION, LocalDate.of(2027, 5, 1), null);

		assertThat(service.preview("Bapak/Ibu", "Nama Tamu", "ID").coverDate())
				.isEqualTo(LocalDate.of(2027, 5, 1));
	}

	@Test
	void previewDerivesEmptyCoupleTitleFromOrderedNicknames() {
		completePartners("Rama", "Shinta");

		assertThat(service.preview("Bapak/Ibu", "Nama Tamu", "ID").coupleTitle())
				.isEqualTo("Rama & Shinta");
	}

	@Test
	void publicationRejectsInvalidTimeZoneAndEventEndBeforeStart() {
		completePartners("Rama", "Shinta");
		jdbc.update("update wedding_settings set time_zone = 'Not/AZone' where id = 1");
		visibleEvent(EventType.CEREMONY, LocalDate.of(2027, 5, 1), "09:00:00");
		jdbc.update("update event_part set end_time = '08:00:00' where event_type = 'CEREMONY'");

		assertThat(service.checkPublication().errors())
				.contains("Time zone is invalid", "Ceremony: end time must be after start time");
	}

	@Test
	void publicationRequiresNavigableHttpMapUrl() {
		completePartners("Rama", "Shinta");
		visibleEvent(EventType.CEREMONY, LocalDate.of(2027, 5, 1), null);

		assertThat(service.publish()).isEqualTo(new PublicationCheck(true, java.util.List.of()));
		for (String invalidUrl : java.util.List.of("https:maps.example.test", "https:/maps.example.test", "ftp://maps.example.test")) {
			jdbc.update("update event_part set map_url = ? where event_type = 'CEREMONY'", invalidUrl);

			assertThat(service.checkPublication().errors())
					.contains("Ceremony: map URL must use HTTP or HTTPS", "At least one complete event must be visible");
		}
	}

	@Test
	void successfulPublicationCanReturnToDraft() {
		completePartners("Rama", "Shinta");
		visibleEvent(EventType.CEREMONY, LocalDate.of(2027, 5, 1), null);

		assertThat(service.publish()).isEqualTo(new PublicationCheck(true, java.util.List.of()));
		service.returnToDraft();

		assertThat(settings.getSingleton().orElseThrow().getPublicationState())
				.isEqualTo(PublicationState.DRAFT);
	}

	@Test
	void staleSettingsEditDoesNotOverwriteNewerSettings() {
		WeddingSettingsForm stale = service.settingsForm();
		WeddingSettingsForm newer = service.settingsForm();
		newer.setCoupleTitle("Newer title");
		service.saveSettings(newer);
		stale.setCoupleTitle("Stale title");

		assertThatThrownBy(() -> service.saveSettings(stale))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(settings.getSingleton().orElseThrow().getCoupleTitle()).isEqualTo("Newer title");
	}

	@Test
	void settingsRoundTripPhaseFourSwitches() {
		WeddingSettingsForm form = service.settingsForm();
		form.setEventClosed(true);
		form.setGreetingsEnabled(false);
		form.setPrivateOrganizerNoteEnabled(true);

		service.saveSettings(form);

		assertThat(service.settingsForm())
				.extracting(WeddingSettingsForm::isEventClosed, WeddingSettingsForm::isGreetingsEnabled,
						WeddingSettingsForm::isPrivateOrganizerNoteEnabled)
				.containsExactly(true, false, true);
	}

	@Test
	void settingsRoundTripCalendarDownloadsSwitch() {
		WeddingSettingsForm form = service.settingsForm();
		assertThat(form.isCalendarDownloadsEnabled()).isFalse();
		form.setCalendarDownloadsEnabled(true);

		service.saveSettings(form);

		assertThat(service.settingsForm().isCalendarDownloadsEnabled()).isTrue();
	}

	@Test
	void staleSettingsEditDoesNotUndoPublication() {
		completePartners("Rama", "Shinta");
		visibleEvent(EventType.CEREMONY, LocalDate.of(2027, 5, 1), null);
		WeddingSettingsForm stale = service.settingsForm();

		assertThat(service.publish()).isEqualTo(new PublicationCheck(true, java.util.List.of()));
		stale.setCoupleTitle("Stale title");

		assertThatThrownBy(() -> service.saveSettings(stale))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(settings.getSingleton().orElseThrow().getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
	}

	@Test
	void overviewReportsMissingEnglishTranslationsForEverySection() {
		completePartners("Rama", "Shinta");
		visibleEvent(EventType.CEREMONY, LocalDate.of(2027, 5, 1), null);
		jdbc.update("update wedding_settings set opening_text_id = 'Selamat datang', closing_text_id = 'Terima kasih' where id = 1");
		jdbc.update("insert into story_entry (title_id, body_id, display_order) values ('Pertama bertemu', 'Cerita kami', 1)");

		WeddingOverview overview = service.overview();

		assertThat(overview.settingsComplete()).isTrue();
		assertThat(overview.partnerComplete()).containsExactly(true, true);
		assertThat(overview.completeVisibleEvents()).containsExactly(EventType.CEREMONY);
		assertThat(overview.storyPresent()).isTrue();
		assertThat(overview.translationWarnings())
				.contains("Story 1: English title is missing", "Story 1: English body is missing");
	}

	private void completePartners(String firstNickname, String secondNickname) {
		completePartner(1, "Rama Pratama", firstNickname);
		completePartner(2, "Shinta Lestari", secondNickname);
	}

	private void completePartner(int displayOrder, String fullName, String nickname) {
		jdbc.update("""
				update partner set full_name = ?, nickname = ?, photo_path = ?,
				child_of_label_id = ?, parents_names_id = ? where display_order = ?
				""", fullName, nickname, "/photos/" + displayOrder + ".jpg", "Putra", "Keluarga", displayOrder);
	}

	private void visibleEvent(EventType type, LocalDate date, String endTime) {
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, end_time,
				venue_name, address_id, map_url)
				values (?, true, ?, '08:00:00', ?, 'Gedung', 'Jakarta', 'https://maps.example.test')
				""", type.name(), date, endTime);
	}
}
