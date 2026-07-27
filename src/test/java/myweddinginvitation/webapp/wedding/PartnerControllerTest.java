package myweddinginvitation.webapp.wedding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class PartnerControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Path MEDIA_DIRECTORY = createMediaDirectory();
	@Autowired
	MockMvc mockMvc;

	@Autowired
	PartnerRepository partners;

	@Autowired
	PartnerPhotoStorage storage;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	AccountSecurityService accountSecurity;

	long partnerId;
	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void resetPartners() throws Exception {
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
		for (Partner partner : partners.findAll()) {
			if (partner.getPhotoPath() != null) storage.delete(partner.getPhotoPath());
		}
		partners.findAll().forEach(partner -> partner.update(new PartnerForm()));
		partners.flush();
		partnerId = partners.findAllByOrderByDisplayOrderAsc().getFirst().getId();
	}

	@Test
	void invalidReplacementPreservesExistingPhoto() throws Exception {
		String oldPath = saveValidPartnerPhoto();
		long version = currentVersion();

		mockMvc.perform(multipart("/admin/wedding/partners/{id}", partnerId)
				.file(new MockMultipartFile("photo", "bad.jpg", "image/jpeg", "bad".getBytes(UTF_8)))
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(version))
				.param("fullName", "Rama")
				.param("nickname", "Rama")
				.param("childOfLabelId", "Putra dari")
				.param("parentsNamesId", "Ayah & Ibu"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/wedding/partners"))
				.andExpect(model().attributeHasFieldErrors("form", "photo"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Photo must be a JPEG, PNG, or WebP image")));

		assertThat(partners.findById(partnerId).orElseThrow().getPhotoPath()).isEqualTo(oldPath);
		assertThat(MEDIA_DIRECTORY.resolve(oldPath)).exists();
	}

	@Test
	void successfulReplacementDeletesOldPhotoAfterSavingNewPath() throws Exception {
		String oldPath = saveValidPartnerPhoto();
		long version = currentVersion();

		mockMvc.perform(multipart("/admin/wedding/partners/{id}", partnerId)
				.file(new MockMultipartFile("photo", "new.png", "image/png", png()))
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(version))
				.param("fullName", "Rama")
				.param("nickname", "Rama")
				.param("childOfLabelId", "Putra dari")
				.param("parentsNamesId", "Ayah & Ibu"))
				.andExpect(status().is3xxRedirection());

		String newPath = partners.findById(partnerId).orElseThrow().getPhotoPath();
		assertThat(newPath).isNotEqualTo(oldPath);
		assertThat(MEDIA_DIRECTORY.resolve(newPath)).exists();
		assertThat(MEDIA_DIRECTORY.resolve(oldPath)).doesNotExist();
	}

	@Test
	void storedPhotoIsAvailableOnlyToAdministratorsWithSafeResponseHeaders() throws Exception {
		String path = saveValidPartnerPhoto();

		mockMvc.perform(get("/admin/wedding/media/{filename}", path).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mockMvc.perform(get("/admin/wedding/media/{filename}", path).session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/wedding/media/missing.jpg").session(adminSession))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/admin/wedding/media/{filename}", "../outside.jpg").session(adminSession))
				.andExpect(status().isNotFound());
	}

	@Test
	void stalePartnerEditPreservesExistingPhotoAndDoesNotStoreTheSubmittedPhoto() throws Exception {
		String oldPath = saveValidPartnerPhoto();
		long staleVersion = currentVersion();
		Partner newer = partners.findById(partnerId).orElseThrow();
		newer.update(validForm("Newer Rama"));
		partners.saveAndFlush(newer);

		mockMvc.perform(multipart("/admin/wedding/partners/{id}", partnerId)
				.file(new MockMultipartFile("photo", "new.png", "image/png", png()))
				.session(adminSession)
				.with(csrf())
				.param("version", Long.toString(staleVersion))
				.param("fullName", "Stale Rama")
				.param("nickname", "Rama")
				.param("childOfLabelId", "Putra dari")
				.param("parentsNamesId", "Ayah & Ibu"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("changed by another administrator")));

		assertThat(partners.findById(partnerId).orElseThrow().getPhotoPath()).isEqualTo(oldPath);
		assertThat(MEDIA_DIRECTORY.resolve(oldPath)).exists();
		assertThat(Files.list(MEDIA_DIRECTORY).toList()).hasSize(1);
	}

	@Test
	void administratorCanSwapPartnerOrder() throws Exception {
		var before = partners.findAllByOrderByDisplayOrderAsc();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/wedding/partners/swap")
				.session(adminSession)
				.with(csrf()))
				.andExpect(status().is3xxRedirection());

		assertThat(partners.findAllByOrderByDisplayOrderAsc())
				.extracting(Partner::getId).containsExactly(before.get(1).getId(), before.get(0).getId());
	}

	private String saveValidPartnerPhoto() {
		String path = storage.store(new MockMultipartFile("photo", "old.jpg", "image/jpeg", jpeg()));
		Partner partner = partners.findById(partnerId).orElseThrow();
		PartnerForm form = validForm("Rama");
		partner.update(form);
		partner.replacePhoto(path);
		partners.saveAndFlush(partner);
		return path;
	}

	private PartnerForm validForm(String fullName) {
		PartnerForm form = new PartnerForm();
		form.setFullName(fullName);
		form.setNickname("Rama");
		form.setChildOfLabelId("Putra dari");
		form.setParentsNamesId("Ayah & Ibu");
		return form;
	}

	private long currentVersion() {
		return partners.findById(partnerId).orElseThrow().getVersion();
	}

	private static byte[] jpeg() {
		return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
	}

	private static byte[] png() {
		return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login")
				.with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", () -> MEDIA_DIRECTORY.toString());
	}

	private static Path createMediaDirectory() {
		try {
			return Files.createTempDirectory("partner-media-");
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
