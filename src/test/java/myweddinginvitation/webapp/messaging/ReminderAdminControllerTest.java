package myweddinginvitation.webapp.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, ReminderAdminControllerTest.FixedClockConfiguration.class})
class ReminderAdminControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant NOW = Instant.parse("2026-08-12T06:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired ReminderService reminders;
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
		jdbc.update("delete from rsvp");
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', rsvp_deadline = '2030-08-12 12:00:00',
				couple_title = 'Rama & Shinta', default_phone_country = 'ID' where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
		phoneSuffix = 0;
	}

	@Test
	void administratorSeesBothQueuesWithCategoryLanguageAndLastSentTime() throws Exception {
		long familyId = category("Family", "family");
		Guest english = guest("English guest", familyId, MessageLanguage.EN);
		reminders.confirmSent(english.getId(), english.getVersion(), ReminderKind.RSVP, familyId, NOW);
		completeVisibleEvent();
		Guest attending = guest("Attending guest", familyId, MessageLanguage.ID);
		rsvp(attending, AttendanceResponse.HADIR);

		String page = mockMvc.perform(get("/admin/reminders").session(adminSession)
				.param("kind", "RSVP").param("categoryId", Long.toString(familyId)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("RSVP reminders")))
				.andExpect(content().string(containsString("Event reminders")))
				.andExpect(content().string(containsString("English guest")))
				.andExpect(content().string(containsString("Family")))
				.andExpect(content().string(containsString("value=\"EN\" selected=\"selected\">English</option>")))
				.andExpect(content().string(containsString(NOW.toString())))
				.andExpect(content().string(not(containsString("batch"))))
				.andReturn().getResponse().getContentAsString();
		assertThat(page).containsOnlyOnce("<a href=\"/admin/reminders\" aria-current=\"page\">Reminders</a>");
		assertThat(page).containsOnlyOnce("aria-current=\"page\"")
				.containsOnlyOnce("role=\"tablist\"")
				.contains("role=\"tab\" aria-selected=\"true\">RSVP reminders</a>",
						"role=\"tab\" aria-selected=\"false\">Event reminders</a>");

		String eventPage = mockMvc.perform(get("/admin/reminders").session(adminSession)
				.param("kind", "EVENT").param("categoryId", Long.toString(familyId)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Attending guest")))
				.andExpect(content().string(containsString("Open WhatsApp")))
				.andExpect(content().string(containsString("Confirm sent")))
				.andReturn().getResponse().getContentAsString();
		assertThat(eventPage).containsOnlyOnce("aria-current=\"page\"")
				.contains("role=\"tab\" aria-selected=\"false\">RSVP reminders</a>",
						"role=\"tab\" aria-selected=\"true\">Event reminders</a>");
	}

	@Test
	void openingWhatsappUsesRequestedLanguageWithoutChangingReminderState() throws Exception {
		Guest guest = guest("Open only", null, MessageLanguage.ID);

		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/open-whatsapp", "RSVP", guest.getId())
				.session(adminSession).with(csrf()).param("language", "EN"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://wa.me/")));

		assertThat(reload(guest).getLastRsvpReminderSentAt()).isNull();
	}

	@Test
	void confirmationRetainsCategoryAndRedirectsToTheNextGuest() throws Exception {
		long familyId = category("Family", "family");
		Guest current = guest("Current", familyId, MessageLanguage.ID);
		Guest next = guest("Next", familyId, MessageLanguage.ID);

		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/confirm-sent", "RSVP", current.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(current.getVersion()))
				.param("categoryId", Long.toString(familyId)))
				.andExpect(redirectedUrl("/admin/reminders?kind=RSVP&categoryId=" + familyId + "#guest-" + next.getId()));

		assertThat(reload(current).getLastRsvpReminderSentAt()).isEqualTo(NOW);
	}

	@Test
	void confirmationReportsCompletionAndRejectsStaleOrIneligibleGuestsSafely() throws Exception {
		Guest completed = guest("Completed", null, MessageLanguage.ID);
		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/confirm-sent", "RSVP", completed.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(completed.getVersion())))
				.andExpect(redirectedUrl("/admin/reminders?kind=RSVP"))
				.andExpect(flash().attributeExists("reminderCompletion"));

		Guest stale = guest("Stale", null, MessageLanguage.ID);
		reminders.confirmSent(stale.getId(), stale.getVersion(), ReminderKind.RSVP, null, NOW);
		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/confirm-sent", "RSVP", stale.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(stale.getVersion())))
				.andExpect(redirectedUrl("/admin/reminders?kind=RSVP"))
				.andExpect(flash().attributeExists("reminderError"));
		assertThat(reload(stale).getLastRsvpReminderSentAt()).isEqualTo(NOW);

		Guest ineligible = guest("Ineligible", null, MessageLanguage.ID);
		rsvp(ineligible, AttendanceResponse.HADIR);
		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/confirm-sent", "RSVP", ineligible.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(ineligible.getVersion())))
				.andExpect(redirectedUrl("/admin/reminders?kind=RSVP"))
				.andExpect(flash().attributeExists("reminderError"));
		assertThat(reload(ineligible).getLastRsvpReminderSentAt()).isNull();
	}

	@Test
	void reminderRoutesRequireAdministratorAndCsrf() throws Exception {
		Guest guest = guest("Protected", null, MessageLanguage.ID);
		mockMvc.perform(get("/admin/reminders"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(get("/admin/reminders").session(staffSession))
				.andExpect(status().isForbidden());

		for (String action : new String[] {"open-whatsapp", "confirm-sent"}) {
			mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/{action}", "RSVP", guest.getId(), action)
					.session(staffSession).with(csrf()).param("language", "ID")
					.param("version", Long.toString(guest.getVersion())))
					.andExpect(status().isForbidden());
			mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/{action}", "RSVP", guest.getId(), action)
					.session(adminSession).param("language", "ID")
					.param("version", Long.toString(guest.getVersion())))
					.andExpect(status().isForbidden());
		}
	}

	private Guest guest(String name, Long categoryId, MessageLanguage language) {
		phoneSuffix++;
		return guestService.create(new GuestForm(name, "Ibu", "ID", "08120000%04d".formatted(phoneSuffix),
				categoryId, false, language, null), false);
	}

	private void rsvp(Guest guest, AttendanceResponse response) {
		rsvps.correctByAdmin(guest.getId(), -1,
				new RsvpSubmission(response, 1, null, false, null), "admin");
	}

	private Guest reload(Guest guest) {
		return guests.findById(guest.getId()).orElseThrow();
	}

	private long category(String name, String normalized) {
		jdbc.update("insert into guest_category (display_name, normalized_name) values (?, ?)", name, normalized);
		return jdbc.queryForObject("select id from guest_category where normalized_name = ?", Long.class, normalized);
	}

	private void completeVisibleEvent() {
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, map_url, version)
				values ('CEREMONY', true, '2030-08-12', '10:00:00', 'Gedung', 'Jakarta', 'https://maps.example/event', 0)
				""");
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}

	@TestConfiguration
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
