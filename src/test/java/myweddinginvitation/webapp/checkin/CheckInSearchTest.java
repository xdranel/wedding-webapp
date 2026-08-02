package myweddinginvitation.webapp.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class CheckInSearchTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired MockMvc mockMvc;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;

	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		accounts.deleteAll();
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
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
