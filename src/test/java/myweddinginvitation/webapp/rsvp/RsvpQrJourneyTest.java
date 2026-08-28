package myweddinginvitation.webapp.rsvp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.wedding.PublicationState;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, RsvpQrJourneyTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class RsvpQrJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired GuestRepository guests;
	@Autowired InvitationLinkSigner invitationLinks;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired RsvpRepository rsvps;
	@Autowired WeddingSettingsRepository settings;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;

	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update partner set full_name = null, nickname = null, photo_path = null,
				child_of_label_id = null, child_of_label_en = null,
				parents_names_id = null, parents_names_en = null, instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set publication_state = 'DRAFT', event_closed = false,
				couple_title = null, opening_text_id = null, opening_text_en = null,
				closing_text_id = null, closing_text_en = null, time_zone = 'Asia/Jakarta',
				rsvp_deadline = null, default_phone_country = 'ID', accent_color = '#7A5C48',
				font_preset = 'CLASSIC', greetings_enabled = true,
				private_organizer_note_enabled = false where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		adminSession = login();
	}

	@Test
	void guestRsvpQrModerationAdminCorrectionAndExportShareOneLifecycle() throws Exception {
		makeWeddingPublishable();
		configureAndPublishWedding();
		Guest primary = createGuest("Journey Guest", "+6281234567890", true);
		Guest viewer = createGuest("Greeting Viewer", "+4915123456789", false);
		String primaryPath = path(primary);

		mockMvc.perform(get(primaryPath).param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/invitation"))
				.andExpect(content().string(containsString("Journey Guest")))
				.andExpect(content().string(containsString("Welcome")))
				.andExpect(content().string(not(containsString("Dengan hormat"))));

		MvcResult saved = mockMvc.perform(post(primaryPath + "/rsvp").with(csrf())
				.param("language", "EN")
				.param("response", "HADIR").param("plannedAttendeeCount", "2")
				.param("greeting", "Journey greeting").param("greetingPublicConsent", "true")
				.param("privateOrganizerNote", "Private journey note")
				.param("pin", "7890").param("version", "-1"))
				.andExpect(redirectedUrl(primaryPath + "?language=EN&rsvpSaved"))
				.andReturn();
		MockHttpSession guestSession = (MockHttpSession) saved.getRequest().getSession(false);

		byte[] displayPng = mockMvc.perform(get(primaryPath + "/qr.png").session(guestSession))
				.andExpect(status().isOk()).andExpect(content().contentType("image/png"))
				.andReturn().getResponse().getContentAsByteArray();
		byte[] downloadPng = mockMvc.perform(get(primaryPath + "/qr-download.png").session(guestSession))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"wedding-check-in-qr.png\""))
				.andReturn().getResponse().getContentAsByteArray();
		String savedPayload = decode(displayPng);
		assertThat(downloadPng).isNotEmpty();
		BufferedImage downloaded = ImageIO.read(new ByteArrayInputStream(downloadPng));
		assertThat(downloaded.getWidth()).isEqualTo(1024);
		assertThat(downloaded.getHeight()).isEqualTo(1024);
		assertThat(decode(downloadPng)).isEqualTo(savedPayload);
		assertThat(qrSigner.verify(savedPayload)).get()
				.extracting(QrReference::publicId, QrReference::tokenVersion)
				.containsExactly(primary.getPublicId(), primary.getInvitationTokenVersion());

		Rsvp pending = rsvps.findByGuestId(primary.getId()).orElseThrow();
		mockMvc.perform(post("/admin/greetings/{id}/approve", pending.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(pending.getVersion())))
				.andExpect(redirectedUrl("/admin/greetings?state=PENDING"));
		mockMvc.perform(get(path(viewer)).param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Journey greeting")))
				.andExpect(content().string(not(containsString("Private journey note"))))
				.andExpect(content().string(not(containsString("+6281234567890"))));

		Rsvp approved = rsvps.findByGuestId(primary.getId()).orElseThrow();
		mockMvc.perform(post(primaryPath + "/rsvp").session(guestSession).with(csrf())
				.param("language", "EN")
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "2")
				.param("greeting", "Journey greeting").param("greetingPublicConsent", "true")
				.param("privateOrganizerNote", "Private journey note")
				.param("pin", "7890").param("version", Long.toString(approved.getVersion())))
				.andExpect(redirectedUrl(primaryPath + "?language=EN&rsvpSaved"));
		assertThat(qrSigner.verify(savedPayload)).isPresent();
		mockMvc.perform(get(primaryPath + "/qr.png").session(guestSession))
				.andExpect(status().isNotFound());

		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 06:00:00' where id = 1");
		Rsvp declined = rsvps.findByGuestId(primary.getId()).orElseThrow();
		mockMvc.perform(post("/admin/guests/{id}/rsvp", primary.getId())
				.session(adminSession).with(csrf()).param("response", "HADIR")
				.param("plannedAttendeeCount", "2").param("version", Long.toString(declined.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + primary.getId()));

		MockHttpSession freshGuestSession = new MockHttpSession();
		mockMvc.perform(get(primaryPath + "/qr.png").session(freshGuestSession).param("language", "EN"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl(primaryPath + "?language=EN&qrPinRequired"));
		mockMvc.perform(post(primaryPath + "/verify-qr").session(freshGuestSession).with(csrf())
				.param("language", "EN").param("pin", "7890"))
				.andExpect(redirectedUrl(primaryPath + "?language=EN&qrVerified"));
		mockMvc.perform(get(primaryPath + "/qr.png").session(freshGuestSession))
				.andExpect(status().isOk()).andExpect(content().contentType("image/png"));

		String csv = mockMvc.perform(get("/admin/guests/export.csv").session(adminSession))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
		assertThat(csv)
				.contains("rsvp_status,planned_attendee_count,greeting,greeting_public_consent,greeting_moderation_status,private_organizer_note,rsvp_updated_by,rsvp_updated_at")
				.contains("HADIR,2,Journey greeting,true,APPROVED,Private journey note,ADMIN,");
	}

	private void makeWeddingPublishable() {
		jdbc.update("""
				update partner set full_name = concat('Partner ', display_order),
				nickname = concat('P', display_order), photo_path = concat('partner-', display_order, '.jpg'),
				child_of_label_id = 'Putra/Putri', parents_names_id = 'Parents'
				""");
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, map_url)
				values ('CEREMONY', true, '2027-05-01', '08:00:00', 'Venue', 'Address', 'https://maps.example.test')
				""");
	}

	private void configureAndPublishWedding() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion()))
				.param("coupleTitle", "Phase 4 Couple")
				.param("openingTextId", "Dengan hormat").param("openingTextEn", "Welcome")
				.param("closingTextId", "Terima kasih").param("closingTextEn", "Thank you")
				.param("timeZone", "Asia/Jakarta").param("rsvpDeadline", "2026-08-01T08:00")
				.param("defaultPhoneCountry", "ID").param("accentColor", "#7A5C48")
				.param("fontPreset", "CLASSIC").param("greetingsEnabled", "true")
				.param("privateOrganizerNoteEnabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding/settings?settingsSaved"));
		mockMvc.perform(post("/admin/wedding/publish").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion())))
				.andExpect(redirectedUrl("/admin/wedding"));
		assertThat(settings.getSingleton().orElseThrow().getPublicationState())
				.isEqualTo(PublicationState.PUBLISHED);
	}

	private Guest createGuest(String name, String phone, boolean plusOne) throws Exception {
		mockMvc.perform(post("/admin/guests").session(adminSession).with(csrf())
				.param("displayName", name).param("salutation", "Mr/Ms")
				.param("phoneRegion", "ID").param("whatsappNumber", phone)
				.param("plusOneAllowed", Boolean.toString(plusOne)).param("preferredLanguage", "EN"))
				.andExpect(status().is3xxRedirection());
		return guests.findAll().stream().filter(guest -> name.equals(guest.getDisplayName())).findFirst().orElseThrow();
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession(false);
	}

	private String path(Guest guest) {
		return URI.create(invitationLinks.urlFor(guest)).getRawPath();
	}

	private String decode(byte[] png) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
				new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels))),
				Map.of(DecodeHintType.CHARACTER_SET, "UTF-8")).getText();
	}

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
