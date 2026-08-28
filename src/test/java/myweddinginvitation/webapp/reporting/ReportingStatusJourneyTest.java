package myweddinginvitation.webapp.reporting;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.guest.DeliveryState;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestCategoryService;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.messaging.GuestDeliveryService;
import myweddinginvitation.webapp.messaging.ReminderKind;
import myweddinginvitation.webapp.messaging.ReminderService;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.CheckInQrSigner;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.wedding.EventStatusMessageForm;
import myweddinginvitation.webapp.wedding.EventStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class ReportingStatusJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final String PRIVATE_NOTE = "private-journey-note";
	private static final String GREETING = "private-journey-greeting";
	private static final String CORRECTION_REASON = "private-correction-reason";
	private static final Instant INITIAL_SENT = Instant.parse("2026-08-18T01:00:00Z");
	private static final Instant RSVP_REMINDER_SENT = Instant.parse("2026-08-18T02:00:00Z");
	private static final Instant EVENT_REMINDER_SENT = Instant.parse("2026-08-18T03:00:00Z");

	@TempDir static Path mediaDirectory;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired GuestCategoryService categories;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired GuestDeliveryService deliveries;
	@Autowired ReminderService reminders;
	@Autowired CheckInService checkIns;
	@Autowired InvitationLinkSigner invitationLinks;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired EventStatusService eventStatus;

	private MockHttpSession admin;
	private long familyId;

	@DynamicPropertySource
	static void mediaDirectory(DynamicPropertyRegistry registry) {
		registry.add("app.media-directory", mediaDirectory::toString);
	}

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("delete from gallery_photo");
		jdbc.update("delete from story_entry");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update partner set full_name = concat('Partner ', display_order),
					nickname = concat('P', display_order), photo_path = concat('partner-', display_order, '.jpg'),
					child_of_label_id = 'Putra/Putri', child_of_label_en = 'Child of',
					parents_names_id = 'Orang tua', parents_names_en = 'Parents', instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false,
					event_status_changed_at = null, event_status_changed_by = null,
					closed_title_id = null, closed_title_en = null, closed_message_id = null, closed_message_en = null,
					couple_title = 'Rama & Shinta', opening_text_id = 'Dengan hormat', opening_text_en = 'Welcome',
					closing_text_id = 'Terima kasih', closing_text_en = 'Thank you',
					time_zone = 'Asia/Jakarta', rsvp_deadline = '2030-08-18 00:00:00',
					default_phone_country = 'ID', calendar_downloads_enabled = true,
					greetings_enabled = true, private_organizer_note_enabled = true,
					gallery_enabled = false, background_audio_enabled = false, background_audio_path = null,
					accent_color = '#7A5C48', font_preset = 'CLASSIC', version = 0 where id = 1
				""");
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name,
					address_id, address_en, map_url)
				values ('CEREMONY', true, '2030-08-19', '08:00:00', 'Journey Venue',
					'Alamat', 'Address', 'https://maps.example.test/journey')
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		categories.create("Family");
		familyId = categories.findAll().getFirst().getId();
		admin = login();
	}

	@Test
	void administratorClosesAndReopensWithoutChangingOperationalTruth() throws Exception {
		Guest active = guest("Ayu Active", familyId, true, "+6281200001001");
		Guest corrected = guest("Bima Corrected", familyId, true, "+6281200001002");
		Guest cancelled = guest("Citra Cancelled", null, false, "+6281200001003");
		Guest unanswered = guest("Dewi Missing", familyId, false, "+6281200001004");
		Guest archived = guest("Eka Archived", familyId, true, "+6281200001005");

		rsvp(active, AttendanceResponse.HADIR, 2, GREETING, true);
		rsvp(corrected, AttendanceResponse.HADIR, 2, null, false);
		rsvp(cancelled, AttendanceResponse.TIDAK_HADIR, 0, null, false);
		rsvp(archived, AttendanceResponse.HADIR, 2, "archived greeting", true);
		deliveries.confirmSent(active.getId(), guest(active).getVersion(), INITIAL_SENT);
		reminders.confirmSent(unanswered.getId(), guest(unanswered).getVersion(), ReminderKind.RSVP, null,
				RSVP_REMINDER_SENT);
		reminders.confirmSent(active.getId(), guest(active).getVersion(), ReminderKind.EVENT, null,
				EVENT_REMINDER_SENT);
		checkIns.confirmGuest(active.getId(), guest(active).getVersion(), 2, false, "admin");
		checkIns.confirmGuest(corrected.getId(), guest(corrected).getVersion(), 2, false, "admin");
		var correctedCheckIn = checkIns.current(corrected.getId()).orElseThrow();
		checkIns.correct(corrected.getId(), correctedCheckIn.version(), 1, CORRECTION_REASON, "admin");
		checkIns.confirmGuest(cancelled.getId(), guest(cancelled).getVersion(), 1, true, "admin");
		var cancelledCheckIn = checkIns.current(cancelled.getId()).orElseThrow();
		checkIns.cancel(cancelled.getId(), cancelledCheckIn.version(), "private-cancellation-reason", "admin");
		guestService.archive(archived.getId(), guest(archived).getVersion());

		String invitationPath = URI.create(invitationLinks.urlFor(guest(active))).getRawPath();
		String qrPayload = qrSigner.payload(active.getPublicId(), active.getInvitationTokenVersion());
		Map<Long, RsvpView> originalRsvps = Map.of(
				active.getId(), rsvps.view(active.getId()).orElseThrow(),
				corrected.getId(), rsvps.view(corrected.getId()).orElseThrow(),
				cancelled.getId(), rsvps.view(cancelled.getId()).orElseThrow(),
				archived.getId(), rsvps.view(archived.getId()).orElseThrow());
		Map<Long, GuestState> originalGuests = states(active, corrected, cancelled, unanswered, archived);
		Map<Long, List<CheckInService.CheckInCorrectionView>> originalHistory = Map.of(
				corrected.getId(), checkIns.history(corrected.getId()),
				cancelled.getId(), checkIns.history(cancelled.getId()));
		var originalQrPreview = checkIns.previewQr(qrPayload);

		assertReportsAndExports(admin, familyId);
		saveCompletedCopy();
		closeEvent();

		assertCompletedPage(invitationPath, "ID", "Acara selesai", "Terima kasih sudah hadir");
		assertCompletedPage(invitationPath, "EN", "Event completed", "Thank you for celebrating with us");
		mockMvc.perform(post(invitationPath + "/rsvp").with(csrf())
				.param("response", "HADIR").param("plannedAttendeeCount", "1")
				.param("pin", "1001").param("version", Long.toString(originalRsvps.get(active.getId()).version())))
				.andExpect(status().isOk()).andExpect(view().name("guest/closed"));
		mockMvc.perform(get(invitationPath + "/qr.png")).andExpect(status().isNotFound());
		mockMvc.perform(get(invitationPath + "/calendar/CEREMONY.ics")).andExpect(status().isNotFound());
		assertThatThrownBy(() -> deliveries.whatsappUri(active.getId(), MessageLanguage.ID))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> deliveries.confirmSent(
				corrected.getId(), guest(corrected).getVersion(), Instant.parse("2026-08-18T04:00:00Z")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> reminders.queue(ReminderKind.RSVP, null))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> reminders.whatsappUri(
				unanswered.getId(), ReminderKind.RSVP, MessageLanguage.ID))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> reminders.confirmSent(unanswered.getId(), guest(unanswered).getVersion(),
				ReminderKind.RSVP, null, Instant.parse("2026-08-18T04:00:00Z")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> checkIns.previewGuest(active.getId()))
				.isInstanceOf(RuntimeException.class).hasMessage("CHECK_IN_CLOSED");
		assertThatThrownBy(() -> checkIns.confirmGuest(
				unanswered.getId(), guest(unanswered).getVersion(), 1, true, "admin"))
				.isInstanceOf(RuntimeException.class).hasMessage("CHECK_IN_CLOSED");

		assertClosedAdministrationRemainsAvailable(active.getId(), corrected.getId());
		assertUnchanged(originalRsvps, originalGuests, originalHistory, qrPayload, active, corrected, cancelled,
				unanswered, archived);

		reopenEvent();

		mockMvc.perform(get(invitationPath).param("language", "EN"))
				.andExpect(status().isOk()).andExpect(view().name("guest/invitation"))
				.andExpect(content().string(containsString("Ayu Active")))
				.andExpect(content().string(containsString("RSVP")));
		mockMvc.perform(get(invitationPath + "/qr.png").param("language", "EN"))
				.andExpect(status().isFound());
		mockMvc.perform(get(invitationPath + "/calendar/CEREMONY.ics").param("language", "EN"))
				.andExpect(status().isOk());
		assertThat(deliveries.whatsappUri(active.getId(), MessageLanguage.EN)).hasHost("wa.me");
		assertThat(reminders.queue(ReminderKind.RSVP, null)).extracting(view -> view.id())
				.contains(unanswered.getId());
		assertThat(reminders.queue(ReminderKind.EVENT, null)).extracting(view -> view.id())
				.contains(active.getId(), corrected.getId());
		assertThat(checkIns.previewQr(qrPayload)).isEqualTo(originalQrPreview);
		assertUnchanged(originalRsvps, originalGuests, originalHistory, qrPayload, active, corrected, cancelled,
				unanswered, archived);
	}

	private void assertReportsAndExports(MockHttpSession session, long categoryId) throws Exception {
		ReportView report = report(get("/admin/reports").session(session));
		assertThat(report.totals()).isEqualTo(new ReportMetrics(4, 6, 2, 1, 1, 4, 2, 3, 0, 1,
				1, 3, 1, 3, 1, 3, 1));
		assertThat(report.categories()).extracting(ReportCategoryView::categoryName)
				.containsExactly("Family", "Uncategorized");
		assertThat(report(get("/admin/reports").session(session).param("categoryId", Long.toString(categoryId))).totals())
				.isEqualTo(new ReportMetrics(3, 5, 2, 0, 1, 4, 2, 3, 0, 1, 1, 2, 1, 2, 1, 2, 1));

		String print = mockMvc.perform(get("/admin/reports/print").session(session))
				.andExpect(status().isOk()).andExpect(view().name("admin/reports/print"))
				.andReturn().getResponse().getContentAsString();
		assertThat(print).contains("Ayu Active", "Bima Corrected", "Citra Cancelled", "Dewi Missing")
				.doesNotContain("Eka Archived", "+6281200001001", PRIVATE_NOTE, GREETING, CORRECTION_REASON);

		String csv = mockMvc.perform(get("/admin/guests/export.csv").session(session))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(UTF_8);
		assertThat(csv).contains("Ayu Active", "Eka Archived", "ARCHIVED", GREETING);
	}

	private void assertCompletedPage(String path, String language, String title, String message) throws Exception {
		String page = mockMvc.perform(get(path).param("language", language))
				.andExpect(status().isOk()).andExpect(view().name("guest/closed"))
				.andReturn().getResponse().getContentAsString();
		assertThat(page).contains(title, message)
				.doesNotContain("Ayu Active", "Bima Corrected", "Citra Cancelled", "Dewi Missing", "Eka Archived",
						"+6281200001001", PRIVATE_NOTE, GREETING, "/calendar/", "/media/", "RSVP");
	}

	private void assertClosedAdministrationRemainsAvailable(long activeGuestId, long correctedGuestId) throws Exception {
		assertReportsAndExports(admin, familyId);
		mockMvc.perform(get("/admin/greetings").session(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/admin/guests/{id}", correctedGuestId).session(admin))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Correction history")));
		mockMvc.perform(get("/admin/guests/{id}", activeGuestId).session(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/admin/wedding").session(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/admin/wedding/media").session(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/admin/system-status").session(admin))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Closed")));
	}

	private void saveCompletedCopy() throws Exception {
		EventStatusMessageForm form = new EventStatusMessageForm();
		form.setVersion(eventStatus.view().version());
		form.setTitleId("Acara selesai");
		form.setMessageId("Terima kasih sudah hadir");
		form.setTitleEn("Event completed");
		form.setMessageEn("Thank you for celebrating with us");
		mockMvc.perform(post("/admin/wedding/event-status/messages").session(admin).with(csrf())
				.param("version", Long.toString(form.getVersion()))
				.param("titleId", form.getTitleId()).param("messageId", form.getMessageId())
				.param("titleEn", form.getTitleEn()).param("messageEn", form.getMessageEn()))
				.andExpect(redirectedUrl("/admin/wedding?messagesSaved"));
	}

	private void closeEvent() throws Exception {
		mockMvc.perform(post("/admin/wedding/event-status/close").session(admin).with(csrf())
				.param("version", Long.toString(eventStatus.view().version())).param("confirmed", "true"))
				.andExpect(redirectedUrl("/admin/wedding?statusChanged"));
		assertThat(eventStatus.view().closed()).isTrue();
	}

	private void reopenEvent() throws Exception {
		mockMvc.perform(post("/admin/wedding/event-status/reopen").session(admin).with(csrf())
				.param("version", Long.toString(eventStatus.view().version())).param("confirmed", "true"))
				.andExpect(redirectedUrl("/admin/wedding?statusChanged"));
		assertThat(eventStatus.view().closed()).isFalse();
	}

	private ReportView report(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk())
				.andExpect(view().name("admin/reports/index")).andReturn();
		return (ReportView) result.getModelAndView().getModel().get("report");
	}

	private void assertUnchanged(Map<Long, RsvpView> originalRsvps, Map<Long, GuestState> originalGuests,
			Map<Long, List<CheckInService.CheckInCorrectionView>> originalHistory, String qrPayload,
			Guest... seededGuests) {
		assertThat(Map.of(
				seededGuests[0].getId(), rsvps.view(seededGuests[0].getId()).orElseThrow(),
				seededGuests[1].getId(), rsvps.view(seededGuests[1].getId()).orElseThrow(),
				seededGuests[2].getId(), rsvps.view(seededGuests[2].getId()).orElseThrow(),
				seededGuests[4].getId(), rsvps.view(seededGuests[4].getId()).orElseThrow()))
				.isEqualTo(originalRsvps);
		assertThat(states(seededGuests)).isEqualTo(originalGuests);
		assertThat(Map.of(
				seededGuests[1].getId(), checkIns.history(seededGuests[1].getId()),
				seededGuests[2].getId(), checkIns.history(seededGuests[2].getId())))
				.isEqualTo(originalHistory);
		assertThat(qrSigner.verify(qrPayload)).isPresent();
	}

	private Map<Long, GuestState> states(Guest... seededGuests) {
		return java.util.Arrays.stream(seededGuests).map(Guest::getId).map(this::guest)
				.collect(java.util.stream.Collectors.toMap(Guest::getId, GuestState::new));
	}

	private Guest guest(String name, Long categoryId, boolean plusOne, String phone) {
		return guestService.create(new GuestForm(name, "Bapak/Ibu", "ID", phone, categoryId, plusOne,
				MessageLanguage.ID, PRIVATE_NOTE), false);
	}

	private Guest guest(Guest guest) {
		return guest(guest.getId());
	}

	private Guest guest(long id) {
		return guests.findById(id).orElseThrow();
	}

	private void rsvp(Guest guest, AttendanceResponse response, int plannedPeople, String greeting, boolean consent) {
		rsvps.submitGuest(guest.getId(), -1,
				new RsvpSubmission(response, plannedPeople, greeting, consent, "private-rsvp-note"));
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
	}

	private record GuestState(long invitationTokenVersion, DeliveryState deliveryState, Instant firstSentAt,
			Instant lastSentAt, Instant lastRsvpReminderSentAt, Instant lastEventReminderSentAt) {
		private GuestState(Guest guest) {
			this(guest.getInvitationTokenVersion(), guest.getDeliveryState(), guest.getFirstSentAt(), guest.getLastSentAt(),
					guest.getLastRsvpReminderSentAt(), guest.getLastEventReminderSentAt());
		}
	}
}
