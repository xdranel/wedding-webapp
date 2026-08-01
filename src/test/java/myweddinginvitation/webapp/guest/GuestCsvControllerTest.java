package myweddinginvitation.webapp.guest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import jakarta.servlet.http.HttpSession;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class GuestCsvControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final String HEADER =
			"display_name,whatsapp_number,salutation,category,plus_one_allowed,preferred_language,internal_note\n";

	@Autowired MockMvc mockMvc;
	@Autowired GuestService service;
	@Autowired GuestRepository guests;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	MockHttpSession adminSession;
	MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
		jdbc.update("insert into guest_category (display_name, normalized_name) values ('Keluarga', 'keluarga')");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void uploadStoresBytesAndConfirmationReparsesThenClearsSession() throws Exception {
		byte[] csv = (HEADER + "Sari,081234567890,Ibu,Keluarga,false,ID,\n").getBytes(UTF_8);

		mockMvc.perform(multipart("/admin/guests/import").file(file(csv)).session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/import"))
				.andExpect(model().attributeExists("preview"));
		assertThat((byte[]) adminSession.getAttribute(GuestCsvController.SESSION_CSV)).isEqualTo(csv);

		jdbc.update("delete from guest_category");
		mockMvc.perform(post("/admin/guests/import/confirm").session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/guests/import"))
				.andExpect(model().attribute("preview", org.hamcrest.Matchers.hasProperty(
						"errors", org.hamcrest.Matchers.hasSize(1))));

		assertThat(guests.count()).isZero();
		assertThat(adminSession.getAttribute(GuestCsvController.SESSION_CSV)).isNull();
	}

	@Test
	void warningsRequireAcceptanceAndSuccessfulImportClearsSession() throws Exception {
		service.create(new GuestForm("Existing", "Ibu", "ID", "081234567890", null, false, MessageLanguage.ID, null), false);
		byte[] csv = (HEADER + "Sari,081234567890,Ibu,Keluarga,false,ID,\n").getBytes(UTF_8);
		mockMvc.perform(multipart("/admin/guests/import").file(file(csv)).session(adminSession).with(csrf()));

		mockMvc.perform(post("/admin/guests/import/confirm").session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("acceptWarningsRequired", true));
		assertThat(guests.count()).isEqualTo(1);
		assertThat(adminSession.getAttribute(GuestCsvController.SESSION_CSV)).isNotNull();

		mockMvc.perform(post("/admin/guests/import/confirm").session(adminSession).with(csrf())
				.param("acceptWarnings", "true"))
				.andExpect(redirectedUrl("/admin/guests?imported=1"));
		assertThat(guests.count()).isEqualTo(2);
		assertThat(adminSession.getAttribute(GuestCsvController.SESSION_CSV)).isNull();
	}

	@Test
	void cancelClearsSessionAndDownloadsHaveCsvAttachments() throws Exception {
		adminSession.setAttribute(GuestCsvController.SESSION_CSV, "pending".getBytes(UTF_8));

		mockMvc.perform(post("/admin/guests/import/cancel").session(adminSession).with(csrf()))
				.andExpect(redirectedUrl("/admin/guests"));
		assertThat(adminSession.getAttribute(GuestCsvController.SESSION_CSV)).isNull();

		mockMvc.perform(get("/admin/guests/template.csv").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"guests-template.csv\""));
		mockMvc.perform(get("/admin/guests/export.csv").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"guests.csv\""));
	}

	@Test
	void exportNeutralizesSpreadsheetFormulasInUserText() throws Exception {
		service.create(new GuestForm("=HYPERLINK(\"https://example.test\")", "+salutation", "ID", "081234567890",
				null, false, MessageLanguage.ID, "@note"), false);

		String export = mockMvc.perform(get("/admin/guests/export.csv").session(adminSession))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(UTF_8);

		assertThat(export).contains("'=HYPERLINK", "'+salutation", "'@note")
				.doesNotContain("\n=HYPERLINK");
	}

	@Test
	void attemptedImportAlwaysClearsSessionOnUnexpectedFailure() {
		byte[] bytes = (HEADER + "Sari,081234567890,Ibu,Keluarga,false,ID,\n").getBytes(UTF_8);
		GuestCsvService failingCsv = mock(GuestCsvService.class);
		GuestCsvPreview preview = new GuestCsvPreview(bytes, List.of(), List.of());
		when(failingCsv.preview(any())).thenReturn(preview);
		when(failingCsv.importAll(any(), eq(false))).thenThrow(new IllegalStateException("database unavailable"));
		GuestCsvController controller = new GuestCsvController(failingCsv);
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(GuestCsvController.SESSION_CSV, bytes);

		assertThatThrownBy(() -> controller.confirm(null, session, new ExtendedModelMap()))
				.isInstanceOf(IllegalStateException.class);
		assertThat(session.getAttribute(GuestCsvController.SESSION_CSV)).isNull();
	}

	@Test
	void oversizedUploadIsRejectedBeforeReadingItsBytes() throws Exception {
		GuestCsvService unusedCsv = mock(GuestCsvService.class);
		MultipartFile file = mock(MultipartFile.class);
		when(file.getSize()).thenReturn((long) GuestCsvService.MAX_BYTES + 1);
		GuestCsvController controller = new GuestCsvController(unusedCsv);
		HttpSession session = new MockHttpSession();
		session.setAttribute(GuestCsvController.SESSION_CSV, "old".getBytes(UTF_8));
		ExtendedModelMap model = new ExtendedModelMap();

		assertThat(controller.upload(file, session, model)).isEqualTo("admin/guests/import");
		verify(file, never()).getBytes();
		assertThat(session.getAttribute(GuestCsvController.SESSION_CSV)).isNull();
		assertThat(model).containsKey("uploadError");
	}

	@Test
	void importIsAdminOnlyAndMutationsRequireCsrf() throws Exception {
		mockMvc.perform(get("/admin/guests/import").session(staffSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/admin/guests/import").file(file(HEADER.getBytes(UTF_8))).session(adminSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/guests/import/confirm").session(adminSession))
				.andExpect(status().isForbidden());
	}

	private MockMultipartFile file(byte[] bytes) {
		return new MockMultipartFile("file", "guests.csv", "text/csv", bytes);
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username)
				.param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
