package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
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

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, AdminRsvpControllerTest.FixedClockConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AdminRsvpControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    time_zone = 'Asia/Jakarta', rsvp_deadline = '2026-08-01 08:00:00',
				    greetings_enabled = true, private_organizer_note_enabled = true
				where id = 1
				""");
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void administratorCanOpenCurrentRsvp() throws Exception {
		Guest guest = guest("Ada", true);
		RsvpView rsvp = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));

		mockMvc.perform(get("/admin/guests/{id}/rsvp", guest.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/rsvp"))
				.andExpect(model().attribute("form",
						is(new AdminRsvpForm(AttendanceResponse.HADIR, 2, rsvp.version()))))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Guest")));
	}

	@Test
	void administratorCanCorrectAfterDeadlineWithoutChangingGuestWrittenText() throws Exception {
		Guest guest = guest("Bela", true);
		RsvpView rsvp = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, "Guest greeting", true, "Private note"));
		jdbc.update("update wedding_settings set rsvp_deadline = '2026-08-01 07:00:00' where id = 1");

		mockMvc.perform(post("/admin/guests/{id}/rsvp", guest.getId()).session(adminSession).with(csrf())
				.param("response", "TIDAK_HADIR")
				.param("plannedAttendeeCount", "2")
				.param("version", Long.toString(rsvp.version()))
				.param("greeting", "Administrator replacement")
				.param("privateOrganizerNote", "Administrator replacement"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));

		RsvpView corrected = rsvps.view(guest.getId()).orElseThrow();
		assertThat(corrected.response()).isEqualTo(AttendanceResponse.TIDAK_HADIR);
		assertThat(corrected.plannedAttendeeCount()).isZero();
		assertThat(corrected.greeting()).isEqualTo("Guest greeting");
		assertThat(corrected.privateOrganizerNote()).isEqualTo("Private note");
		assertThat(corrected.updateSource()).isEqualTo(RsvpUpdateSource.ADMIN);
		assertThat(corrected.updatedAt()).isEqualTo(NOW);
		assertThat(jdbc.queryForObject("""
				select a.username from rsvp r join user_account a on a.id = r.updated_by_account_id
				where r.id = ?
				""", String.class, corrected.id())).isEqualTo("admin");
	}

	@Test
	void invalidCorrectionAssociatesEachRenderedFieldError() throws Exception {
		Guest guest = guest("Batas", true);

		mockMvc.perform(post("/admin/guests/{id}/rsvp", guest.getId()).session(adminSession).with(csrf())
				.param("plannedAttendeeCount", "3")
				.param("version", "-1"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "response", "plannedAttendeeCount"))
				.andExpect(content().string(containsString("aria-describedby=\"response-error\"")))
				.andExpect(content().string(containsString("id=\"response-error\"")))
				.andExpect(content().string(containsString("aria-describedby=\"planned-attendee-count-error\"")))
				.andExpect(content().string(containsString("id=\"planned-attendee-count-error\"")));
	}

	@Test
	void staleCorrectionShowsCurrentStateAndRequiresResubmission() throws Exception {
		Guest guest = guest("Cici", false);
		RsvpView current = rsvps.correctByAdmin(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null), "admin");

		mockMvc.perform(post("/admin/guests/{id}/rsvp", guest.getId()).session(adminSession).with(csrf())
				.param("response", "TIDAK_HADIR")
				.param("plannedAttendeeCount", "0")
				.param("version", "-1"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/rsvp"))
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(model().attribute("form",
						is(new AdminRsvpForm(AttendanceResponse.HADIR, 1, current.version()))));

		assertThat(rsvps.view(guest.getId())).get()
				.extracting(RsvpView::response).isEqualTo(AttendanceResponse.HADIR);
	}

	@Test
	void onlyAdministratorWithCsrfCanCorrectRsvp() throws Exception {
		Guest guest = guest("Dedi", false);

		mockMvc.perform(post("/admin/guests/{id}/rsvp", guest.getId()).session(staffSession).with(csrf())
				.param("response", "HADIR").param("plannedAttendeeCount", "1").param("version", "-1"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guests/{id}/rsvp", guest.getId()).session(adminSession)
				.param("response", "HADIR").param("plannedAttendeeCount", "1").param("version", "-1"))
				.andExpect(status().isForbidden());
		assertThat(rsvps.view(guest.getId())).isEmpty();
	}

	@Test
	void administratorCanClearOnlyPinSecurityState() throws Exception {
		Guest guest = guest("Eka", false);
		for (int attempt = 0; attempt < 5; attempt++) guest.pinFailed(NOW);
		guest = guests.saveAndFlush(guest);
		long version = guest.getVersion();

		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Clear PIN lock")));
		mockMvc.perform(post("/admin/guests/{id}/clear-pin-lock", guest.getId())
				.session(adminSession).with(csrf()))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));

		Guest cleared = guests.findById(guest.getId()).orElseThrow();
		assertThat(cleared.getFailedPinCount()).isZero();
		assertThat(cleared.getPinLockedUntil()).isNull();
		assertThat(cleared.getInvitationTokenVersion()).isEqualTo(guest.getInvitationTokenVersion());
		assertThat(cleared.getVersion()).isGreaterThan(version);
	}

	@Test
	void expiredPinLockIsNotOfferedForClearing() throws Exception {
		Guest guest = guest("Fani", false);
		for (int attempt = 0; attempt < 5; attempt++) guest.pinFailed(NOW.minusSeconds(901));
		guest = guests.saveAndFlush(guest);

		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("Clear PIN lock"))));
	}

	private Guest guest(String name, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Bapak/Ibu", "ID", "+628123456%04d".formatted(guests.count()),
				null, plusOne, MessageLanguage.ID, null), false);
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
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
