package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class AdminCheckInControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired CheckInService checkInService;
	@Autowired CheckInRepository checkIns;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;
	private int phoneSuffix;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
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
				    rsvp_deadline = '2030-08-01 00:00:00', greetings_enabled = true,
				    private_organizer_note_enabled = true
				where id = 1
				""");
		adminSession = login("admin");
		staffSession = login("staff");
		phoneSuffix = 8000;
	}

	@Test
	void onlyAdministratorWithCsrfCanUseCorrectionAndCancellationRoutes() throws Exception {
		Guest guest = attendingGuest("Secured correction", false, 1);
		var current = checkInService.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();

		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(staffSession))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("Correction history"))));
		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId())
				.session(staffSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "Blocked"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guests/{id}/check-in/cancel", guest.getId())
				.session(staffSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "Blocked"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId())
				.session(adminSession)
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "No CSRF"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guests/{id}/check-in/cancel", guest.getId())
				.session(adminSession)
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "No CSRF"))
				.andExpect(status().isForbidden());

		assertThat(checkInService.current(guest.getId())).contains(current);
	}

	@Test
	void adminDetailShowsVersionedNativeControlsAndEscapedAuditTimeline() throws Exception {
		Guest guest = attendingGuest("Audited guest", true, 2);
		var first = checkInService.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();
		var current = checkInService.correct(
				guest.getId(), first.version(), 2, "<script>audit reason</script>", "admin").checkIn();

		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Current check-in")))
				.andExpect(content().string(containsString("/check-in/correct")))
				.andExpect(content().string(containsString("/check-in/cancel")))
				.andExpect(content().string(containsString("name=\"checkInVersion\" value=\"" + current.version() + "\"")))
				.andExpect(content().string(containsString("name=\"actualCount\" value=\"1\"")))
				.andExpect(content().string(containsString("name=\"actualCount\" value=\"2\"")))
				.andExpect(content().string(containsString("name=\"reason\" required maxlength=\"500\"")))
				.andExpect(content().string(containsString("Correction history")))
				.andExpect(content().string(containsString("&lt;script&gt;audit reason&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>audit reason</script>"))))
				.andExpect(content().string(containsString("staff")));
	}

	@Test
	void correctionPostUsesPrgAndUpdatesCurrentActualCount() throws Exception {
		Guest guest = attendingGuest("Correct via form", true, 1);
		var current = checkInService.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();

		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "2").param("reason", "Companion arrived"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));

		assertThat(checkInService.current(guest.getId())).get()
				.extracting(CheckInService.CheckInView::actualAttendeeCount).isEqualTo(2);
	}

	@Test
	void cancellationPostShowsWarningWhenLaterRsvpEditPreventsRestoration() throws Exception {
		Guest guest = guest("Changed RSVP", false);
		var current = checkInService.confirmGuest(
				guest.getId(), guest.getVersion(), 1, true, "staff").checkIn();
		RsvpView promoted = rsvps.view(guest.getId()).orElseThrow();
		rsvps.correctByAdmin(guest.getId(), promoted.version(),
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null), "admin");

		mockMvc.perform(post("/admin/guests/{id}/check-in/cancel", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("reason", "Duplicate invitation"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()))
				.andExpect(flash().attribute("checkInWarning", containsString("RSVP")));

		assertThat(checkIns.findByGuestId(guest.getId())).isEmpty();
		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(content().string(containsString("RSVP changed after check-in")))
				.andExpect(content().string(containsString("CANCEL")))
				.andExpect(content().string(containsString("Duplicate invitation")));
	}

	@Test
	void invalidReasonDoesNotMutateCurrentCheckIn() throws Exception {
		Guest guest = attendingGuest("Invalid form", false, 1);
		var current = checkInService.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();

		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "  "))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()))
				.andExpect(flash().attributeExists("checkInError"));

		assertThat(checkInService.current(guest.getId())).contains(current);
		assertThat(checkInService.history(guest.getId())).isEmpty();
	}

	@Test
	void surroundingWhitespaceDoesNotCountTowardReasonLimit() throws Exception {
		Guest guest = attendingGuest("Maximum reason", false, 1);
		var current = checkInService.confirmGuest(
				guest.getId(), guest.getVersion(), 1, false, "staff").checkIn();
		String reason = "x".repeat(500);

		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "  " + reason + "  "))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));

		assertThat(checkInService.history(guest.getId())).singleElement()
				.extracting(CheckInService.CheckInCorrectionView::reason).isEqualTo(reason);
	}

	private Guest attendingGuest(String name, boolean plusOne, int plannedCount) {
		Guest guest = guest(name, plusOne);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, plannedCount, null, false, null));
		return guest;
	}

	private Guest guest(String name, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Guest", "ID", "+628123456" + phoneSuffix++,
				null, plusOne, MessageLanguage.ID, null), false);
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
