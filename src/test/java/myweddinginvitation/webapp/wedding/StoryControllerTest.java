package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
class StoryControllerTest {
	private static final String PASSWORD = "Test-Password-2026";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	WeddingContentService service;

	@Autowired
	StoryEntryRepository stories;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	AccountSecurityService accountSecurity;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		stories.deleteAll();
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void moveAndDeleteKeepContiguousOrder() {
		long first = service.addStory(story("First"));
		service.addStory(story("Second"));
		long third = service.addStory(story("Third"));

		service.moveStoryUp(third);
		service.deleteStory(first);

		assertThat(stories.findAllByOrderByDisplayOrderAsc())
				.extracting(StoryEntry::getTitleId, StoryEntry::getDisplayOrder)
				.containsExactly(tuple("Third", 1), tuple("Second", 2));
	}

	@Test
	void boundaryMovesAreNoOps() {
		long first = service.addStory(story("First"));
		long second = service.addStory(story("Second"));

		service.moveStoryUp(first);
		service.moveStoryDown(second);

		assertThat(stories.findAllByOrderByDisplayOrderAsc())
				.extracting(StoryEntry::getTitleId, StoryEntry::getDisplayOrder)
				.containsExactly(tuple("First", 1), tuple("Second", 2));
	}

	@Test
	void invalidEditPreservesSubmittedValuesAndFieldErrors() throws Exception {
		long id = service.addStory(story("Original"));

		mockMvc.perform(post("/admin/wedding/story/{id}", id)
				.session(adminSession)
				.with(csrf())
				.param("version", "0")
				.param("date", "2027-05-01")
				.param("titleId", "")
				.param("bodyId", "Submitted body")
				.param("titleEn", "English title"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "titleId"))
				.andExpect(content().string(containsString("Submitted body")));

		assertThat(stories.findById(id).orElseThrow().getTitleId()).isEqualTo("Original");
	}

	@Test
	void storyValidationEnforcesLengthsAndAllowsOptionalDateAndEnglish() throws Exception {
		mockMvc.perform(post("/admin/wedding/story")
				.session(adminSession)
				.with(csrf())
				.param("titleId", "x".repeat(201))
				.param("bodyId", "Body"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "titleId"));

		mockMvc.perform(post("/admin/wedding/story")
				.session(adminSession)
				.with(csrf())
				.param("titleId", "No date")
				.param("bodyId", "No English"))
				.andExpect(redirectedUrl("/admin/wedding/story?storyAdded"));
		StoryEntry entry = stories.findAllByOrderByDisplayOrderAsc().getFirst();
		assertThat(entry.getDate()).isNull();
		assertThat(entry.getTitleEn()).isNull();
		assertThat(entry.getBodyEn()).isNull();
	}

	@Test
	void staleEditPreservesCurrentContentAndRendersConflict() throws Exception {
		long id = service.addStory(story("Original"));
		StoryEntry newer = stories.findById(id).orElseThrow();
		newer.update(story("Newer"));
		stories.saveAndFlush(newer);

		mockMvc.perform(post("/admin/wedding/story/{id}", id)
				.session(adminSession)
				.with(csrf())
				.param("version", "0")
				.param("titleId", "Stale")
				.param("bodyId", "Stale body"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(containsString("changed by another administrator")))
				.andExpect(content().string(containsString("Stale")));

		assertThat(stories.findById(id).orElseThrow().getTitleId()).isEqualTo("Newer");
	}

	@Test
	void storyPageUsesAccessiblePostControlsAndDeleteConfirmation() throws Exception {
		long first = service.addStory(story("First"));
		service.addStory(story("Second"));

		String page = mockMvc.perform(get("/admin/wedding/story").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/admin/wedding/story/" + first + "/up")))
				.andExpect(content().string(containsString("Move up")))
				.andExpect(content().string(containsString("disabled")))
				.andExpect(content().string(containsString("confirm(")))
				.andReturn().getResponse().getContentAsString();

		assertThat(page.split("name=\"_csrf\"", -1)).hasSize(10);
	}

	@Test
	void staffAndRequestsWithoutCsrfCannotManageStory() throws Exception {
		mockMvc.perform(post("/admin/wedding/story")
				.session(staffSession)
				.with(csrf())
				.param("titleId", "Blocked")
				.param("bodyId", "Blocked"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/wedding/story").session(adminSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void administratorCanAddEditMoveAndDeleteStory() throws Exception {
		mockMvc.perform(post("/admin/wedding/story")
				.session(adminSession)
				.with(csrf())
				.param("date", "2027-05-01")
				.param("titleId", "First")
				.param("bodyId", "First body"))
				.andExpect(redirectedUrl("/admin/wedding/story?storyAdded"));
		long id = stories.findAllByOrderByDisplayOrderAsc().getFirst().getId();

		mockMvc.perform(post("/admin/wedding/story/{id}", id)
				.session(adminSession)
				.with(csrf())
				.param("version", "0")
				.param("date", "2027-05-02")
				.param("titleId", "Edited")
				.param("bodyId", "Edited body"))
				.andExpect(redirectedUrl("/admin/wedding/story?storySaved"));
		mockMvc.perform(post("/admin/wedding/story/{id}/delete", id)
				.session(adminSession)
				.with(csrf()))
				.andExpect(redirectedUrl("/admin/wedding/story?storyDeleted"));
		assertThat(stories.findAll()).isEmpty();
	}

	private StoryEntryForm story(String title) {
		StoryEntryForm form = new StoryEntryForm();
		form.setDate(LocalDate.of(2027, 5, 1));
		form.setTitleId(title);
		form.setBodyId(title + " body");
		return form;
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
