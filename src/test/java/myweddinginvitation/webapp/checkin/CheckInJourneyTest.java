package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
import myweddinginvitation.webapp.rsvp.CheckInQrSigner;
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
class CheckInJourneyTest {
	private static final String ADMIN_PASSWORD = "Admin-Password-2026";
	private static final String TEMPORARY_PASSWORD = "Temporary-Password-2026";
	private static final String STAFF_PASSWORD = "Staff-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired CheckInService checkInService;
	@Autowired CheckInRepository checkIns;
	@Autowired CheckInCorrectionRepository corrections;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + ADMIN_PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", ADMIN_PASSWORD, ADMIN_PASSWORD);
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    rsvp_deadline = '2030-08-01 08:00:00'
				where id = 1
				""");
		adminSession = login("admin", ADMIN_PASSWORD, "/admin");
	}

	@Test
	void staffAndAdministratorCompleteQrCorrectionManualPromotionAndCancellationJourney() throws Exception {
		MockHttpSession staffSession = createStaffAndCompleteFirstPasswordChange();
		Guest guest = guestService.create(new GuestForm("Journey Guest", "Guest", "ID", "+6281234567890",
				null, true, MessageLanguage.EN, "admin-only note"), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, "private greeting", false, "private RSVP note"));
		String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());

		mockMvc.perform(post("/check-in/preview/qr").session(staffSession).with(csrf()).param("payload", payload))
				.andExpect(status().isOk())
				.andExpect(view().name("checkin/preview"))
				.andExpect(content().string(containsString("Journey Guest")))
				.andExpect(content().string(containsString("HADIR")));
		mockMvc.perform(post("/check-in/confirm/qr").session(staffSession).with(csrf())
				.param("payload", payload).param("actualCount", "2"))
				.andExpect(redirectedUrl("/check-in/result"));
		mockMvc.perform(get("/check-in/result").session(staffSession))
				.andExpect(content().string(containsString("Check-in complete")))
				.andExpect(content().string(containsString("door-staff")))
				.andExpect(content().string(containsString("count <span>2</span>")));

		mockMvc.perform(post("/check-in/confirm/qr").session(staffSession).with(csrf())
				.param("payload", payload).param("actualCount", "1"))
				.andExpect(redirectedUrl("/check-in/result"));
		mockMvc.perform(get("/check-in/result").session(staffSession))
				.andExpect(content().string(containsString("Already checked in")))
				.andExpect(content().string(containsString("count <span>2</span>")));

		mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(model().attribute("checkInSummary", is(new CheckInService.CheckInSummary(1, 2))));
		mockMvc.perform(get("/admin/guests").session(adminSession).param("checkedIn", "true"))
				.andExpect(content().string(containsString("Journey Guest")))
				.andExpect(content().string(containsString("<td data-label=\"Actual attendees\">2</td>")));
		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(content().string(containsString("Current check-in")))
				.andExpect(content().string(containsString("door-staff")))
				.andExpect(content().string(containsString("<dd>2</dd>")));

		var current = checkInService.current(guest.getId()).orElseThrow();
		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "Companion did not arrive"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
		current = checkInService.current(guest.getId()).orElseThrow();
		assertThat(current.actualAttendeeCount()).isEqualTo(1);
		mockMvc.perform(post("/admin/guests/{id}/check-in/cancel", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "Wrong invitation"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
		assertThat(checkIns.findByGuestId(guest.getId())).isEmpty();

		RsvpView attending = rsvps.view(guest.getId()).orElseThrow();
		updateRsvp(guest, attending, AttendanceResponse.TIDAK_HADIR, 0);
		mockMvc.perform(get("/check-in/search").session(staffSession).param("q", "Journey"))
				.andExpect(content().string(containsString("Journey Guest")));
		mockMvc.perform(get("/check-in/preview/guest/{id}", guest.getId()).session(staffSession))
				.andExpect(content().string(containsString("TIDAK_HADIR")))
				.andExpect(content().string(containsString("will be changed to attending")));
		mockMvc.perform(post("/check-in/confirm/guest/{id}", guest.getId()).session(staffSession).with(csrf())
				.param("guestVersion", Long.toString(guests.findById(guest.getId()).orElseThrow().getVersion()))
				.param("actualCount", "1").param("acceptRsvpChange", "true"))
				.andExpect(redirectedUrl("/check-in/result"));
		assertThat(rsvps.view(guest.getId())).get()
				.extracting(RsvpView::response, RsvpView::plannedAttendeeCount)
				.containsExactly(AttendanceResponse.HADIR, 1);

		RsvpView promoted = rsvps.view(guest.getId()).orElseThrow();
		updateRsvp(guest, promoted, AttendanceResponse.HADIR, 2);
		current = checkInService.current(guest.getId()).orElseThrow();
		mockMvc.perform(post("/admin/guests/{id}/check-in/cancel", guest.getId())
				.session(adminSession).with(csrf())
				.param("checkInVersion", Long.toString(current.version()))
				.param("actualCount", "1").param("reason", "Later RSVP wins"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()))
				.andExpect(flash().attribute("checkInWarning", containsString("RSVP changed after check-in")));

		assertThat(checkIns.findByGuestId(guest.getId())).isEmpty();
		assertThat(rsvps.view(guest.getId())).get()
				.extracting(RsvpView::response, RsvpView::plannedAttendeeCount)
				.containsExactly(AttendanceResponse.HADIR, 2);
		assertThat(corrections.findByGuestIdOrderByCorrectedAtDescIdDesc(guest.getId()))
				.extracting(CheckInCorrection::getAction)
				.containsExactly(CheckInCorrectionAction.CANCEL, CheckInCorrectionAction.CANCEL,
						CheckInCorrectionAction.CORRECT);
	}

	private MockHttpSession createStaffAndCompleteFirstPasswordChange() throws Exception {
		mockMvc.perform(post("/admin/accounts").session(adminSession).with(csrf())
				.param("username", "door-staff").param("temporaryPassword", TEMPORARY_PASSWORD))
				.andExpect(redirectedUrl("/admin/accounts"));
		MockHttpSession firstSession = login("door-staff", TEMPORARY_PASSWORD, "/account/password");
		mockMvc.perform(get("/check-in").session(firstSession))
				.andExpect(redirectedUrl("/account/password"));
		mockMvc.perform(post("/account/password").session(firstSession).with(csrf())
				.param("currentPassword", TEMPORARY_PASSWORD)
				.param("newPassword", STAFF_PASSWORD).param("confirmPassword", STAFF_PASSWORD))
				.andExpect(redirectedUrl("/login?passwordChanged"));
		return login("door-staff", STAFF_PASSWORD, "/check-in");
	}

	private void updateRsvp(Guest guest, RsvpView current, AttendanceResponse response, int count) throws Exception {
		mockMvc.perform(post("/admin/guests/{id}/rsvp", guest.getId()).session(adminSession).with(csrf())
				.param("response", response.name()).param("plannedAttendeeCount", Integer.toString(count))
				.param("version", Long.toString(current.version())))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
	}

	private MockHttpSession login(String username, String password, String destination) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", password))
				.andExpect(redirectedUrl(destination))
				.andReturn().getRequest().getSession(false);
	}
}
