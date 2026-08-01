package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
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
	JdbcTemplate jdbc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	AccountSecurityService accountSecurity;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void clearGuests() throws Exception {
		jdbc.update("delete from guest");
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
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
				.andExpect(model().attributeHasFieldErrors("form", "phoneRegion"));
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
}
