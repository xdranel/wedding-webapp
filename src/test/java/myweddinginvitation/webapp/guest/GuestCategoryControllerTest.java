package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"app.invitation.signing-secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class GuestCategoryControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	GuestCategoryRepository categories;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	AccountSecurityService accountSecurity;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void createsCollapsedCategoryAndRejectsCaseInsensitiveDuplicate() throws Exception {
		mockMvc.perform(post("/admin/guest-categories")
				.session(adminSession)
				.with(csrf())
				.param("name", "  Keluarga   Besar  "))
				.andExpect(redirectedUrl("/admin/guest-categories"));

		GuestCategory saved = categories.findByNormalizedName("keluarga besar").orElseThrow();
		assertThat(saved.getDisplayName()).isEqualTo("Keluarga Besar");

		mockMvc.perform(post("/admin/guest-categories")
				.session(adminSession)
				.with(csrf())
				.param("name", "keluarga besar"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guest-categories/list"))
				.andExpect(model().attributeHasFieldErrors("form", "name"));
	}

	@Test
	void renameCollapsesWhitespaceAndRejectsDuplicateName() throws Exception {
		GuestCategory family = category("Family", "family", 0);
		GuestCategory friends = category("Friends", "friends", 0);

		mockMvc.perform(post("/admin/guest-categories/{id}", friends.getId())
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(friends.getVersion()))
				.param("name", "  Close   Friends "))
				.andExpect(redirectedUrl("/admin/guest-categories"));
		assertThat(categories.findById(friends.getId()).orElseThrow())
				.extracting(GuestCategory::getDisplayName, GuestCategory::getNormalizedName)
				.containsExactly("Close Friends", "close friends");

		long currentVersion = categories.findById(friends.getId()).orElseThrow().getVersion();
		mockMvc.perform(post("/admin/guest-categories/{id}", friends.getId())
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(currentVersion))
				.param("name", " FAMILY "))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "name"))
				.andExpect(content().string(containsString("aria-describedby=\"category-edit-name-error\"")))
				.andExpect(content().string(containsString("id=\"category-edit-name-error\"")));
		assertThat(categories.findById(family.getId())).isPresent();
	}

	@Test
	void staleRenamePreservesCurrentCategoryAndSubmittedInput() throws Exception {
		GuestCategory category = category("Current", "current", 1);

		mockMvc.perform(post("/admin/guest-categories/{id}", category.getId())
				.session(adminSession)
				.with(csrf())
				.param("version", "0")
				.param("name", "Stale category"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(containsString("Stale category")))
				.andExpect(content().string(containsString("changed by another administrator")));

		assertThat(categories.findById(category.getId()).orElseThrow().getDisplayName()).isEqualTo("Current");
	}

	@Test
	void deleteClearsGuestCategoryAndPageCountsUncategorizedGuests() throws Exception {
		GuestCategory category = category("Family", "family", 0);
		jdbc.update("""
				insert into guest (public_id, display_name, salutation, normalized_whatsapp_number, category_id)
				values (uuid_to_bin(uuid()), 'Sari', 'Ibu', '+6281234567890', ?)
				""", category.getId());

		mockMvc.perform(post("/admin/guest-categories/{id}/delete", category.getId())
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(category.getVersion())))
				.andExpect(redirectedUrl("/admin/guest-categories"));

		assertThat(categories.findById(category.getId())).isEmpty();
		assertThat(jdbc.queryForObject("select category_id from guest where display_name = 'Sari'", Long.class)).isNull();
		mockMvc.perform(get("/admin/guest-categories").session(adminSession))
				.andExpect(model().attribute("withoutCategoryCount", 1L))
				.andExpect(content().string(containsString("Without category")));
	}

	@Test
	void staleDeleteKeepsCategoryAndReportsConflict() throws Exception {
		GuestCategory category = category("Family", "family", 1);

		mockMvc.perform(post("/admin/guest-categories/{id}/delete", category.getId())
				.session(adminSession)
				.with(csrf())
				.param("version", "0"))
				.andExpect(redirectedUrl("/admin/guest-categories"))
				.andExpect(flash().attribute("categoryError",
						"This category changed by another administrator. Reload and try again."));

		assertThat(categories.findById(category.getId())).isPresent();
	}

	@Test
	void pageHasEnglishInlineActionsCsrfAndAdminHomeLink() throws Exception {
		GuestCategory category = category("Family", "family", 0);

		String page = mockMvc.perform(get("/admin/guest-categories").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guest-categories/list"))
				.andExpect(content().string(containsString("Guest categories")))
				.andExpect(content().string(containsString("/admin/guest-categories/" + category.getId())))
				.andExpect(content().string(containsString("Rename")))
				.andExpect(content().string(containsString("Delete")))
				.andExpect(content().string(containsString("confirm(")))
				.andReturn().getResponse().getContentAsString();
		assertThat(page.split("name=\"_csrf\"", -1)).hasSize(6);

		mockMvc.perform(get("/admin").session(adminSession))
				.andExpect(content().string(containsString("/admin/guest-categories")));
	}

	@Test
	void validatesCategoryNameWithoutDiscardingInput() throws Exception {
		mockMvc.perform(post("/admin/guest-categories")
				.session(adminSession)
				.with(csrf())
				.param("name", " "))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "name"));

		String tooLong = "x".repeat(81);
		mockMvc.perform(post("/admin/guest-categories")
				.session(adminSession)
				.with(csrf())
				.param("name", tooLong))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "name"))
				.andExpect(content().string(containsString(tooLong)));
	}

	@Test
	void staffAndRequestsWithoutCsrfCannotMutateCategories() throws Exception {
		mockMvc.perform(get("/admin/guest-categories").session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guest-categories")
				.session(staffSession)
				.with(csrf())
				.param("name", "Blocked"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guest-categories")
				.session(adminSession)
				.param("name", "Blocked"))
				.andExpect(status().isForbidden());
	}

	private GuestCategory category(String displayName, String normalizedName, long version) {
		jdbc.update("insert into guest_category (display_name, normalized_name, version) values (?, ?, ?)",
				displayName, normalizedName, version);
		return categories.findByNormalizedName(normalizedName).orElseThrow();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
