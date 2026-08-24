package myweddinginvitation.webapp.guest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;
import java.util.UUID;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class PublicInvitationControllerTest {
	private static final String GUEST_NAME = "Sari";
	private static final String WHATSAPP = "+628123456789";
	private static final String INTERNAL_NOTE = "private-note-probe";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private GuestService guestService;

	@Autowired
	private InvitationLinkSigner signer;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from guest");
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
				closing_text_en = null, rsvp_deadline = '2027-04-30 23:59:59', event_closed = false,
				closed_title_id = null, closed_title_en = null, closed_message_id = null, closed_message_en = null,
				greetings_enabled = true, private_organizer_note_enabled = false,
				calendar_downloads_enabled = false, time_zone = 'Asia/Jakarta',
				accent_color = '#7A5C48', font_preset = 'CLASSIC'
				where id = 1
				""");
		seedWeddingContent();
	}

	@Test
	void publishedActiveGuestSeesPersonalizedInvitationWithoutPrivateData() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		publishWedding();

		mockMvc.perform(get(path(signer.urlFor(guest))).param("language", "ID"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(view().name("guest/invitation"))
				.andExpect(content().string(containsString("lang=\"id\"")))
				.andExpect(content().string(containsString("Ibu Sari")))
				.andExpect(content().string(containsString("Dengan hormat")))
				.andExpect(content().string(containsString("/css/invitation.css")))
				.andExpect(content().string(containsString("noindex, nofollow")))
				.andExpect(content().string(containsString("/media/partner/")))
				.andExpect(content().string(not(containsString("rama.jpg"))))
				.andExpect(content().string(not(containsString(WHATSAPP))))
				.andExpect(content().string(not(containsString(INTERNAL_NOTE))))
				.andExpect(content().string(containsString("RSVP")))
				.andExpect(content().string(containsString("PIN")))
				.andExpect(content().string(not(containsString("QRCode"))))
				.andExpect(content().string(not(containsString("/admin/"))));
	}

	@Test
	void englishSelectionUsesEnglishLabelsAndFallsBackToIndonesianContent() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		publishWedding();

		mockMvc.perform(get(path(signer.urlFor(guest))).param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/invitation"))
				.andExpect(content().string(containsString("lang=\"en\"")))
				.andExpect(content().string(containsString("Wedding invitation")))
				.andExpect(content().string(containsString("The couple")))
				.andExpect(content().string(containsString("With joy")))
				.andExpect(content().string(containsString("Putra")))
				.andExpect(content().string(containsString("Jakarta")));
	}

	@Test
	void absentLanguageUsesGuestPreference() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.EN);
		publishWedding();

		mockMvc.perform(get(path(signer.urlFor(guest))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("lang=\"en\"")))
				.andExpect(content().string(containsString("Wedding invitation")));
	}

	@Test
	void enabledCompleteEventsRenderLocalizedSignedCalendarLinksBesideTheirEvent() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		publishWedding();
		jdbc.update("update wedding_settings set calendar_downloads_enabled = true where id = 1");
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, address_en, map_url)
				values ('RECEPTION', true, '2027-05-01', '18:00:00', 'Ballroom', 'Jakarta', 'Jakarta',
				'https://maps.example.test/reception')
				""");
		String invitationPath = path(signer.urlFor(guest));

		String indonesian = mockMvc.perform(get(invitationPath).param("language", "ID"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String english = mockMvc.perform(get(invitationPath).param("language", "EN"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		String idLink = invitationPath + "/calendar/CEREMONY.ics?language=ID";
		String enLink = invitationPath + "/calendar/CEREMONY.ics?language=EN";
		String idReceptionLink = invitationPath + "/calendar/RECEPTION.ics?language=ID";
		String enReceptionLink = invitationPath + "/calendar/RECEPTION.ics?language=EN";
		assertThat(indonesian).contains("<h3 id=\"event-CEREMONY\">Akad", "href=\"" + idLink + "\"",
				"Tambahkan Akad ke Kalender", "href=\"" + idReceptionLink + "\"", "Tambahkan Resepsi ke Kalender");
		assertThat(indonesian.indexOf(idLink)).isGreaterThan(indonesian.indexOf("id=\"event-CEREMONY\""));
		assertThat(english).contains("<h3 id=\"event-CEREMONY\">Ceremony", "href=\"" + enLink + "\"",
				"Add Ceremony to Calendar", "href=\"" + enReceptionLink + "\"", "Add Reception to Calendar");
		assertThat(english.indexOf(enLink)).isGreaterThan(english.indexOf("id=\"event-CEREMONY\""));
	}

	@Test
	void disabledHiddenAndIncompleteEventsDoNotRenderCalendarLinks() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		publishWedding();
		String invitationPath = path(signer.urlFor(guest));

		String disabled = page(invitationPath);
		jdbc.update("update wedding_settings set calendar_downloads_enabled = true where id = 1");
		jdbc.update("update event_part set visible = false where event_type = 'CEREMONY'");
		String hidden = page(invitationPath);
		jdbc.update("update event_part set visible = true, address_id = null where event_type = 'CEREMONY'");
		String incomplete = page(invitationPath);

		assertThat(disabled).doesNotContain("/calendar/", "Tambahkan Akad ke Kalender");
		assertThat(hidden).doesNotContain("/calendar/", "Tambahkan Akad ke Kalender");
		assertThat(incomplete).doesNotContain("/calendar/", "Tambahkan Akad ke Kalender");
	}

	@Test
	void legacyNonJakartaWeddingOmitsCalendarLinksAndRejectsDownloads() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		publishWedding();
		jdbc.update("update wedding_settings set calendar_downloads_enabled = true, time_zone = 'Asia/Makassar' where id = 1");
		String invitationPath = path(signer.urlFor(guest));

		mockMvc.perform(get(invitationPath).param("language", "ID"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("/calendar/"))));
		mockMvc.perform(get(invitationPath + "/calendar/CEREMONY.ics").param("language", "ID"))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(""));
	}

	@ParameterizedTest
	@EnumSource(UnavailableCase.class)
	void unavailableStatesUseOneNeutralViewWithoutGuestData(UnavailableCase unavailableCase) throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		String path = unavailablePath(unavailableCase, guest);

		mockMvc.perform(get(path))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(view().name("guest/unavailable"))
				.andExpect(content().string(containsString("Undangan tidak tersedia")))
				.andExpect(content().string(containsString("Invitation unavailable")))
				.andExpect(content().string(containsString("noindex, nofollow")))
				.andExpect(content().string(not(containsString(GUEST_NAME))))
				.andExpect(content().string(not(containsString(WHATSAPP))))
				.andExpect(content().string(not(containsString(INTERNAL_NOTE))));
	}

	@Test
	void closedEventUsesConfiguredFallbackBeforeSignedGuestResolution() throws Exception {
		Guest guest = savedActiveGuest(MessageLanguage.ID);
		jdbc.update("""
				update wedding_settings set event_closed = true, closed_title_id = 'Acara kami telah selesai',
				closed_message_id = 'Terima kasih telah hadir', closed_title_en = null, closed_message_en = null
				where id = 1
				""");

		mockMvc.perform(get(path(signer.urlFor(guest))).param("language", "ID"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(view().name("guest/closed"))
				.andExpect(content().string(containsString("lang=\"id\"")))
				.andExpect(content().string(containsString("Acara kami telah selesai")))
				.andExpect(content().string(containsString("Terima kasih telah hadir")))
				.andExpect(content().string(not(containsString(GUEST_NAME))))
				.andExpect(content().string(not(containsString("RSVP"))))
				.andExpect(content().string(not(containsString("/media/"))))
				.andExpect(content().string(not(containsString("/calendar/"))));

		mockMvc.perform(get("/i/not-a-uuid/not-a-version/not-a-signature").param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(view().name("guest/closed"))
				.andExpect(content().string(containsString("lang=\"en\"")))
				.andExpect(content().string(containsString("Acara kami telah selesai")))
				.andExpect(content().string(containsString("Terima kasih telah hadir")))
				.andExpect(content().string(not(containsString(GUEST_NAME))));
	}

	@Test
	void closedEventUsesApplicationDefaultsWhenConfiguredCopyIsMissing() throws Exception {
		jdbc.update("update wedding_settings set event_closed = true where id = 1");

		mockMvc.perform(get("/i/not-a-uuid/not-a-version/not-a-signature").param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/closed"))
				.andExpect(content().string(containsString("The event has ended")))
				.andExpect(content().string(containsString("Thank you for being part of our celebration.")));
	}

	private String unavailablePath(UnavailableCase unavailableCase, Guest guest) {
		return switch (unavailableCase) {
			case DRAFT -> path(signer.urlFor(guest));
			case INVALID_SIGNATURE -> {
				publishWedding();
				yield path(signer.urlFor(guest)) + "x";
			}
			case OLD_VERSION -> {
				publishWedding();
				yield path(signer.url(guest.getPublicId(), guest.getInvitationTokenVersion() - 1));
			}
			case ARCHIVED -> {
				publishWedding();
				guestService.archive(guest.getId(), guest.getVersion());
				yield path(signer.urlFor(guest));
			}
			case MISSING -> {
				publishWedding();
				yield path(signer.url(UUID.fromString("fc91be34-48a8-4388-8e56-e070d53e2068"), 1));
			}
			case MALFORMED_PUBLIC_ID -> "/i/not-a-uuid/1/not-a-signature";
			case MALFORMED_VERSION -> "/i/" + guest.getPublicId() + "/not-a-version/not-a-signature";
		};
	}

	private Guest savedActiveGuest(MessageLanguage language) {
		return guestService.create(new GuestForm(
				GUEST_NAME, "Ibu", "ID", WHATSAPP, null, false, language, INTERNAL_NOTE), false);
	}

	private String page(String invitationPath) throws Exception {
		return mockMvc.perform(get(invitationPath).param("language", "ID"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private void publishWedding() {
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED' where id = 1");
	}

	private void seedWeddingContent() {
		jdbc.update("""
				update wedding_settings set couple_title = 'Rama & Shinta',
				opening_text_id = 'Dengan hormat', opening_text_en = 'With joy',
				closing_text_id = 'Terima kasih', closing_text_en = null,
				accent_color = '#2E5E4E', font_preset = 'MODERN' where id = 1
				""");
		jdbc.update("""
				update partner set full_name = ?, nickname = ?, photo_path = ?, child_of_label_id = ?,
				child_of_label_en = null, parents_names_id = ?, parents_names_en = null
				where display_order = ?
				""", "Rama Pratama", "Rama", "rama.jpg", "Putra", "Keluarga Pratama", 1);
		jdbc.update("""
				update partner set full_name = ?, nickname = ?, child_of_label_id = ?,
				child_of_label_en = null, parents_names_id = ?, parents_names_en = null
				where display_order = ?
				""", "Shinta Lestari", "Shinta", "Putri", "Keluarga Lestari", 2);
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name,
				address_id, address_en, map_url)
				values ('CEREMONY', true, '2027-05-01', '08:00:00', 'Gedung Bahagia',
				'Jakarta', null, 'https://maps.example.test')
				""");
	}

	private String path(String url) {
		return URI.create(url).getRawPath();
	}

	private enum UnavailableCase {
		DRAFT,
		INVALID_SIGNATURE,
		OLD_VERSION,
		ARCHIVED,
		MISSING,
		MALFORMED_PUBLIC_ID,
		MALFORMED_VERSION
	}
}
