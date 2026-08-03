package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
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
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class CheckInSearchTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestCategoryService categories;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired CheckInService checkIns;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;

	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		accounts.deleteAll();
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		jdbc.update("""
				update wedding_settings
				set publication_state = 'PUBLISHED', event_closed = false,
				    rsvp_deadline = '2030-08-01 08:00:00'
				where id = 1
				""");
		staffSession = login();
	}

	@Test
	void findsAtMostTwentyActiveGuestsByCaseInsensitiveNameInDisplayNameThenIdOrder() {
		for (int i = 0; i < 21; i++) guest("Ada %02d".formatted(i), "+6281111%04d".formatted(i));
		Guest archived = guest("Ada archived", "+62819999999");
		guestService.archive(archived.getId(), archived.getVersion());

		List<Guest> found = guests.findActiveForCheckIn("aDa", false, PageRequest.of(0, 20));

		assertThat(found).hasSize(20);
		assertThat(found).extracting(Guest::getDisplayName)
				.containsExactlyElementsOf(java.util.stream.IntStream.range(0, 20)
						.mapToObj(i -> "Ada %02d".formatted(i)).toList());
	}

	@Test
	void findsOnlyExactLastFourDigitsOfNormalizedE164Numbers() {
		Guest matching = guest("Suffix match", "+62811112222");
		guest("Different suffix", "+62811113333");

		List<Guest> found = guests.findActiveForCheckIn("2222", true, PageRequest.of(0, 20));

		assertThat(found).extracting(Guest::getId).containsExactly(matching.getId());
	}

	@Test
	void rejectsBlankShortAndNonSuffixNumericSearchesWithoutRenderingGuests() throws Exception {
		guest("Visible only after valid search", "+62811112222");

		for (String query : List.of(" ", "A", "12345")) {
			mockMvc.perform(get("/check-in/search").session(staffSession).param("q", query))
					.andExpect(status().isOk())
					.andExpect(view().name("checkin/home"))
					.andExpect(content().string(containsString("Enter at least two name characters or exactly four digits.")))
					.andExpect(content().string(not(containsString("Visible only after valid search"))));
		}
	}

	@Test
	void manualSearchRendersEveryApprovedDistinguishingFieldWithoutPrivateData() throws Exception {
		categories.create("Family");
		long categoryId = categories.findAll().getFirst().getId();
		Guest guest = guestService.create(new GuestForm("Distinct guest", "Guest", "ID", "+62811112222",
				categoryId, true, MessageLanguage.EN, "private-search-note"), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));
		checkIns.confirmGuest(guest.getId(), guest.getVersion(), 1, false, "staff");

		mockMvc.perform(get("/check-in/search").session(staffSession).param("q", "Distinct"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Distinct guest")))
				.andExpect(content().string(containsString("Family")))
				.andExpect(content().string(containsString("•••• 2222")))
				.andExpect(content().string(containsString("Plus one: Allowed")))
				.andExpect(content().string(containsString("RSVP: HADIR")))
				.andExpect(content().string(containsString("Planned attendees: 2")))
				.andExpect(content().string(containsString("Check-in: Checked in")))
				.andExpect(content().string(not(containsString("+62811112222"))))
				.andExpect(content().string(not(containsString("private-search-note"))));
	}

	@Test
	void manualSearchFetchesGuestCategoriesInOneStatement() {
		categories.create("Family");
		categories.create("Friends");
		List<Long> categoryIds = categories.findAll().stream().map(category -> category.getId()).toList();
		guestService.create(new GuestForm("Category result one", "Guest", "ID", "+62811112221",
				categoryIds.get(0), false, MessageLanguage.EN, null), false);
		guestService.create(new GuestForm("Category result two", "Guest", "ID", "+62811112222",
				categoryIds.get(1), false, MessageLanguage.EN, null), false);
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		assertThat(guests.findActiveForCheckIn("Category result", false, PageRequest.of(0, 20)))
				.extracting(found -> found.getCategory().getDisplayName())
				.containsExactly("Family", "Friends");

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void rejectsSearchLongerThanGuestNamesBeforeQuerying() throws Exception {
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		mockMvc.perform(get("/check-in").session(staffSession)).andExpect(status().isOk());
		long sessionCheckStatements = statistics.getPrepareStatementCount();
		statistics.clear();

		mockMvc.perform(get("/check-in/search").session(staffSession).param("q", "a".repeat(161)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Enter at least two name characters or exactly four digits.")));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(sessionCheckStatements);
	}

	private Guest guest(String name, String number) {
		return guestService.create(new GuestForm(name, "Guest", "ID", number, null, false,
				MessageLanguage.EN, null), false);
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login")
				.with(SecurityMockMvcRequestPostProcessors.csrf())
				.param("username", "staff").param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
