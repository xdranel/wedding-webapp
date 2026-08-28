package myweddinginvitation.webapp.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;
import java.time.Instant;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.guest.DeliveryState;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
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
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class GuestDeliveryControllerTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant FIRST = Instant.parse("2026-07-28T06:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private GuestService guestService;

	@Autowired
	private GuestDeliveryService delivery;

	@Autowired
	private GuestRepository guests;

	@Autowired
	private InvitationLinkSigner signer;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AccountSecurityService accountSecurity;

	@Autowired
	private JdbcTemplate jdbc;

	private MockHttpSession adminSession;
	private MockHttpSession staffSession;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from guest");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED',
				couple_title = 'Rama & Shinta', default_phone_country = 'ID', event_closed = false where id = 1
				""");
		jdbc.update("""
				update message_template
				set body = 'Dear {{salutation}} {{guest_name}}. {{invitation_link}}'
				where message_type = 'INVITATION' and language = 'EN'
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accounts.save(new UserAccount("staff", "{noop}" + PASSWORD, AccountRole.STAFF));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		accountSecurity.changePassword("staff", PASSWORD, PASSWORD);
		adminSession = login("admin");
		staffSession = login("staff");
	}

	@Test
	void openingWhatsappRedirectsWithoutDeliveryMutation() throws Exception {
		Guest guest = savedGuest();

		mockMvc.perform(post("/admin/guests/{id}/open-whatsapp", guest.getId())
				.session(adminSession).with(csrf()).param("language", "EN"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://wa.me/")));

		assertThat(reload(guest).getDeliveryState()).isEqualTo(DeliveryState.UNSENT);
	}

	@Test
	void confirmingSentIsASeparateExplicitMutation() throws Exception {
		Guest guest = savedGuest();

		mockMvc.perform(post("/admin/guests/{id}/confirm-sent", guest.getId())
				.session(adminSession).with(csrf())
				.param("version", Long.toString(guest.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));

		Guest sent = reload(guest);
		assertThat(sent.getDeliveryState()).isEqualTo(DeliveryState.SENT);
		assertThat(sent.getFirstSentAt()).isNotNull();
		assertThat(sent.getLastSentAt()).isEqualTo(sent.getFirstSentAt());
	}

	@Test
	void regenerationInvalidatesOldLinkAndResetsDelivery() throws Exception {
		Guest guest = savedGuest();
		delivery.confirmSent(guest.getId(), guest.getVersion(), FIRST);
		Guest sent = reload(guest);
		String oldPath = URI.create(signer.urlFor(sent)).getRawPath();
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED' where id = 1");
		mockMvc.perform(get(oldPath)).andExpect(status().isOk());

		mockMvc.perform(post("/admin/guests/{id}/regenerate-invitation", sent.getId())
				.session(adminSession).with(csrf())
				.param("version", Long.toString(sent.getVersion())))
				.andExpect(redirectedUrl("/admin/guests/" + sent.getId()));

		Guest regenerated = reload(sent);
		assertThat(regenerated.getInvitationTokenVersion()).isEqualTo(sent.getInvitationTokenVersion() + 1);
		assertThat(regenerated.getTokenRegeneratedAt()).isNotNull();
		assertThat(regenerated).extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
				.containsExactly(DeliveryState.UNSENT, null, null);
		mockMvc.perform(get(oldPath))
				.andExpect(status().isNotFound())
				.andExpect(view().name("guest/unavailable"));
		mockMvc.perform(get(URI.create(signer.urlFor(regenerated)).getRawPath())).andExpect(status().isOk());
	}

	@Test
	void detailShowsDeliveryActionsAndHidesThemForArchivedGuest() throws Exception {
		Guest guest = savedGuest();
		delivery.confirmSent(guest.getId(), guest.getVersion(), FIRST);
		Guest sent = reload(guest);

		mockMvc.perform(get("/admin/guests/{id}", sent.getId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Open WhatsApp")))
				.andExpect(content().string(containsString("Confirm sent")))
				.andExpect(content().string(containsString("Regenerate invitation link")))
				.andExpect(content().string(containsString("name=\"language\"")))
				.andExpect(content().string(containsString(FIRST.toString())))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(not(containsString("Send reminder"))));

		guestService.archive(sent.getId(), sent.getVersion());
		mockMvc.perform(get("/admin/guests/{id}", sent.getId()).session(adminSession))
				.andExpect(content().string(not(containsString("/open-whatsapp"))))
				.andExpect(content().string(not(containsString("/confirm-sent"))))
				.andExpect(content().string(not(containsString("/regenerate-invitation"))));
	}

	@Test
	void deliveryMutationsRequireAdministratorAndCsrf() throws Exception {
		Guest guest = savedGuest();

		for (String action : new String[] {"open-whatsapp", "confirm-sent", "regenerate-invitation"}) {
			mockMvc.perform(post("/admin/guests/{id}/{action}", guest.getId(), action)
					.session(staffSession).with(csrf())
					.param("language", "ID").param("version", Long.toString(guest.getVersion())))
					.andExpect(status().isForbidden());
			mockMvc.perform(post("/admin/guests/{id}/{action}", guest.getId(), action)
					.session(adminSession)
					.param("language", "ID").param("version", Long.toString(guest.getVersion())))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void blockedInitialDeliveryPointsToPublicationAndEventStatus() throws Exception {
		Guest guest = savedGuest();
		jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");

		mockMvc.perform(post("/admin/guests/{id}/open-whatsapp", guest.getId())
				.session(adminSession).with(csrf()).param("language", "ID"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(content().string(containsString("Initial delivery requires a published wedding.")))
				.andExpect(content().string(containsString("Review Publication &amp; Event Status")));
	}

	@Test
	void archivedDeliveryErrorDoesNotPointToEventStatus() throws Exception {
		Guest guest = savedGuest();
		guestService.archive(guest.getId(), guest.getVersion());

		mockMvc.perform(post("/admin/guests/{id}/open-whatsapp", guest.getId())
				.session(adminSession).with(csrf()).param("language", "ID"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
		mockMvc.perform(get("/admin/guests/{id}", guest.getId()).session(adminSession))
				.andExpect(content().string(containsString("Delivery is disabled for archived guests.")))
				.andExpect(content().string(not(containsString("Review Publication &amp; Event Status"))));
	}

	private Guest savedGuest() {
		return guestService.create(new GuestForm(
				"Sari", "Ibu", "ID", "081234567890", null, false, MessageLanguage.ID, null), false);
	}

	private Guest reload(Guest guest) {
		return guests.findById(guest.getId()).orElseThrow();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", PASSWORD))
				.andReturn().getRequest().getSession(false);
	}
}
