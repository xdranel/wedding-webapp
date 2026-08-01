package myweddinginvitation.webapp.rsvp;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
class AdminRsvpSummaryTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpRepository rsvps;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		adminSession = login();
	}

	@Test
	void dashboardShowsActiveOnlyRsvpAndPeopleSummary() throws Exception {
		Guest attending = guest("Attending", true);
		Guest declined = guest("Declined", false);
		guest("No RSVP", false);
		Guest archived = guest("Archived", false);
		rsvps.save(Rsvp.create(attending, AttendanceResponse.HADIR, 2, "Pending", true,
				GreetingModerationState.PENDING, null, RsvpUpdateSource.GUEST, null, NOW));
		rsvps.save(Rsvp.create(declined, AttendanceResponse.TIDAK_HADIR, 0, null, false,
				GreetingModerationState.HIDDEN, null, RsvpUpdateSource.GUEST, null, NOW));
		rsvps.saveAndFlush(Rsvp.create(archived, AttendanceResponse.HADIR, 1, "Archived pending", true,
				GreetingModerationState.PENDING, null, RsvpUpdateSource.GUEST, null, NOW));
		guestService.archive(archived.getId(), archived.getVersion());

		mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(model().attribute("rsvpSummary", new RsvpSummary(1, 1, 1, 2, 1)))
				.andExpect(content().string(containsString("Accepted invitations")))
				.andExpect(content().string(containsString("Planned people")))
				.andExpect(content().string(containsString("/admin/greetings")));
	}

	private Guest guest(String name, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Guest", "ID",
				"+62812345%04d".formatted(guests.count() + 1), null, plusOne,
				MessageLanguage.ID, null), false);
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
