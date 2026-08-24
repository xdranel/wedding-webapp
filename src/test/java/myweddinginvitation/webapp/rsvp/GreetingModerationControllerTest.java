package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
class GreetingModerationControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpRepository rsvps;
	@Autowired RsvpService rsvpService;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	MockHttpSession adminSession;
	MockHttpSession staffSession;
	long categoryId;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("insert into guest_category (display_name, normalized_name) values ('Private Category', 'private category')");
		categoryId = jdbc.queryForObject("select id from guest_category where normalized_name = 'private category'", Long.class);
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void filtersGreetingsByModerationStateAndDefaultsToPending() throws Exception {
		rsvp("Pending Guest", "Pending greeting", GreetingModerationState.PENDING);
		rsvp("Approved Guest", "Approved greeting", GreetingModerationState.APPROVED);
		rsvp("Hidden Guest", "Hidden greeting", GreetingModerationState.HIDDEN);

		String page = mockMvc.perform(get("/admin/greetings").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/greetings/list"))
				.andExpect(model().attribute("state", GreetingModerationState.PENDING))
				.andExpect(model().attribute("page", hasProperty("size", is(50))))
				.andExpect(content().string(containsString("Pending greeting")))
				.andExpect(content().string(not(containsString("Approved greeting"))))
				.andExpect(content().string(not(containsString("Hidden greeting"))))
				.andReturn().getResponse().getContentAsString();
		assertThat(page).containsOnlyOnce("<a href=\"/admin/greetings\" aria-current=\"page\">Greetings</a>");

		mockMvc.perform(get("/admin/greetings").param("state", "APPROVED").session(adminSession))
				.andExpect(content().string(containsString("Approved greeting")))
				.andExpect(content().string(not(containsString("Pending greeting"))));
		mockMvc.perform(get("/admin/greetings").param("state", "HIDDEN").session(adminSession))
				.andExpect(content().string(containsString("Hidden greeting")))
				.andExpect(content().string(not(containsString("Pending greeting"))));
	}

	@Test
	void moderationPageDoesNotExposeGuestPrivateData() throws Exception {
		rsvp("Visible Guest", "Visible greeting", GreetingModerationState.PENDING);

		mockMvc.perform(get("/admin/greetings").session(adminSession))
				.andExpect(content().string(containsString("Visible Guest")))
				.andExpect(content().string(containsString("Visible greeting")))
				.andExpect(content().string(not(containsString("+628123450001"))))
				.andExpect(content().string(not(containsString("Private Category"))))
				.andExpect(content().string(not(containsString("Private internal note"))))
				.andExpect(content().string(not(containsString("Private organizer note"))));
	}

	@Test
	void administratorCanApproveAndHideWithOptimisticVersions() throws Exception {
		Rsvp pending = rsvp("Action Guest", "Please publish", GreetingModerationState.PENDING);

		mockMvc.perform(post("/admin/greetings/{id}/approve", pending.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(pending.getVersion())))
				.andExpect(redirectedUrl("/admin/greetings?state=PENDING"));
		Rsvp approved = rsvps.findById(pending.getId()).orElseThrow();
		assertThat(approved.getGreetingModerationState()).isEqualTo(GreetingModerationState.APPROVED);

		mockMvc.perform(post("/admin/greetings/{id}/hide", approved.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(approved.getVersion())))
				.andExpect(redirectedUrl("/admin/greetings?state=PENDING"));
		assertThat(rsvps.findById(approved.getId())).get()
				.extracting(Rsvp::getGreetingModerationState).isEqualTo(GreetingModerationState.HIDDEN);
	}

	@Test
	void staleModerationUsesPrgAndDoesNotOverwriteCurrentState() throws Exception {
		Rsvp pending = rsvp("Concurrent Guest", "Current greeting", GreetingModerationState.PENDING);
		rsvpService.approveGreeting(pending.getId(), pending.getVersion());

		mockMvc.perform(post("/admin/greetings/{id}/hide", pending.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(pending.getVersion())))
				.andExpect(redirectedUrl("/admin/greetings?state=PENDING"))
				.andExpect(flash().attribute("greetingError", containsString("changed")));
		assertThat(rsvps.findById(pending.getId())).get()
				.extracting(Rsvp::getGreetingModerationState).isEqualTo(GreetingModerationState.APPROVED);
	}

	@Test
	void approveRequiresGuestTextAndConsent() throws Exception {
		Guest guest = guest("No Consent");
		Rsvp hidden = rsvps.saveAndFlush(Rsvp.create(guest, AttendanceResponse.HADIR, 1, null, false,
				GreetingModerationState.HIDDEN, null, RsvpUpdateSource.GUEST, null, NOW));

		mockMvc.perform(post("/admin/greetings/{id}/approve", hidden.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(hidden.getVersion())))
				.andExpect(redirectedUrl("/admin/greetings?state=PENDING"))
				.andExpect(flash().attribute("greetingError", containsString("required")));
		assertThat(rsvps.findById(hidden.getId())).get()
				.extracting(Rsvp::getGreetingModerationState).isEqualTo(GreetingModerationState.HIDDEN);
	}

	@Test
	void moderationIsAdministratorOnlyAndMutationsRequireCsrf() throws Exception {
		Rsvp pending = rsvp("Protected Guest", "Protected greeting", GreetingModerationState.PENDING);

		mockMvc.perform(get("/admin/greetings").session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/greetings/{id}/approve", pending.getId()).session(staffSession).with(csrf())
				.param("version", Long.toString(pending.getVersion())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/greetings/{id}/approve", pending.getId()).session(adminSession)
				.param("version", Long.toString(pending.getVersion())))
				.andExpect(status().isForbidden());
		assertThat(rsvps.findById(pending.getId())).get()
				.extracting(Rsvp::getGreetingModerationState).isEqualTo(GreetingModerationState.PENDING);
	}

	private Rsvp rsvp(String name, String greeting, GreetingModerationState state) {
		return rsvps.saveAndFlush(Rsvp.create(guest(name), AttendanceResponse.HADIR, 1, greeting, true,
				state, "Private organizer note", RsvpUpdateSource.GUEST, null, NOW));
	}

	private Guest guest(String name) {
		long suffix = guests.count() + 1;
		return guestService.create(new GuestForm(name, "Private Salutation", "ID",
				"+62812345%04d".formatted(suffix), categoryId, false, MessageLanguage.ID,
				"Private internal note"), false);
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
