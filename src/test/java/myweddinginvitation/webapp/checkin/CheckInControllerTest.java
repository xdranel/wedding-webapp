package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class CheckInControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired CheckInRepository checkIns;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

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
				    rsvp_deadline = '2030-08-01 08:00:00'
				where id = 1
				""");
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void anonymousRequestsRedirectToLogin() throws Exception {
		mockMvc.perform(get("/check-in")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/check-in/search").param("q", "Ada")).andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/check-in/preview/guest/1")).andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/check-in/preview/qr").with(csrf()).param("payload", "x"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/check-in/confirm/qr").with(csrf()).param("payload", "x"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/check-in/confirm/guest/1").with(csrf()).param("guestVersion", "0"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void administratorsAndStaffCanUseEveryCheckInRoute() throws Exception {
		for (int i = 0; i < 2; i++) {
			MockHttpSession session = i == 0 ? adminSession : staffSession;
			Guest guest = attendingGuest("Route guest " + i, false, "+6281111222" + i);
			String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());
			mockMvc.perform(get("/check-in").session(session)).andExpect(status().isOk());
			mockMvc.perform(get("/check-in/search").session(session).param("q", "Route"))
					.andExpect(status().isOk());
			mockMvc.perform(get("/check-in/preview/guest/{id}", guest.getId()).session(session))
					.andExpect(status().isOk());
			mockMvc.perform(post("/check-in/preview/qr").session(session).with(csrf()).param("payload", payload))
					.andExpect(status().isOk());
			mockMvc.perform(post("/check-in/confirm/qr").session(session).with(csrf())
					.param("payload", payload).param("actualCount", "1"))
					.andExpect(redirectedUrl("/check-in/result"));
			Guest manual = guest("Manual route " + i, "+6281111333" + i, null);
			mockMvc.perform(post("/check-in/confirm/guest/{id}", manual.getId()).session(session).with(csrf())
					.param("guestVersion", Long.toString(manual.getVersion())).param("actualCount", "1")
					.param("acceptRsvpChange", "true"))
					.andExpect(redirectedUrl("/check-in/result"));
		}
	}

	@Test
	void checkInPostsRequireCsrf() throws Exception {
		Guest guest = attendingGuest("CSRF guest", false, "+62811114444");
		String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());

		mockMvc.perform(post("/check-in/preview/qr").session(staffSession).param("payload", payload))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/check-in/confirm/qr").session(staffSession).param("payload", payload))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/check-in/confirm/guest/{id}", guest.getId()).session(staffSession)
				.param("guestVersion", Long.toString(guest.getVersion())))
				.andExpect(status().isForbidden());
	}

	@Test
	void previewsNeverWriteAndConfirmationUsesPrg() throws Exception {
		Guest guest = attendingGuest("Preview guest", false, "+62811115555");
		String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());

		mockMvc.perform(get("/check-in/preview/guest/{id}", guest.getId()).session(staffSession))
				.andExpect(status().isOk()).andExpect(view().name("checkin/preview"));
		mockMvc.perform(post("/check-in/preview/qr").session(staffSession).with(csrf()).param("payload", payload))
				.andExpect(status().isOk()).andExpect(view().name("checkin/preview"));
		assertThat(checkIns.count()).isZero();

		mockMvc.perform(post("/check-in/confirm/guest/{id}", guest.getId()).session(staffSession).with(csrf())
				.param("guestVersion", Long.toString(guest.getVersion())).param("actualCount", "1"))
				.andExpect(redirectedUrl("/check-in/result"));
		mockMvc.perform(get("/check-in/result").session(staffSession))
				.andExpect(status().isOk()).andExpect(view().name("checkin/result"));
		mockMvc.perform(get("/check-in/result").session(staffSession));
		assertThat(checkIns.count()).isEqualTo(1);
	}

	@Test
	void staffPagesNeverRenderPrivateGuestData() throws Exception {
		Guest guest = guest("Private guest", "+62811116666", "internal-note-secret");
		rsvps.submitGuest(guest.getId(), -1, new RsvpSubmission(AttendanceResponse.HADIR, 1,
				"greeting-secret", false, "private-note-secret"));
		String payload = qrSigner.payload(guest.getPublicId(), guest.getInvitationTokenVersion());

		for (var request : java.util.List.of(
				get("/check-in").session(staffSession),
				get("/check-in/search").session(staffSession).param("q", "Private"),
				get("/check-in/preview/guest/{id}", guest.getId()).session(staffSession),
				post("/check-in/preview/qr").session(staffSession).with(csrf()).param("payload", payload))) {
			mockMvc.perform(request).andExpect(content().string(not(containsString("+62811116666"))))
					.andExpect(content().string(not(containsString("internal-note-secret"))))
					.andExpect(content().string(not(containsString("greeting-secret"))))
					.andExpect(content().string(not(containsString("private-note-secret"))))
					.andExpect(content().string(not(containsString("correction-reason-secret"))));
		}
		mockMvc.perform(post("/check-in/confirm/qr").session(staffSession).with(csrf())
				.param("payload", payload).param("actualCount", "1"))
				.andExpect(redirectedUrl("/check-in/result"));
		mockMvc.perform(get("/check-in/result").session(staffSession))
				.andExpect(content().string(not(containsString("+62811116666"))))
				.andExpect(content().string(not(containsString("internal-note-secret"))))
				.andExpect(content().string(not(containsString("greeting-secret"))))
				.andExpect(content().string(not(containsString("private-note-secret"))))
				.andExpect(content().string(not(containsString("correction-reason-secret"))));
	}

	private Guest attendingGuest(String name, boolean plusOne, String number) {
		Guest guest = guest(name, number, null, plusOne);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, plusOne ? 2 : 1, null, false, null));
		return guest;
	}

	private Guest guest(String name, String number, String note) {
		return guest(name, number, note, false);
	}

	private Guest guest(String name, String number, String note, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Guest", "ID", number, null, plusOne,
				MessageLanguage.EN, note), false);
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
