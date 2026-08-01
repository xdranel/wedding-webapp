package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
@Import({MySqlTestConfiguration.class, PublicRsvpControllerTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PublicRsvpControllerTest {
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired InvitationLinkSigner signer;
	@Autowired RsvpService rsvps;
	@Autowired GuestVerificationSession verification;

	private int phoneSuffix;

	@BeforeEach
	void resetData() {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    time_zone = 'Asia/Jakarta', rsvp_deadline = '2026-08-01 08:00:00',
				    greetings_enabled = true, private_organizer_note_enabled = true
				where id = 1
				""");
		phoneSuffix = 7800;
	}

	@Test
	void validPinSavesRsvpAndGrantsInvitationSession() throws Exception {
		Guest guest = guest("Sari", true, MessageLanguage.ID, "+62 812 3456 7890");
		String path = path(guest);

		MvcResult result = mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("response", "HADIR")
				.param("plannedAttendeeCount", "2")
				.param("greeting", "Selamat")
				.param("greetingPublicConsent", "true")
				.param("privateOrganizerNote", "Vegetarian")
				.param("pin", "7890")
				.param("version", "-1"))
				.andExpect(redirectedUrl(path + "?rsvpSaved"))
				.andReturn();

		RsvpView saved = rsvps.view(guest.getId()).orElseThrow();
		assertThat(saved.response()).isEqualTo(AttendanceResponse.HADIR);
		assertThat(saved.plannedAttendeeCount()).isEqualTo(2);
		assertThat(saved.greeting()).isEqualTo("Selamat");
		assertThat(saved.privateOrganizerNote()).isEqualTo("Vegetarian");
		assertThat(verification.verified(result.getRequest().getSession(), guest.getPublicId(),
				guest.getInvitationTokenVersion(), guest.getNormalizedWhatsappNumber())).isTrue();
	}

	@Test
	void declineStoresZeroAndPreservesExplicitEnglishSelection() throws Exception {
		Guest guest = guest("Declines", true, MessageLanguage.ID, "+62 812 3456 7890");

		mockMvc.perform(post(path(guest) + "/rsvp").with(csrf())
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "2")
				.param("pin", "7890").param("version", "-1").param("language", "EN"))
				.andExpect(redirectedUrl(path(guest) + "?language=EN&rsvpSaved"));

		assertThat(rsvps.view(guest.getId()).orElseThrow().plannedAttendeeCount()).isZero();
	}

	@Test
	void invalidFormDoesNotCountPinAndRedisplaysSafeInput() throws Exception {
		Guest guest = guest("Tanpa pendamping", false, MessageLanguage.ID, "+62 812 3456 7890");

		mockMvc.perform(post(path(guest) + "/rsvp").with(csrf())
				.param("response", "HADIR")
				.param("plannedAttendeeCount", "2")
				.param("greeting", "Tetap tampil")
				.param("pin", "0000")
				.param("version", "-1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Undangan ini tidak mencakup pendamping")))
				.andExpect(content().string(containsString("Tetap tampil")))
				.andExpect(content().string(not(containsString("value=\"0000\""))));

		assertThat(guests.findById(guest.getId()).orElseThrow().getFailedPinCount()).isZero();
		assertThat(rsvps.view(guest.getId())).isEmpty();
	}

	@Test
	void malformedAndWrongPinsAreHandledWithoutDisclosingPhoneDigits() throws Exception {
		Guest guest = guest("Ada", false, MessageLanguage.EN, "+62 812 3456 7890");
		String path = path(guest);

		mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "0")
				.param("pin", "12x").param("version", "-1").param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Enter a four-digit PIN")));
		assertThat(guests.findById(guest.getId()).orElseThrow().getFailedPinCount()).isZero();

		mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "0")
				.param("greeting", "Keep this safely").param("pin", "0000")
				.param("version", "-1").param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PIN does not match")))
				.andExpect(content().string(containsString("Keep this safely")))
				.andExpect(content().string(not(containsString("7890"))));
		assertThat(guests.findById(guest.getId()).orElseThrow().getFailedPinCount()).isEqualTo(1);
	}

	@Test
	void fifthWrongPinShowsLockWithoutAttemptsRemaining() throws Exception {
		Guest guest = guest("Terkunci", false, MessageLanguage.ID, "+62 812 3456 7890");
		for (int attempt = 1; attempt < 5; attempt++) {
			mockMvc.perform(validDecline(path(guest), "0000"))
					.andExpect(content().string(containsString("PIN tidak cocok")))
					.andExpect(content().string(not(containsString("percobaan"))));
		}

		mockMvc.perform(validDecline(path(guest), "0000"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Coba lagi setelah")));
	}

	@Test
	void deadlineAndClosedEventAreEnforcedAtRequestTime() throws Exception {
		Guest guest = guest("Batas", false, MessageLanguage.ID, "+62 812 3456 7890");

		jdbc.update("update wedding_settings set rsvp_deadline = null where id = 1");
		mockMvc.perform(get(path(guest)))
				.andExpect(content().string(containsString("RSVP belum dibuka")));

		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 07:00:00' where id = 1");
		mockMvc.perform(validDecline(path(guest), "7890"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Batas waktu RSVP telah lewat")));
		assertThat(rsvps.view(guest.getId())).isEmpty();

		jdbc.update("update wedding_settings set event_closed = true where id = 1");
		mockMvc.perform(get(path(guest)))
				.andExpect(status().isOk())
				.andExpect(view().name("guest/closed"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(containsString("Acara telah selesai")))
				.andExpect(content().string(not(containsString("Batas"))));
	}

	@Test
	void archivedAndRegeneratedPostLinksUseSameNeutralUnavailablePage() throws Exception {
		Guest guest = guest("Rahasia", false, MessageLanguage.ID, "+62 812 3456 7890");
		String oldPath = path(guest);
		guestService.regenerateInvitation(guest.getId(), guest.getVersion(), NOW);

		mockMvc.perform(validDecline(oldPath, "7890"))
				.andExpect(status().isNotFound())
				.andExpect(view().name("guest/unavailable"))
				.andExpect(content().string(not(containsString("Rahasia"))));
	}

	@Test
	void staleVersionRedisplayUsesCurrentVersionAndImmediateResubmissionSucceeds() throws Exception {
		Guest guest = guest("Konflik", false, MessageLanguage.ID, "+62 812 3456 7890");
		RsvpView current = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, "Saat ini", true, null));
		String path = path(guest);

		mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "0")
				.param("greeting", "Tetap dikirim").param("greetingPublicConsent", "true")
				.param("privateOrganizerNote", "Tetap privat")
				.param("pin", "7890").param("version", Long.toString(current.version() - 1)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("RSVP telah berubah")))
				.andExpect(model().attribute("rsvp", current))
				.andExpect(content().string(containsString(
						"id=\"version\" name=\"version\" value=\"" + current.version() + "\"")))
				.andExpect(content().string(containsString("Tetap dikirim")))
				.andExpect(content().string(containsString("Tetap privat")))
				.andExpect(content().string(not(containsString("value=\"7890\""))));

		mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "0")
				.param("greeting", "Tetap dikirim").param("greetingPublicConsent", "true")
				.param("privateOrganizerNote", "Tetap privat")
				.param("pin", "7890").param("version", Long.toString(current.version())))
				.andExpect(redirectedUrl(path + "?rsvpSaved"));

		RsvpView updated = rsvps.view(guest.getId()).orElseThrow();
		assertThat(updated.response()).isEqualTo(AttendanceResponse.TIDAK_HADIR);
		assertThat(updated.greeting()).isEqualTo("Tetap dikirim");
		assertThat(updated.privateOrganizerNote()).isEqualTo("Tetap privat");
	}

	@Test
	void structuralValidationUsesOnlyIndonesianGuestMessages() throws Exception {
		assertLocalizedValidation("ID", "Pilih Hadir atau Tidak hadir.",
				"Jumlah hadir harus satu atau dua.", "Ucapan maksimal 500 karakter.",
				"Catatan privat maksimal 1000 karakter.", "Masukkan PIN empat digit.");
	}

	@Test
	void structuralValidationUsesOnlyEnglishGuestMessages() throws Exception {
		assertLocalizedValidation("EN", "Choose attending or not attending.",
				"Attendance must be one or two.", "Greeting must be at most 500 characters.",
				"Private note must be at most 1000 characters.", "Enter a four-digit PIN.");
	}

	@Test
	void qrVerificationWorksAfterDeadlineWithoutRewritingAcceptedRsvp() throws Exception {
		Guest guest = guest("QR", false, MessageLanguage.ID, "+62 812 3456 7890");
		RsvpView before = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));
		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 07:00:00' where id = 1");

		MvcResult result = mockMvc.perform(post(path(guest) + "/verify-qr").with(csrf()).param("pin", "7890"))
				.andExpect(redirectedUrl(path(guest) + "?qrVerified"))
				.andReturn();

		assertThat(rsvps.view(guest.getId()).orElseThrow()).isEqualTo(before);
		assertThat(verification.verified(result.getRequest().getSession(), guest.getPublicId(),
				guest.getInvitationTokenVersion(), guest.getNormalizedWhatsappNumber())).isTrue();
	}

	@Test
	void qrVerificationRequiresAcceptedRsvpAndOpenEvent() throws Exception {
		Guest guest = guest("Tanpa QR", false, MessageLanguage.EN, "+62 812 3456 7890");
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null));

		mockMvc.perform(post(path(guest) + "/verify-qr").with(csrf()).param("pin", "0000").param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("QR is available only for an accepted RSVP")));
		assertThat(guests.findById(guest.getId()).orElseThrow().getFailedPinCount()).isZero();

		jdbc.update("update wedding_settings set event_closed = true where id = 1");
		mockMvc.perform(post(path(guest) + "/verify-qr").with(csrf()).param("pin", "7890"))
				.andExpect(view().name("guest/closed"));
	}

	@Test
	void wrongQrPinCountsWithoutRewritingAcceptedRsvp() throws Exception {
		Guest guest = guest("Wrong QR PIN", false, MessageLanguage.ID, "+62 812 3456 7890");
		RsvpView before = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));

		mockMvc.perform(post(path(guest) + "/verify-qr").with(csrf()).param("pin", "0000"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("PIN tidak cocok")));

		assertThat(guests.findById(guest.getId()).orElseThrow().getFailedPinCount()).isEqualTo(1);
		assertThat(rsvps.view(guest.getId()).orElseThrow()).isEqualTo(before);
	}

	@Test
	void approvedGreetingFeedIsPagedNewestFirstAndPreservesSignedPathAndLanguage() throws Exception {
		Guest viewer = guest("Viewer", false, MessageLanguage.ID, "+62 812 3456 7890");
		for (int index = 1; index <= 21; index++) addGreeting(index, "APPROVED", true, "Ucapan " + index);
		addGreeting(22, "PENDING", true, "Jangan pending");
		addGreeting(23, "APPROVED", false, "Jangan tanpa consent");
		addGreeting(24, "HIDDEN", true, "Jangan hidden");
		jdbc.update("update rsvp set private_organizer_note = 'feed-private-probe' where greeting = 'Ucapan 21'");

		mockMvc.perform(get(path(viewer)).param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("greetings", hasSize(20)))
				.andExpect(content().string(containsString("Ucapan 21")))
				.andExpect(content().string(not(containsString(">Ucapan 1<"))))
				.andExpect(content().string(not(containsString("Jangan pending"))))
				.andExpect(content().string(not(containsString("feed-private-probe"))))
				.andExpect(content().string(containsString(path(viewer) + "?language=EN&amp;page=1")))
				.andExpect(content().string(containsString("Load more")));

		mockMvc.perform(get(path(viewer)).param("language", "EN").param("page", "1"))
				.andExpect(model().attribute("greetings", hasSize(1)))
				.andExpect(content().string(containsString("Ucapan 1")));
	}

	@Test
	void csrfStillProtectsPublicRsvpWrites() throws Exception {
		Guest guest = guest("CSRF", false, MessageLanguage.ID, "+62 812 3456 7890");
		mockMvc.perform(post(path(guest) + "/rsvp")
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "0")
				.param("pin", "7890").param("version", "-1"))
				.andExpect(status().isForbidden());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validDecline(String path, String pin) {
		return post(path + "/rsvp").with(csrf())
				.param("response", "TIDAK_HADIR").param("plannedAttendeeCount", "0")
				.param("pin", pin).param("version", "-1");
	}

	private Guest guest(String name, boolean plusOne, MessageLanguage language, String number) {
		return guestService.create(new GuestForm(name, "Bapak/Ibu", "ID", number, null, plusOne, language, null), false);
	}

	private void assertLocalizedValidation(String language, String responseError, String countError,
			String greetingError, String noteError, String pinError) throws Exception {
		Guest guest = guest("Validation " + language, true, MessageLanguage.ID, "+62 812 3456 7890");
		String path = path(guest);

		mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("response", "INVALID").param("plannedAttendeeCount", "many")
				.param("pin", "12x").param("version", "-1").param("language", language))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(responseError)))
				.andExpect(content().string(containsString(countError)))
				.andExpect(content().string(containsString(pinError)))
				.andExpect(content().string(not(containsString("Failed to convert value of type"))))
				.andExpect(content().string(not(containsString("must match"))))
				.andExpect(content().string(not(containsString("must not be blank"))));

		mockMvc.perform(post(path + "/rsvp").with(csrf())
				.param("plannedAttendeeCount", "3")
				.param("greeting", "g".repeat(501))
				.param("privateOrganizerNote", "n".repeat(1001))
				.param("pin", "12x").param("version", "-1").param("language", language))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(responseError)))
				.andExpect(content().string(containsString(countError)))
				.andExpect(content().string(containsString(greetingError)))
				.andExpect(content().string(containsString(noteError)))
				.andExpect(content().string(containsString(pinError)))
				.andExpect(content().string(not(containsString("must not be null"))))
				.andExpect(content().string(not(containsString("must be less than or equal to"))))
				.andExpect(content().string(not(containsString("size must be between"))))
				.andExpect(content().string(not(containsString("must match"))));

		assertThat(guests.findById(guest.getId()).orElseThrow().getFailedPinCount()).isZero();
		assertThat(rsvps.view(guest.getId())).isEmpty();
	}

	private void addGreeting(int index, String state, boolean consent, String greeting) {
		phoneSuffix++;
		Guest guest = guest("Tamu " + index, false, MessageLanguage.ID, "08120000" + phoneSuffix);
		Instant at = NOW.plusSeconds(index);
		jdbc.update("""
				insert into rsvp (guest_id, response, planned_attendee_count, greeting,
				greeting_public_consent, greeting_moderation_state, update_source,
				version, created_at, updated_at)
				values (?, 'HADIR', 1, ?, ?, ?, 'GUEST', 0, ?, ?)
				""", guest.getId(), greeting, consent, state, Timestamp.from(at), Timestamp.from(at));
	}

	private String path(Guest guest) {
		return URI.create(signer.urlFor(guest)).getRawPath();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
