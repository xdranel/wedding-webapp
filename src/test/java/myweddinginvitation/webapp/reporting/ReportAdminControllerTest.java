package myweddinginvitation.webapp.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.checkin.CheckInRepository;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestCategoryService;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
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
class ReportAdminControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired GuestCategoryService categories;
	@Autowired RsvpService rsvps;
	@Autowired CheckInService checkIns;
	@Autowired CheckInRepository checkInRepository;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	private MockHttpSession admin;
	private long alphaCategoryId;
	private long zetaCategoryId;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED', event_closed = false, "
				+ "rsvp_deadline = '2030-08-18 00:00:00' where id = 1");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		categories.create("Zeta");
		categories.create("Alpha");
		alphaCategoryId = categoryId("Alpha");
		zetaCategoryId = categoryId("Zeta");
		admin = login("admin");
	}

	@Test
	void reportShowsDistinctInvitationAndPeopleMetricsWithSortedCategoriesAndPreservedFilter() throws Exception {
		guest("Known Guest", zetaCategoryId, "+4915123456789", "private note");
		guest("Alpha Guest", alphaCategoryId, "+4915123456790", null);

		String report = mockMvc.perform(get("/admin/reports").session(admin))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/reports/index"))
				.andExpect(content().string(containsString("Invitations")))
				.andExpect(content().string(containsString("Potential people")))
				.andExpect(content().string(containsString("Moderate greetings")))
				.andExpect(content().string(containsString("Export complete CSV")))
				.andExpect(content().string(containsString("Print report")))
				.andReturn().getResponse().getContentAsString();
		assertThat(report.indexOf("Alpha")).isLessThan(report.indexOf("Zeta"));

		mockMvc.perform(get("/admin/reports").session(admin).param("categoryId", Long.toString(zetaCategoryId)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("categoryId=" + zetaCategoryId)));
	}

	@Test
	void invalidCategoryReturnsSafeNotFoundResponse() throws Exception {
		mockMvc.perform(get("/admin/reports").session(admin).param("categoryId", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().string(not(containsString("Category not found"))));
	}

	@Test
	void printIncludesAllRowsButExcludesSensitiveGuestAndAuditValues() throws Exception {
		Guest known = guest("Known Guest", zetaCategoryId, "+4915123456789", "private note");
		Guest second = guest("Second Guest", alphaCategoryId, "+4915123456790", null);
		rsvps.submitGuest(known.getId(), -1, new RsvpSubmission(AttendanceResponse.HADIR, 2,
				"private greeting", true, "private organizer note"));
		checkIns.confirmGuest(known.getId(), current(known).getVersion(), 2, false, "admin");
		var checkIn = checkInRepository.findByGuestId(known.getId()).orElseThrow();
		checkIns.correct(known.getId(), checkIn.getVersion(), 1, "private correction reason", "admin");
		jdbc.update("update guest set failed_pin_count = 5, pin_locked_until = ? where id = ?",
				Instant.parse("2030-01-01T00:00:00Z"), known.getId());

		String print = mockMvc.perform(get("/admin/reports/print").session(admin))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/reports/print"))
				.andExpect(content().string(containsString("Known Guest")))
				.andExpect(content().string(containsString("Second Guest")))
				.andExpect(content().string(containsString("HADIR")))
				.andExpect(content().string(not(containsString("+4915123456789"))))
				.andExpect(content().string(not(containsString("private note"))))
				.andExpect(content().string(not(containsString("private greeting"))))
				.andExpect(content().string(not(containsString("private organizer note"))))
				.andExpect(content().string(not(containsString("private correction reason"))))
				.andExpect(content().string(not(containsString("pin_locked_until"))))
				.andReturn().getResponse().getContentAsString();
		assertThat(print).containsPattern("(?s)<td>Second Guest</td>.*?<td>Not checked in</td>\\s*<td>0</td>")
				.contains("Checked in").doesNotContain("WhatsApp", "Internal note", "Greeting", "PIN");
	}

	private Guest guest(String name, long categoryId, String phone, String note) {
		return guestService.create(new GuestForm(name, "Guest", "DE", phone, categoryId, true,
				MessageLanguage.ID, note), false);
	}

	private Guest current(Guest guest) {
		return guests.findById(guest.getId()).orElseThrow();
	}

	private long categoryId(String name) {
		return categories.findAll().stream().filter(category -> category.getDisplayName().equals(name))
				.findFirst().orElseThrow().getId();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
