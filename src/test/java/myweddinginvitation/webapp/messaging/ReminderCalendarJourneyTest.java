package myweddinginvitation.webapp.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.CheckInQrSigner;
import myweddinginvitation.webapp.rsvp.QrReference;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, ReminderCalendarJourneyTest.MutableClockConfiguration.class})
class ReminderCalendarJourneyTest {
	private static final String PASSWORD = "Test-Password-2026";
	private static final Instant FIRST = Instant.parse("2026-08-12T06:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-08-12T07:00:00Z");

	@Autowired MockMvc mockMvc;
	@Autowired MutableClock clock;
	@Autowired ReminderService reminders;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired CheckInService checkIns;
	@Autowired InvitationLinkSigner invitationLinks;
	@Autowired CheckInQrSigner qrSigner;
	@Autowired WeddingSettingsRepository settings;
	@Autowired UserAccountRepository accounts;
	@Autowired AccountSecurityService accountSecurity;
	@Autowired JdbcTemplate jdbc;

	private MockHttpSession adminSession;
	private int phoneSuffix;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false,
				calendar_downloads_enabled = false, couple_title = 'Rama & Shinta',
				opening_text_id = 'Dengan hormat', opening_text_en = 'Welcome',
				closing_text_id = 'Terima kasih', closing_text_en = 'Thank you',
				time_zone = 'Asia/Jakarta', rsvp_deadline = '2030-08-12 12:00:00',
				default_phone_country = 'ID', accent_color = '#7A5C48', font_preset = 'CLASSIC',
				greetings_enabled = true, private_organizer_note_enabled = false, version = 0
				where id = 1
				""");
		jdbc.update("""
				update message_template set body = 'ID {{guest_name}} {{rsvp_deadline}} {{invitation_link}}'
				where message_type = 'RSVP_REMINDER' and language = 'ID'
				""");
		jdbc.update("""
				update message_template set body = 'EN {{guest_name}} {{rsvp_deadline}} {{invitation_link}}'
				where message_type = 'RSVP_REMINDER' and language = 'EN'
				""");
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name,
					address_id, address_en, map_url, version)
				values
					('CEREMONY', true, '2027-05-01', '08:00:00', 'Gedung Bahagia',
						'Jakarta', 'Jakarta', 'https://maps.example.test/ceremony', 0),
					('RECEPTION', true, '2027-05-01', '18:00:00', 'Ballroom',
						'Jakarta', 'Jakarta', 'https://maps.example.test/reception', 0)
				""");
		accounts.deleteAll();
		accounts.save(new UserAccount("admin", "{noop}" + PASSWORD, AccountRole.ADMIN));
		accountSecurity.changePassword("admin", PASSWORD, PASSWORD);
		adminSession = login();
		clock.set(FIRST);
		phoneSuffix = 0;
	}

	@Test
	void administratorRunsRemindersAndGuestsDownloadCalendarsWithoutUnrelatedStateChanges() throws Exception {
		long familyId = category("Family", "family");
		long friendsId = category("Friends", "friends");
		Guest resend = guest("Aaron Resend", familyId, MessageLanguage.ID);
		Guest next = guest("Bianca Pending", familyId, MessageLanguage.EN);
		Guest attending = guest("Citra Hadir", familyId, MessageLanguage.EN);
		Guest declined = guest("Dewi Tidak Hadir", familyId, MessageLanguage.ID);
		Guest friend = guest("A Friend", friendsId, MessageLanguage.ID);
		rsvp(attending, AttendanceResponse.HADIR);
		rsvp(declined, AttendanceResponse.TIDAK_HADIR);
		RsvpView attendingRsvp = rsvps.view(attending.getId()).orElseThrow();
		RsvpView declinedRsvp = rsvps.view(declined.getId()).orElseThrow();
		String qrPayload = qrSigner.payload(attending.getPublicId(), attending.getInvitationTokenVersion());

		assertThat(reminders.queue(ReminderKind.RSVP, familyId))
				.extracting(ReminderGuestView::id).containsExactly(resend.getId(), next.getId());
		assertThat(reminders.queue(ReminderKind.RSVP, friendsId))
				.extracting(ReminderGuestView::id).containsExactly(friend.getId());
		assertThat(reminders.queue(ReminderKind.EVENT, familyId))
				.extracting(ReminderGuestView::id).containsExactly(attending.getId());

		assertWhatsapp(resend, "ID", "ID Aaron Resend");
		assertWhatsapp(resend, "EN", "EN Aaron Resend");
		assertThat(reload(resend))
				.extracting(Guest::getPreferredLanguage, Guest::getLastRsvpReminderSentAt,
						Guest::getLastEventReminderSentAt)
				.containsExactly(MessageLanguage.ID, null, null);

		confirm(resend, familyId, next, FIRST);
		assertThat(reminders.queue(ReminderKind.RSVP, familyId))
				.extracting(ReminderGuestView::id).containsExactly(next.getId(), resend.getId());

		clock.set(SECOND);
		confirm(reload(resend), familyId, next, SECOND);
		assertThat(reload(resend).getLastRsvpReminderSentAt()).isEqualTo(SECOND);

		rsvp(next, AttendanceResponse.HADIR);
		RsvpView changedRsvp = rsvps.view(next.getId()).orElseThrow();
		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/confirm-sent", "RSVP", next.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(next.getVersion()))
				.param("categoryId", Long.toString(familyId)))
				.andExpect(redirectedUrl("/admin/reminders?kind=RSVP&categoryId=" + familyId))
				.andExpect(flash().attributeExists("reminderError"));
		assertThat(reload(next).getLastRsvpReminderSentAt()).isNull();
		assertThat(rsvps.view(next.getId())).contains(changedRsvp);

		enableCalendars();
		Guest calendarGuest = reload(attending);
		String invitationUrl = invitationLinks.urlFor(calendarGuest);
		String oldPath = URI.create(invitationUrl).getRawPath();
		String ceremony = calendar(oldPath, "CEREMONY", "ID", "wedding-ceremony.ics");
		String reception = calendar(oldPath, "RECEPTION", "EN", "wedding-reception.ics");
		assertImportable(ceremony, "SUMMARY:Akad - Rama & Shinta", "DTSTART;TZID=Asia/Jakarta:20270501T080000",
				"DTEND;TZID=Asia/Jakarta:20270501T090000", invitationUrl);
		assertImportable(reception, "SUMMARY:Reception - Rama & Shinta", "DTSTART;TZID=Asia/Jakarta:20270501T180000",
				"DTEND;TZID=Asia/Jakarta:20270501T210000", invitationUrl);
		assertThat(uid(ceremony)).isNotEqualTo(uid(reception));

		assertThat(rsvps.view(resend.getId())).isEmpty();
		assertThat(rsvps.view(next.getId())).contains(changedRsvp);
		assertThat(rsvps.view(attending.getId())).contains(attendingRsvp);
		assertThat(rsvps.view(declined.getId())).contains(declinedRsvp);
		assertThat(checkIns.currentFor(List.of(resend.getId(), next.getId(), attending.getId(), declined.getId(), friend.getId())))
				.isEmpty();
		assertThat(reload(attending))
				.extracting(Guest::getInvitationTokenVersion, Guest::getFailedPinCount, Guest::getPinLockedUntil)
				.containsExactly(attending.getInvitationTokenVersion(), 0, null);
		assertThat(qrSigner.verify(qrPayload)).get()
				.extracting(QrReference::publicId, QrReference::tokenVersion)
				.containsExactly(attending.getPublicId(), attending.getInvitationTokenVersion());

		Guest regenerated = guestService.regenerateInvitation(calendarGuest.getId(), calendarGuest.getVersion(), SECOND);
		assertThat(regenerated.getInvitationTokenVersion()).isEqualTo(attending.getInvitationTokenVersion() + 1);
		mockMvc.perform(get(oldPath + "/calendar/CEREMONY.ics"))
				.andExpect(status().isNotFound());
		assertThat(rsvps.view(attending.getId())).contains(attendingRsvp);
		assertThat(checkIns.current(attending.getId())).isEmpty();
		assertThat(reload(attending))
				.extracting(Guest::getFailedPinCount, Guest::getPinLockedUntil)
				.containsExactly(0, null);
	}

	private void assertWhatsapp(Guest guest, String language, String text) throws Exception {
		MvcResult result = mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/open-whatsapp", "RSVP", guest.getId())
				.session(adminSession).with(csrf()).param("language", language))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", startsWith("https://wa.me/")))
				.andReturn();
		URI uri = URI.create(result.getResponse().getRedirectedUrl());
		String message = URLDecoder.decode(uri.getRawQuery().substring("text=".length()), UTF_8);
		assertThat(message).contains(text, invitationLinks.urlFor(reload(guest)));
	}

	private void confirm(Guest guest, long categoryId, Guest next, Instant sentAt) throws Exception {
		clock.set(sentAt);
		mockMvc.perform(post("/admin/reminders/{kind}/{guestId}/confirm-sent", "RSVP", guest.getId())
				.session(adminSession).with(csrf()).param("version", Long.toString(guest.getVersion()))
				.param("categoryId", Long.toString(categoryId)))
				.andExpect(redirectedUrl("/admin/reminders?kind=RSVP&categoryId=" + categoryId + "#guest-" + next.getId()));
		assertThat(reload(guest).getLastRsvpReminderSentAt()).isEqualTo(sentAt);
	}

	private void enableCalendars() throws Exception {
		mockMvc.perform(post("/admin/wedding/settings").session(adminSession).with(csrf())
				.param("version", Long.toString(settings.getSingleton().orElseThrow().getVersion()))
				.param("coupleTitle", "Rama & Shinta")
				.param("openingTextId", "Dengan hormat").param("openingTextEn", "Welcome")
				.param("closingTextId", "Terima kasih").param("closingTextEn", "Thank you")
				.param("timeZone", "Asia/Jakarta").param("rsvpDeadline", "2030-08-12T12:00")
				.param("defaultPhoneCountry", "ID").param("accentColor", "#7A5C48")
				.param("fontPreset", "CLASSIC").param("greetingsEnabled", "true")
				.param("calendarDownloadsEnabled", "true"))
				.andExpect(redirectedUrl("/admin/wedding/settings?settingsSaved"));
		assertThat(settings.getSingleton().orElseThrow().isCalendarDownloadsEnabled()).isTrue();
	}

	private String calendar(String invitationPath, String type, String language, String filename) throws Exception {
		return mockMvc.perform(get(invitationPath + "/calendar/" + type + ".ics").param("language", language))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""))
				.andReturn().getResponse().getContentAsString(UTF_8);
	}

	private void assertImportable(String body, String summary, String start, String end, String invitationUrl) {
		assertThat(body)
				.startsWith("BEGIN:VCALENDAR\r\n")
				.endsWith("END:VCALENDAR\r\n")
				.contains("BEGIN:VTIMEZONE\r\n", "TZID:Asia/Jakarta\r\n", "END:VTIMEZONE\r\n",
						"BEGIN:VEVENT\r\n", "UID:", summary, start, end, "END:VEVENT\r\n")
				.doesNotContain("BEGIN:VALARM", "\r\r", "\n\n");
		assertThat(body.replace("\r\n ", "")).contains("URL:" + invitationUrl);
		assertThat(body.replace("\r\n", "")).doesNotContain("\r", "\n");
	}

	private String uid(String calendar) {
		return calendar.lines().filter(line -> line.startsWith("UID:")).findFirst().orElseThrow();
	}

	private void rsvp(Guest guest, AttendanceResponse response) {
		RsvpView current = rsvps.view(guest.getId()).orElse(null);
		rsvps.correctByAdmin(guest.getId(), current == null ? -1 : current.version(),
				new RsvpSubmission(response, response == AttendanceResponse.HADIR ? 1 : 0,
						null, false, null), "admin");
	}

	private Guest guest(String name, long categoryId, MessageLanguage language) {
		phoneSuffix++;
		return guestService.create(new GuestForm(name, "Ibu", "ID", "08120000%04d".formatted(phoneSuffix),
				categoryId, false, language, null), false);
	}

	private Guest reload(Guest guest) {
		return guests.findById(guest.getId()).orElseThrow();
	}

	private long category(String name, String normalized) {
		jdbc.update("insert into guest_category (display_name, normalized_name) values (?, ?)", name, normalized);
		return jdbc.queryForObject("select id from guest_category where normalized_name = ?", Long.class, normalized);
	}

	private MockHttpSession login() throws Exception {
		return (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
				.param("username", "admin").param("password", PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession(false);
	}

	static final class MutableClock extends Clock {
		private Instant current;

		MutableClock(Instant current) {
			this.current = current;
		}

		void set(Instant current) {
			this.current = current;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(current, zone);
		}

		@Override
		public Instant instant() {
			return current;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MutableClockConfiguration {
		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(FIRST);
		}
	}
}
