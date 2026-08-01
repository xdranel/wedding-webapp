package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import javax.imageio.ImageIO;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
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
@Import({MySqlTestConfiguration.class, PublicQrControllerTest.MutableClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PublicQrControllerTest {
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired GuestService guestService;
	@Autowired InvitationLinkSigner invitationLinks;
	@Autowired RsvpService rsvps;
	@Autowired GuestVerificationSession verification;
	@Autowired MutableClock clock;

	@BeforeEach
	void resetData() {
		clock.set(NOW);
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    time_zone = 'Asia/Jakarta', rsvp_deadline = '2026-08-01 08:00:00'
				where id = 1
				""");
	}

	@Test
	void verifiedAcceptedGuestGetsDisplayAndDownloadPngWithoutCaching() throws Exception {
		Guest guest = acceptedGuest();
		MockHttpSession session = verifiedSession(guest);

		MvcResult display = mockMvc.perform(get(path(guest) + "/qr.png").session(session))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn();
		assertThat(display.getResponse().getContentAsByteArray())
				.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
		assertThat(ImageIO.read(new ByteArrayInputStream(display.getResponse().getContentAsByteArray())).getWidth())
				.isEqualTo(320);

		MvcResult download = mockMvc.perform(get(path(guest) + "/qr-download.png").session(session))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"wedding-check-in-qr.png\""))
				.andReturn();
		assertThat(ImageIO.read(new ByteArrayInputStream(download.getResponse().getContentAsByteArray())).getWidth())
				.isEqualTo(1024);
	}

	@Test
	void missingAndExpiredVerificationRedirectToLocalizedPinPrompt() throws Exception {
		Guest guest = acceptedGuest();
		String path = path(guest);

		mockMvc.perform(get(path + "/qr.png").param("language", "EN"))
				.andExpect(status().isFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(redirectedUrl(path + "?language=EN&qrPinRequired"));

		MockHttpSession session = verifiedSession(guest);
		clock.set(NOW.plusSeconds(31 * 60));
		mockMvc.perform(get(path + "/qr.png").session(session))
				.andExpect(redirectedUrl(path + "?qrPinRequired"));
	}

	@Test
	void invitationRendersQrLinksOnlyForCurrentVerification() throws Exception {
		Guest guest = acceptedGuest();
		String path = path(guest);

		mockMvc.perform(get(path))
				.andExpect(content().string(not(containsString(path + "/qr.png"))));

		mockMvc.perform(get(path).session(verifiedSession(guest)).param("language", "EN"))
				.andExpect(content().string(containsString(path + "/qr.png?language=EN")))
				.andExpect(content().string(containsString(path + "/qr-download.png?language=EN")));
	}

	@Test
	void currentInvitationStateIsRecheckedOnEveryRequest() throws Exception {
		assertDeniedAfter(StateChange.DECLINE);
		assertDeniedAfter(StateChange.ARCHIVE);
		assertDeniedAfter(StateChange.REGENERATE_TOKEN);
		assertDeniedAfter(StateChange.DRAFT);
		assertDeniedAfter(StateChange.EVENT_CLOSED);
	}

	@Test
	void deadlineDoesNotHideQrForCurrentAcceptedGuest() throws Exception {
		Guest guest = acceptedGuest();
		MockHttpSession session = verifiedSession(guest);
		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 06:00:00' where id = 1");

		mockMvc.perform(get(path(guest) + "/qr.png").session(session))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"));
	}

	private void assertDeniedAfter(StateChange change) throws Exception {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED', event_closed = false where id = 1");
		Guest guest = acceptedGuest();
		String path = path(guest);
		MockHttpSession session = verifiedSession(guest);

		switch (change) {
			case DECLINE -> {
				RsvpView current = rsvps.view(guest.getId()).orElseThrow();
				rsvps.submitGuest(guest.getId(), current.version(),
						new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null));
			}
			case ARCHIVE -> guestService.archive(guest.getId(), guest.getVersion());
			case REGENERATE_TOKEN -> guestService.regenerateInvitation(guest.getId(), guest.getVersion(), NOW);
			case DRAFT -> jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");
			case EVENT_CLOSED -> jdbc.update("update wedding_settings set event_closed = true where id = 1");
		}

		mockMvc.perform(get(path + "/qr.png").session(session))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"));
	}

	private Guest acceptedGuest() {
		Guest guest = guestService.create(new GuestForm("QR guest", "Bapak/Ibu", "ID", "+62 812 3456 7890",
				null, false, MessageLanguage.ID, null), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));
		return guest;
	}

	private MockHttpSession verifiedSession(Guest guest) {
		MockHttpSession session = new MockHttpSession();
		verification.grant(session, guest.getPublicId(), guest.getInvitationTokenVersion(),
				guest.getNormalizedWhatsappNumber());
		return session;
	}

	private String path(Guest guest) {
		return URI.create(invitationLinks.urlFor(guest)).getRawPath();
	}

	private enum StateChange {
		DECLINE,
		ARCHIVE,
		REGENERATE_TOKEN,
		DRAFT,
		EVENT_CLOSED
	}

	static final class MutableClock extends Clock {
		private Instant instant = NOW;

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MutableClockConfig {
		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}
	}
}
