package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpUpdateSource;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class GuestControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	@Autowired
	MockMvc mockMvc;

	@Autowired
	GuestService service;

	@Autowired
	GuestRepository guests;

	@Autowired
	RsvpService rsvps;

	@Autowired
	CheckInService checkIns;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	AccountSecurityService accountSecurity;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void clearGuests() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings set default_phone_country = 'ID',
				publication_state = 'PUBLISHED', event_closed = false,
				time_zone = 'Asia/Jakarta', rsvp_deadline = '2030-08-01 08:00:00',
				private_organizer_note_enabled = false
				where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@AfterEach
	void resetPhoneCountry() {
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
	}

	@Test
	void listsGuestsWithFiltersAndFiftyRowPage() throws Exception {
		service.create(form("Sari", "081234567890"), false);

		mockMvc.perform(get("/admin/guests").session(adminSession)
				.param("query", "Sari")
				.param("delivery", "UNSENT")
				.param("archived", "false"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/list"))
				.andExpect(model().attribute("page", hasProperty("size", is(50))));
	}

	@Test
	void guestNavigationRendersFilteredShortcutsWithOneCurrentPage() throws Exception {
		String defaultPage = mockMvc.perform(get("/admin/guests").session(adminSession))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(defaultPage).contains(
				"href=\"/admin/guests?delivery=UNSENT\"",
				"href=\"/admin/guests?rsvpStatus=NONE\"",
				"href=\"/admin/guests?checkedIn=false\"");
		assertCurrentNavigation(defaultPage, "<a href=\"/admin/guests\" aria-current=\"page\">Guest List</a>");

		String invitations = mockMvc.perform(get("/admin/guests").session(adminSession).param("delivery", "UNSENT"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertCurrentNavigation(invitations,
				"<a href=\"/admin/guests?delivery=UNSENT\" aria-current=\"page\">Invitations</a>");

		String rsvp = mockMvc.perform(get("/admin/guests").session(adminSession).param("rsvpStatus", "NONE"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertCurrentNavigation(rsvp,
				"<a href=\"/admin/guests?rsvpStatus=NONE\" aria-current=\"page\">RSVP</a>");

		String checkIns = mockMvc.perform(get("/admin/guests").session(adminSession).param("checkedIn", "false"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertCurrentNavigation(checkIns,
				"<a href=\"/admin/guests?checkedIn=false\" aria-current=\"page\">Check-ins</a>");
	}

	@Test
	void guestListRendersContextForEachNavigationDestination() throws Exception {
		assertGuestListContext(get("/admin/guests"), "guest-list", "All guests");
		assertGuestListContext(get("/admin/guests").param("delivery", "UNSENT"), "invitations", "Unsent invitations");
		assertGuestListContext(get("/admin/guests").param("rsvpStatus", "NONE"), "rsvp", "Guests awaiting RSVP");
		assertGuestListContext(get("/admin/guests").param("checkedIn", "false"), "check-ins", "Guests not checked in");
	}

	@Test
	void filtersAndDisplaysCurrentRsvpState() throws Exception {
		Guest attending = service.create(form("Hadir Guest", "081234567891", true), false);
		Guest declined = service.create(form("Declined Guest", "081234567892"), false);
		service.create(form("No Reply Guest", "081234567893"), false);
		rsvps.submitGuest(attending.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));
		rsvps.submitGuest(declined.getId(), -1,
				new RsvpSubmission(AttendanceResponse.TIDAK_HADIR, 0, null, false, null));

		mockMvc.perform(get("/admin/guests").session(adminSession).param("rsvpStatus", "HADIR"))
				.andExpect(content().string(containsString("Hadir Guest")))
				.andExpect(content().string(not(containsString("Declined Guest"))))
				.andExpect(content().string(not(containsString("No Reply Guest"))))
				.andExpect(content().string(containsString("HADIR")))
				.andExpect(content().string(containsString(">2<")))
				.andExpect(content().string(containsString("Edit RSVP")));

		mockMvc.perform(get("/admin/guests").session(adminSession).param("rsvpStatus", "TIDAK_HADIR"))
				.andExpect(content().string(containsString("Declined Guest")))
				.andExpect(content().string(not(containsString("Hadir Guest"))));

		mockMvc.perform(get("/admin/guests").session(adminSession).param("rsvpStatus", "NONE"))
				.andExpect(content().string(containsString("No Reply Guest")))
				.andExpect(content().string(not(containsString("Hadir Guest"))))
				.andExpect(content().string(not(containsString("Declined Guest"))));
	}

	@Test
	void checkedInFilterComposesWithExistingGuestFiltersAndDisplaysCurrentCheckIn() throws Exception {
		Guest checked = service.create(form("Filtered Checked", "081234567898", true), false);
		service.confirmSent(checked.getId(), checked.getVersion(), java.time.Instant.parse("2026-08-02T00:00:00Z"));
		checked = guests.findById(checked.getId()).orElseThrow();
		rsvps.submitGuest(checked.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));
		checkIns.confirmGuest(checked.getId(), checked.getVersion(), 1, false, "admin");

		Guest unchecked = service.create(form("Filtered Unchecked", "081234567899"), false);
		service.confirmSent(unchecked.getId(), unchecked.getVersion(), java.time.Instant.parse("2026-08-02T00:00:00Z"));
		rsvps.submitGuest(unchecked.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));

		mockMvc.perform(get("/admin/guests").session(adminSession)
				.param("query", "Filtered").param("delivery", "SENT").param("archived", "false")
				.param("rsvpStatus", "HADIR").param("checkedIn", "true")
				.param("sort", "name,asc").param("page", "0"))
				.andExpect(content().string(containsString("Filtered Checked")))
				.andExpect(content().string(not(containsString("Filtered Unchecked"))))
				.andExpect(content().string(containsString("Actual attendees")))
				.andExpect(content().string(containsString(">1<")));

		mockMvc.perform(get("/admin/guests").session(adminSession)
				.param("query", "Filtered").param("delivery", "SENT").param("archived", "false")
				.param("rsvpStatus", "HADIR").param("checkedIn", "false")
				.param("sort", "name,desc").param("page", "0"))
				.andExpect(content().string(containsString("Filtered Unchecked")))
				.andExpect(content().string(not(containsString("Filtered Checked"))));
	}

	@Test
	void disablingPlusOneRequiresConfirmationAndReducesRsvpAtomically() throws Exception {
		Guest guest = service.create(form("Plus One Guest", "081234567894", true), false);
		RsvpView rsvp = rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));

		mockMvc.perform(post("/admin/guests/{id}", guest.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(guest.getVersion()))
				.param("displayName", guest.getDisplayName()).param("salutation", guest.getSalutation())
				.param("phoneRegion", "ID").param("whatsappNumber", guest.getNormalizedWhatsappNumber())
				.param("preferredLanguage", "ID").param("_plusOneAllowed", "on"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/form"))
				.andExpect(model().attribute("reducePlannedAttendanceWarning", true));

		assertThat(guests.findById(guest.getId())).get().extracting(Guest::isPlusOneAllowed).isEqualTo(true);
		assertThat(rsvps.view(guest.getId())).get().extracting(RsvpView::plannedAttendeeCount).isEqualTo(2);

		Guest current = guests.findById(guest.getId()).orElseThrow();
		mockMvc.perform(post("/admin/guests/{id}", guest.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(current.getVersion()))
				.param("displayName", current.getDisplayName()).param("salutation", current.getSalutation())
				.param("phoneRegion", "ID").param("whatsappNumber", current.getNormalizedWhatsappNumber())
				.param("preferredLanguage", "ID").param("_plusOneAllowed", "on")
				.param("reducePlannedAttendance", "true"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));

		assertThat(guests.findById(guest.getId())).get().extracting(Guest::isPlusOneAllowed).isEqualTo(false);
		RsvpView reduced = rsvps.view(guest.getId()).orElseThrow();
		assertThat(reduced.plannedAttendeeCount()).isEqualTo(1);
		assertThat(reduced.updateSource()).isEqualTo(RsvpUpdateSource.ADMIN);
		assertThat(reduced.version()).isGreaterThan(rsvp.version());
	}

	@Test
	void disablingPlusOneAfterTwoPeopleCheckedInRendersValidationError() throws Exception {
		Guest guest = service.create(form("Checked In Guest", "081234567880", true), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null));
		checkIns.confirmGuest(guest.getId(), guest.getVersion(), 2, false, "admin");

		mockMvc.perform(post("/admin/guests/{id}", guest.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(guest.getVersion()))
				.param("displayName", "Submitted name").param("salutation", guest.getSalutation())
				.param("phoneRegion", "ID").param("whatsappNumber", guest.getNormalizedWhatsappNumber())
				.param("preferredLanguage", "ID").param("_plusOneAllowed", "on"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/form"))
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(model().attribute("guest", hasProperty("id", is(guest.getId()))))
				.andExpect(content().string(containsString("Cannot disable +1 after two people have checked in.")))
				.andExpect(content().string(containsString("value=\"Submitted name\"")));

		assertThat(guests.findById(guest.getId())).get().extracting(Guest::isPlusOneAllowed).isEqualTo(true);
	}

	@Test
	void privateOrganizerNoteIsEscapedOnAdminDetailAndUnavailableToStaff() throws Exception {
		jdbc.update("update wedding_settings set private_organizer_note_enabled = true where id = 1");
		Guest guest = service.create(form("Private Note Guest", "081234567897"), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false,
						"<script>private-organizer-note</script>"));

		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Private organizer note")))
				.andExpect(content().string(containsString("&lt;script&gt;private-organizer-note&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>private-organizer-note</script>"))));

		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(staffSession))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("private-organizer-note"))));
	}

	@Test
	void plusOneWarningPreservesAcceptedDuplicateConfirmation() throws Exception {
		service.create(form("Existing", "081234567895"), false);
		Guest guest = service.create(form("Target", "081234567896", true), false);
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(AttendanceResponse.HADIR, 2, null, false, null));

		mockMvc.perform(post("/admin/guests/{id}", guest.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(guest.getVersion()))
				.param("displayName", guest.getDisplayName()).param("salutation", guest.getSalutation())
				.param("phoneRegion", "ID").param("whatsappNumber", "081234567895")
				.param("preferredLanguage", "ID").param("_plusOneAllowed", "on")
				.param("acceptDuplicate", "true"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("reducePlannedAttendanceWarning", true))
				.andExpect(content().string(containsString("name=\"acceptDuplicate\" value=\"true\"")));
	}

	@Test
	void duplicateRequiresExplicitConfirmation() throws Exception {
		service.create(form("Existing", "081234567890"), false);
		assertThat(guests.count()).isEqualTo(1);

		mockMvc.perform(post("/admin/guests").session(adminSession).with(csrf())
				.param("displayName", "Sari")
				.param("salutation", "Ibu")
				.param("phoneRegion", "ID")
				.param("whatsappNumber", "081234567890")
				.param("preferredLanguage", "ID"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/form"))
				.andExpect(model().attribute("duplicateWarning", true));

		assertThat(guests.count()).isEqualTo(1);
	}

	@Test
	void duplicateWarningPreservesSubmittedValidationErrors() throws Exception {
		service.create(form("Existing", "081234567890"), false);

		mockMvc.perform(post("/admin/guests").session(adminSession).with(csrf())
				.param("displayName", "Ada")
				.param("salutation", "Ibu")
				.param("phoneRegion", "ID")
				.param("whatsappNumber", "081234567890")
				.param("plusOneAllowed", "not-a-boolean")
				.param("preferredLanguage", "ID"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/form"))
				.andExpect(model().attribute("duplicateWarning", true))
				.andExpect(model().attributeHasFieldErrors("form", "plusOneAllowed"));
	}

	@Test
	void newGuestUsesWeddingDefaultAndEditInfersStoredRegion() throws Exception {
		jdbc.update("update wedding_settings set default_phone_country = 'MY' where id = 1");
		mockMvc.perform(get("/admin/guests/new").session(adminSession))
				.andExpect(model().attribute("form", hasProperty("phoneRegion", is("MY"))))
				.andExpect(model().attributeExists("phoneRegions"));

		Guest guest = service.create(form("Ada", "DE", "01512 3456789"), false);
		mockMvc.perform(get("/admin/guests/{id}/edit", guest.getId()).session(adminSession))
				.andExpect(model().attribute("form", hasProperty("phoneRegion", is("DE"))));
	}

	@Test
	void invalidNumberRedisplayKeepsSubmittedRegion() throws Exception {
		mockMvc.perform(post("/admin/guests").session(adminSession).with(csrf())
				.param("displayName", "Ada")
				.param("salutation", "Frau")
				.param("phoneRegion", "DE")
				.param("whatsappNumber", "123")
				.param("preferredLanguage", "EN"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/form"))
				.andExpect(model().attribute("form", hasProperty("phoneRegion", is("DE"))))
				.andExpect(model().attributeExists("phoneRegions"));
	}

	@Test
	void unsupportedSubmittedRegionIsRejected() throws Exception {
		mockMvc.perform(post("/admin/guests").session(adminSession).with(csrf())
				.param("displayName", "Ada")
				.param("salutation", "Ibu")
				.param("phoneRegion", "ZZ")
				.param("whatsappNumber", "081234567890")
				.param("preferredLanguage", "ID"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "phoneRegion"))
				.andExpect(content().string(containsString("aria-describedby=\"phone-region-error\"")))
				.andExpect(content().string(containsString("id=\"phone-region-error\"")));
	}

	@Test
	void archivesRestoresAndDeletesOnlyUnsentGuests() throws Exception {
		Guest guest = service.create(form("Sari", "081234567890"), false);

		mockMvc.perform(post("/admin/guests/{id}/archive", guest.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(guest.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
		Guest archived = guests.findById(guest.getId()).orElseThrow();
		assertThat(archived.isArchived()).isTrue();

		mockMvc.perform(post("/admin/guests/{id}/restore", archived.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(archived.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + archived.getId()));
		Guest restored = guests.findById(guest.getId()).orElseThrow();

		mockMvc.perform(post("/admin/guests/{id}/delete", restored.getId()).session(adminSession).with(csrf())
				.param("version", Long.toString(restored.getVersion())))
				.andExpect(redirectedUrl("/admin/guests"));
		assertThat(guests.findById(guest.getId())).isEmpty();
	}

	@Test
	void onlyAdministratorsWithCsrfCanMutateGuests() throws Exception {
		mockMvc.perform(post("/admin/guests").session(staffSession)
				.with(csrf())
				.param("displayName", "Blocked")
				.param("salutation", "Ibu")
				.param("phoneRegion", "ID")
				.param("whatsappNumber", "081234567890")
				.param("preferredLanguage", "ID"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/admin/guests").session(adminSession)
				.param("displayName", "Blocked")
				.param("salutation", "Ibu")
				.param("phoneRegion", "ID")
				.param("whatsappNumber", "081234567890")
				.param("preferredLanguage", "ID"))
				.andExpect(status().isForbidden());
	}

	private GuestForm form(String name, String whatsappNumber) {
		return form(name, "ID", whatsappNumber);
	}

	private GuestForm form(String name, String whatsappNumber, boolean plusOne) {
		return new GuestForm(name, "Ibu", "ID", whatsappNumber, null, plusOne, MessageLanguage.ID, null);
	}

	private GuestForm form(String name, String phoneRegion, String whatsappNumber) {
		return new GuestForm(name, "Ibu", phoneRegion, whatsappNumber, null, false, MessageLanguage.ID, null);
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}

	private void assertCurrentNavigation(String page, String currentLink) {
		assertThat(page).containsOnlyOnce("aria-current=\"page\"").containsOnlyOnce(currentLink);
	}

	private void assertGuestListContext(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String navigationPage, String heading) throws Exception {
		mockMvc.perform(request.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(model().attribute("navigationPage", navigationPage))
				.andExpect(content().string(containsString(heading)))
				.andExpect(content().string(containsString("href=\"/admin/guests\">Clear filters / View all guests</a>")));
	}
}
