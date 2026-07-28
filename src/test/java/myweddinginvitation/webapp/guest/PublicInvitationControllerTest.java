package myweddinginvitation.webapp.guest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
				closing_text_en = null, accent_color = '#7A5C48', font_preset = 'CLASSIC'
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
				.andExpect(content().string(not(containsString(WHATSAPP))))
				.andExpect(content().string(not(containsString(INTERNAL_NOTE))))
				.andExpect(content().string(not(containsString("RSVP"))))
				.andExpect(content().string(not(containsString("PIN"))))
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
				GUEST_NAME, "Ibu", WHATSAPP, null, false, language, INTERNAL_NOTE), false);
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
				update partner set full_name = ?, nickname = ?, child_of_label_id = ?,
				child_of_label_en = null, parents_names_id = ?, parents_names_en = null
				where display_order = ?
				""", "Rama Pratama", "Rama", "Putra", "Keluarga Pratama", 1);
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
