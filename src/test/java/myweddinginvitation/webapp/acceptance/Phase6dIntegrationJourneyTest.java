package myweddinginvitation.webapp.acceptance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.checkin.CheckInCorrectionAction;
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
import myweddinginvitation.webapp.reporting.ReportService;
import myweddinginvitation.webapp.reporting.ReportView;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.CheckInQrSigner;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.wedding.EventStatusService;
import myweddinginvitation.webapp.wedding.GalleryPhoto;
import myweddinginvitation.webapp.wedding.GalleryPhotoRepository;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
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
class Phase6dIntegrationJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final String STAFF_TEMPORARY_PASSWORD = "Temporary-Password-2026";
	private static final String STAFF_PASSWORD = "Staff-Password-2026";
	private static final Instant INITIAL_SENT = Instant.parse("2026-08-23T01:00:00Z");
	private static final Instant EVENT_REMINDER_SENT = Instant.parse("2026-08-23T02:00:00Z");
	private static final byte[] MP3 = {(byte) 0xff, (byte) 0xfb, 1, 2, 3, 4};

	@TempDir static Path mediaDirectory;

	@Autowired MockMvc mockMvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired GuestCategoryService categories;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired InvitationLinkSigner invitationLinks;
	@Autowired GuestDeliveryService deliveries;
	@Autowired RsvpService rsvps;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired CheckInService checkIns;
	@Autowired ReminderService reminders;
	@Autowired ReportService reports;
	@Autowired WeddingSettingsRepository settings;
	@Autowired GalleryPhotoRepository photos;
	@Autowired EventStatusService eventStatus;

	private MockHttpSession admin;
	private long familyId;
	private long friendsId;

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
				update partner set full_name = null, nickname = null, photo_path = null,
				child_of_label_id = null, child_of_label_en = null,
				parents_names_id = null, parents_names_en = null, instagram_url = null
				""");
		jdbc.update("""
				update wedding_settings set publication_state = 'DRAFT', event_closed = false,
				event_status_changed_at = null, event_status_changed_by = null,
				closed_title_id = null, closed_title_en = null, closed_message_id = null, closed_message_en = null,
				couple_title = null, opening_text_id = null, opening_text_en = null,
				closing_text_id = null, closing_text_en = null, time_zone = 'Asia/Jakarta',
				rsvp_deadline = null, default_phone_country = 'ID', calendar_downloads_enabled = false,
				gallery_enabled = false, background_audio_enabled = false, background_audio_path = null,
				accent_color = '#7A5C48', font_preset = 'CLASSIC', version = 0 where id = 1
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		categories.create("Family");
		categories.create("Friends");
		familyId = categories.findAll().getFirst().getId();
		friendsId = categories.findAll().getLast().getId();
		admin = login("admin", PASSWORD, "/admin");
	}

	@Test
	void completeWeddingLifecycleKeepsOneAuthoritativeState() throws Exception {
		publishBilingualWedding();
		long photoId = uploadPhoto();
		uploadAudio();
		enableMedia();
		Guest indonesian = guest("Indonesian Journey Guest", "+6281234567890", MessageLanguage.ID, true, familyId);
		Guest english = guest("English Journey Guest", "+14155550123", MessageLanguage.EN, false, friendsId);
		String invitationPath = path(indonesian);

		assertInvitation(invitationPath, "ID", "Dengan hormat", photoId);
		assertInvitation(invitationPath, "EN", "Welcome", photoId);
		URI openedDelivery = deliveries.whatsappUri(indonesian.getId(), MessageLanguage.ID);
		deliveries.confirmSent(indonesian.getId(), reload(indonesian).getVersion(), INITIAL_SENT);
		assertThat(reload(indonesian)).extracting(Guest::getDeliveryState, Guest::getFirstSentAt, Guest::getLastSentAt)
				.containsExactly(DeliveryState.SENT, INITIAL_SENT, INITIAL_SENT);
		assertGeneratedLanguageQuery(openedDelivery, indonesian, MessageLanguage.ID);

		MockHttpSession guestSession = submitAttendingRsvp(invitationPath);
		assertQrAndCalendars(invitationPath, guestSession);
		reminders.confirmSent(indonesian.getId(), reload(indonesian).getVersion(), ReminderKind.EVENT, familyId,
				EVENT_REMINDER_SENT);
		assertThat(reload(indonesian).getLastEventReminderSentAt()).isEqualTo(EVENT_REMINDER_SENT);

		MockHttpSession staff = createStaff();
		previewAndConfirmCheckIn(staff, indonesian);
		correctCheckIn(indonesian);
		assertThat(checkIns.history(indonesian.getId())).extracting(CheckInService.CheckInCorrectionView::action)
				.containsExactly(CheckInCorrectionAction.CORRECT);
		assertReportsPrintAndCsv();

		JourneySnapshot snapshot = snapshot(indonesian, photoId);
		close();
		assertClosedPublicPages(invitationPath);
		assertClosedWritesAreBlocked(invitationPath, snapshot);
		assertReportsPrintAndCsv();
		mockMvc.perform(get("/admin/guests/{id}", indonesian.getId()).session(admin)).andExpect(status().isOk());

		reopen();
		assertInvitation(invitationPath, "EN", "Welcome", photoId);
		assertThat(deliveries.whatsappUri(indonesian.getId(), MessageLanguage.EN)).hasHost("wa.me");
		assertThat(checkIns.previewGuest(indonesian.getId()).currentCheckIn().actualAttendeeCount()).isEqualTo(1);
		assertThat(reminders.queue(ReminderKind.EVENT, familyId)).extracting(view -> view.id())
				.contains(indonesian.getId());
		JourneySnapshot reopened = snapshot(indonesian, photoId);
		assertThat(reopened.invitationTokenVersion()).isEqualTo(snapshot.invitationTokenVersion());
		assertThat(reopened).isEqualTo(snapshot);
		assertThat(eventStatus.view().closed()).isFalse();
		assertThat(eventStatus.view().changedAt()).isNotNull();
		assertThat(eventStatus.view().changedBy()).isEqualTo("admin");
	}

	private void publishBilingualWedding() throws Exception {
		jdbc.update("""
				update partner set full_name = concat('Partner ', display_order),
				nickname = concat('P', display_order), photo_path = concat('partner-', display_order, '.jpg'),
				child_of_label_id = 'Putra/Putri', child_of_label_en = 'Child of',
				parents_names_id = 'Parents', parents_names_en = 'Parents'
				""");
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, end_time, venue_name,
					address_id, address_en, map_url)
				values ('CEREMONY', true, '2030-08-24', '08:00:00', '09:00:00', 'Journey Venue',
					'Alamat Journey', 'Journey Address', 'https://maps.example.test/ceremony'),
					('RECEPTION', true, '2030-08-24', '18:00:00', '20:00:00', 'Journey Venue',
					'Alamat Journey', 'Journey Address', 'https://maps.example.test/reception')
				""");
		mockMvc.perform(post("/admin/wedding/settings").session(admin).with(csrf())
				.param("version", Long.toString(settingsVersion()))
				.param("coupleTitle", "Phase 6D Couple")
				.param("openingTextId", "Dengan hormat").param("openingTextEn", "Welcome")
				.param("closingTextId", "Terima kasih").param("closingTextEn", "Thank you")
				.param("timeZone", "Asia/Jakarta").param("rsvpDeadline", "2030-08-23T23:59")
				.param("defaultPhoneCountry", "ID").param("accentColor", "#7A5C48")
				.param("fontPreset", "CLASSIC").param("greetingsEnabled", "true")
				.param("calendarDownloadsEnabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding?settingsSaved"));
		mockMvc.perform(post("/admin/wedding/publish").session(admin).with(csrf())
				.param("version", Long.toString(settingsVersion())))
				.andExpect(redirectedUrl("/admin/wedding"));
	}

	private long uploadPhoto() throws Exception {
		mockMvc.perform(multipart("/admin/wedding/media/photos").session(admin).with(csrf())
				.file(image()).param("version", "0").param("altText", "Journey photo")
				.param("captionId", "Foto Journey").param("captionEn", "Journey photo"))
				.andExpect(redirectedUrl("/admin/wedding/media?photoAdded"));
		return photos.findAllByOrderByPositionAsc().getFirst().getId();
	}

	private void uploadAudio() throws Exception {
		mockMvc.perform(multipart("/admin/wedding/media/audio").session(admin).with(csrf())
				.file(new MockMultipartFile("audio", "journey.mp3", "audio/mpeg", MP3))
				.param("version", Long.toString(settingsVersion())))
				.andExpect(redirectedUrl("/admin/wedding/media?audioReplaced"));
	}

	private void enableMedia() throws Exception {
		mockMvc.perform(post("/admin/wedding/media/gallery-enabled").session(admin).with(csrf())
				.param("version", Long.toString(settingsVersion())).param("enabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?galleryVisibilityChanged"));
		mockMvc.perform(post("/admin/wedding/media/audio-enabled").session(admin).with(csrf())
				.param("version", Long.toString(settingsVersion())).param("enabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding/media?audioVisibilityChanged"));
	}

	private Guest guest(String name, String phone, MessageLanguage language, boolean plusOne, Long categoryId) {
		return guestService.create(new GuestForm(name, "Bapak/Ibu", "ID", phone, categoryId, plusOne, language, null), false);
	}

	private void assertInvitation(String invitationPath, String language, String opening, long photoId) throws Exception {
		mockMvc.perform(get(invitationPath).param("language", language))
				.andExpect(status().isOk()).andExpect(view().name("guest/invitation"))
				.andExpect(content().string(containsString(opening)))
				.andExpect(content().string(containsString("/media/gallery/" + photoId + "/image")))
				.andExpect(content().string(containsString("id=\"background-audio\"")));
	}

	private void assertGeneratedLanguageQuery(URI delivery, Guest guest, MessageLanguage language) {
		String message = URLDecoder.decode(delivery.getRawQuery(), UTF_8);
		assertThat(message).contains(invitationLinks.urlFor(reload(guest), language));
	}

	private MockHttpSession submitAttendingRsvp(String invitationPath) throws Exception {
		MvcResult saved = mockMvc.perform(post(invitationPath + "/rsvp").with(csrf())
				.param("language", "ID").param("response", "HADIR").param("plannedAttendeeCount", "2")
				.param("pin", "7890").param("version", "-1"))
				.andExpect(redirectedUrl(invitationPath + "?language=ID&rsvpSaved")).andReturn();
		assertThat(rsvps.view(guests.findAll().stream()
				.filter(guest -> "Indonesian Journey Guest".equals(guest.getDisplayName())).findFirst().orElseThrow().getId()))
				.get().extracting(RsvpView::response, RsvpView::plannedAttendeeCount)
				.containsExactly(AttendanceResponse.HADIR, 2);
		return (MockHttpSession) saved.getRequest().getSession(false);
	}

	private void assertQrAndCalendars(String invitationPath, MockHttpSession guestSession) throws Exception {
		mockMvc.perform(get(invitationPath + "/qr.png").session(guestSession))
				.andExpect(status().isOk()).andExpect(content().contentType("image/png"));
		mockMvc.perform(get(invitationPath + "/calendar/CEREMONY.ics").param("language", "ID"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("SUMMARY:")));
		mockMvc.perform(get(invitationPath + "/calendar/RECEPTION.ics").param("language", "EN"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("SUMMARY:")));
	}

	private MockHttpSession createStaff() throws Exception {
		mockMvc.perform(post("/admin/accounts").session(admin).with(csrf())
				.param("username", "door-staff").param("temporaryPassword", STAFF_TEMPORARY_PASSWORD))
				.andExpect(redirectedUrl("/admin/accounts"));
		MockHttpSession firstLogin = login("door-staff", STAFF_TEMPORARY_PASSWORD, "/account/password");
		mockMvc.perform(post("/account/password").session(firstLogin).with(csrf())
				.param("currentPassword", STAFF_TEMPORARY_PASSWORD).param("newPassword", STAFF_PASSWORD)
				.param("confirmPassword", STAFF_PASSWORD)).andExpect(redirectedUrl("/login?passwordChanged"));
		return login("door-staff", STAFF_PASSWORD, "/check-in");
	}

	private void previewAndConfirmCheckIn(MockHttpSession staff, Guest guest) throws Exception {
		mockMvc.perform(get("/check-in/preview/guest/{id}", guest.getId()).session(staff))
				.andExpect(status().isOk()).andExpect(view().name("checkin/preview"))
				.andExpect(content().string(containsString("Indonesian Journey Guest")));
		mockMvc.perform(post("/check-in/confirm/guest/{id}", guest.getId()).session(staff).with(csrf())
				.param("guestVersion", Long.toString(reload(guest).getVersion())).param("actualCount", "2")
				.param("acceptRsvpChange", "false"))
				.andExpect(redirectedUrl("/check-in/result"));
		assertThat(checkIns.current(guest.getId())).get()
				.extracting(CheckInService.CheckInView::actualAttendeeCount, CheckInService.CheckInView::checkedInByUsername)
				.containsExactly(2, "door-staff");
	}

	private void correctCheckIn(Guest guest) throws Exception {
		long version = checkIns.current(guest.getId()).orElseThrow().version();
		mockMvc.perform(post("/admin/guests/{id}/check-in/correct", guest.getId()).session(admin).with(csrf())
				.param("checkInVersion", Long.toString(version)).param("actualCount", "1")
				.param("reason", "Companion did not arrive"))
				.andExpect(redirectedUrl("/admin/guests/" + guest.getId()));
		assertThat(checkIns.current(guest.getId())).get().extracting(CheckInService.CheckInView::actualAttendeeCount)
				.isEqualTo(1);
	}

	private void assertReportsPrintAndCsv() throws Exception {
		assertThat(reports.snapshot(null).totals().invitations()).isEqualTo(2);
		assertThat(reports.snapshot(familyId).totals().invitations()).isEqualTo(1);
		assertThat(report(get("/admin/reports").session(admin)).totals().invitations()).isEqualTo(2);
		assertThat(report(get("/admin/reports").session(admin).param("categoryId", Long.toString(familyId))).totals()
				.invitations()).isEqualTo(1);
		mockMvc.perform(get("/admin/reports/print").session(admin)).andExpect(status().isOk())
				.andExpect(view().name("admin/reports/print"))
				.andExpect(content().string(containsString("Indonesian Journey Guest")));
		mockMvc.perform(get("/admin/guests/export.csv").session(admin)).andExpect(status().isOk())
				.andExpect(content().string(containsString("Indonesian Journey Guest")));
	}

	private ReportView report(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk())
				.andExpect(view().name("admin/reports/index")).andReturn();
		return (ReportView) result.getModelAndView().getModel().get("report");
	}

	private JourneySnapshot snapshot(Guest guest, long photoId) {
		Guest current = reload(guest);
		GalleryPhoto photo = photos.findById(photoId).orElseThrow();
		return new JourneySnapshot(current.getInvitationTokenVersion(), current.getPreferredLanguage(),
				current.getDeliveryState(), current.getFirstSentAt(), current.getLastSentAt(),
				current.getLastEventReminderSentAt(), rsvps.view(current.getId()).orElseThrow(),
				checkIns.current(current.getId()).orElseThrow(), checkIns.history(current.getId()), photo.getId(),
				photo.getMainPath(), settings.getSingleton().orElseThrow().getBackgroundAudioPath());
	}

	private void close() throws Exception {
		mockMvc.perform(post("/admin/wedding/event-status/close").session(admin).with(csrf())
				.param("version", Long.toString(eventStatus.view().version())).param("confirmed", "true"))
				.andExpect(redirectedUrl("/admin/wedding/event-status?statusChanged"));
		assertThat(eventStatus.view().closed()).isTrue();
	}

	private void assertClosedPublicPages(String invitationPath) throws Exception {
		for (String language : List.of("ID", "EN")) {
			mockMvc.perform(get(invitationPath).param("language", language)).andExpect(status().isOk())
					.andExpect(view().name("guest/closed"))
					.andExpect(content().string(containsString("ID".equals(language) ? "Acara telah selesai" : "The event has ended")));
		}
	}

	private void assertClosedWritesAreBlocked(String invitationPath, JourneySnapshot snapshot) throws Exception {
		mockMvc.perform(post(invitationPath + "/rsvp").with(csrf()).param("language", "ID")
				.param("response", "HADIR").param("plannedAttendeeCount", "2").param("pin", "7890")
				.param("version", Long.toString(snapshot.rsvp().version())))
				.andExpect(status().isOk()).andExpect(view().name("guest/closed"));
		assertThatThrownBy(() -> deliveries.confirmSent(guests.findAll().stream()
				.filter(guest -> "Indonesian Journey Guest".equals(guest.getDisplayName())).findFirst().orElseThrow().getId(),
				reloadByName("Indonesian Journey Guest").getVersion(), Instant.parse("2026-08-23T03:00:00Z")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> reminders.confirmSent(reloadByName("Indonesian Journey Guest").getId(),
				reloadByName("Indonesian Journey Guest").getVersion(), ReminderKind.EVENT, familyId,
				Instant.parse("2026-08-23T03:00:00Z")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("open wedding");
		assertThatThrownBy(() -> checkIns.confirmGuest(reloadByName("Indonesian Journey Guest").getId(),
				reloadByName("Indonesian Journey Guest").getVersion(), 1, false, "door-staff"))
				.isInstanceOf(RuntimeException.class).hasMessage("CHECK_IN_CLOSED");
	}

	private void reopen() throws Exception {
		mockMvc.perform(post("/admin/wedding/event-status/reopen").session(admin).with(csrf())
				.param("version", Long.toString(eventStatus.view().version())).param("confirmed", "true"))
				.andExpect(redirectedUrl("/admin/wedding/event-status?statusChanged"));
	}

	private Guest reload(Guest guest) {
		return guests.findById(guest.getId()).orElseThrow();
	}

	private Guest reloadByName(String name) {
		return guests.findAll().stream().filter(guest -> name.equals(guest.getDisplayName())).findFirst().orElseThrow();
	}

	private String path(Guest guest) {
		return URI.create(invitationLinks.urlFor(guest)).getRawPath();
	}

	private long settingsVersion() {
		return settings.getSingleton().orElseThrow().getVersion();
	}

	private MockHttpSession login(String username, String password, String destination) throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", username).param("password", password))
				.andExpect(redirectedUrl(destination)).andReturn().getRequest().getSession(false);
	}

	private static MockMultipartFile image() throws Exception {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, 8, 8);
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		javax.imageio.ImageIO.write(image, "png", output);
		return new MockMultipartFile("image", "journey.png", "image/png", output.toByteArray());
	}

	private record JourneySnapshot(long invitationTokenVersion, MessageLanguage language, DeliveryState deliveryState,
			Instant firstSentAt, Instant lastSentAt, Instant lastEventReminderSentAt, RsvpView rsvp,
			CheckInService.CheckInView checkIn, List<CheckInService.CheckInCorrectionView> history, long photoId,
			String photoPath, String audioPath) {
	}
}
