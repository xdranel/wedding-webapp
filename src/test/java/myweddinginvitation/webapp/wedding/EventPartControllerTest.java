package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class EventPartControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	@Autowired
	private EventPartRepository events;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		events.deleteAll();
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void visibleEventRequiresCoreFieldsAndOrderedTimes() throws Exception {
		mockMvc.perform(post("/admin/wedding/events/CEREMONY")
				.session(adminSession)
				.with(csrf())
				.param("visible", "true")
				.param("eventDate", "2027-05-01")
				.param("startTime", "10:00")
				.param("endTime", "09:00")
				.param("venueName", "")
				.param("addressId", "")
				.param("mapUrl", "javascript:alert(1)"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "endTime", "venueName", "addressId", "mapUrl"));
	}

	@Test
	void hiddenEventMayRemainIncomplete() throws Exception {
		mockMvc.perform(post("/admin/wedding/events/RECEPTION")
				.session(adminSession)
				.with(csrf())
				.param("visible", "false"))
				.andExpect(redirectedUrl("/admin/wedding?eventsSaved"));

		EventPart saved = events.findByType(EventType.RECEPTION).orElseThrow();
		assertThat(saved.isVisible()).isFalse();
		assertThat(saved.getDate()).isNull();
	}

	@Test
	void pathTypeControlsTheUpsertAndAcceptsOnlyHierarchicalHttpUrls() throws Exception {
		mockMvc.perform(post("/admin/wedding/events/CEREMONY")
				.session(adminSession)
				.with(csrf())
				.param("eventType", "RECEPTION")
				.param("visible", "true")
				.param("eventDate", "2027-05-01")
				.param("startTime", "10:00")
				.param("endTime", "11:00")
				.param("venueName", "Gedung")
				.param("addressId", "Jakarta")
				.param("mapUrl", "https://maps.example.test"))
				.andExpect(redirectedUrl("/admin/wedding?eventsSaved"));

		EventPart saved = events.findByType(EventType.CEREMONY).orElseThrow();
		assertThat(events.findByType(EventType.RECEPTION)).isEmpty();
		assertThat(saved.getDate()).isEqualTo(LocalDate.of(2027, 5, 1));
		assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(11, 0));

		mockMvc.perform(post("/admin/wedding/events/CEREMONY")
				.session(adminSession)
				.with(csrf())
				.param("visible", "true")
				.param("eventDate", "2027-05-01")
				.param("startTime", "10:00")
				.param("venueName", "Gedung")
				.param("addressId", "Jakarta")
				.param("mapUrl", "https:maps.example.test"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "mapUrl"));
	}

	@Test
	void eventsPageHasSeparateAccessibleFormsWithEnglishFallbackLabels() throws Exception {
		String page = mockMvc.perform(get("/admin/wedding/events").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/wedding/events/CEREMONY")))
				.andExpect(content().string(containsString("/admin/wedding/events/RECEPTION")))
				.andExpect(content().string(containsString("Save ceremony")))
				.andExpect(content().string(containsString("Save reception")))
				.andExpect(content().string(containsString("Address (English, falls back to Indonesian)")))
				.andReturn().getResponse().getContentAsString();

		assertThat(page.split("name=\"_csrf\"", -1)).hasSize(3);
	}

	@Test
	void staffCannotManageEvents() throws Exception {
		mockMvc.perform(post("/admin/wedding/events/CEREMONY")
				.session(staffSession)
				.with(csrf()))
				.andExpect(status().isForbidden());
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
