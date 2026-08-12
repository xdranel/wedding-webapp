package myweddinginvitation.webapp.wedding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.InvitationLinkSigner;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, CalendarControllerTest.CalendarProbeConfiguration.class})
class CalendarControllerTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private GuestService guests;

	@Autowired
	private InvitationLinkSigner signer;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from guest");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false,
				calendar_downloads_enabled = true, couple_title = 'Rama & Shinta',
				opening_text_id = 'Dengan hormat', closing_text_id = 'Terima kasih',
				time_zone = 'Asia/Jakarta' where id = 1
				""");
		completeCeremony();
	}

	@Test
	void anonymousSignedGetReturnsExactCalendarBytesAndFixedDownloadHeadersWithoutPinOrMutation() throws Exception {
		Guest guest = guest();
		String invitationPath = path(signer.urlFor(guest));
		String invitationUrl = signer.urlFor(guest);
		long guestVersion = guest.getVersion();
		long weddingVersion = jdbc.queryForObject("select version from wedding_settings where id = 1", Long.class);

		mockMvc.perform(get(invitationPath + "/calendar/CEREMONY.ics").param("language", "ID"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/calendar;charset=UTF-8"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"wedding-ceremony.ics\""))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().bytes(calendar("ID", invitationUrl)));

		Guest unchanged = guests.get(guest.getId());
		assertThat(unchanged.getVersion()).isEqualTo(guestVersion);
		assertThat(jdbc.queryForObject("select version from wedding_settings where id = 1", Long.class))
				.isEqualTo(weddingVersion);
		assertThat(jdbc.queryForObject("select count(*) from rsvp", Integer.class)).isZero();
	}

	@Test
	void englishRequestProducesEnglishCalendarForTheSameSignedInvitation() throws Exception {
		Guest guest = guest();
		String invitationUrl = signer.urlFor(guest);

		mockMvc.perform(get(path(invitationUrl) + "/calendar/CEREMONY.ics").param("language", "EN"))
				.andExpect(status().isOk())
				.andExpect(content().bytes(calendar("EN", invitationUrl)));
	}

	@ParameterizedTest
	@EnumSource(UnavailableCase.class)
	void unavailableCalendarStatesReturnTheSameNeutralNotFound(UnavailableCase unavailableCase) throws Exception {
		Guest guest = guest();
		String calendarPath = path(signer.urlFor(guest)) + "/calendar/CEREMONY.ics";
		switch (unavailableCase) {
			case DISABLED -> jdbc.update("update wedding_settings set calendar_downloads_enabled = false where id = 1");
			case HIDDEN -> jdbc.update("update event_part set visible = false where event_type = 'CEREMONY'");
			case INCOMPLETE -> jdbc.update("update event_part set address_id = null where event_type = 'CEREMONY'");
			case INVALID_SIGNATURE -> calendarPath = path(signer.urlFor(guest)) + "x/calendar/CEREMONY.ics";
			case ARCHIVED -> guests.archive(guest.getId(), guest.getVersion());
			case STALE_TOKEN -> guests.regenerateInvitation(guest.getId(), guest.getVersion(), Instant.parse("2026-08-12T00:00:00Z"));
			case UNPUBLISHED -> jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");
			case CLOSED -> jdbc.update("update wedding_settings set event_closed = true where id = 1");
		}

		mockMvc.perform(get(calendarPath))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(""));
	}

	@Test
	void malformedEventTypeReturnsNeutralNotFound() throws Exception {
		Guest guest = guest();

		mockMvc.perform(get(path(signer.urlFor(guest)) + "/calendar/not-an-event.ics"))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(""));
	}

	private Guest guest() {
		return guests.create(new GuestForm("Sari", "Ibu", "ID", "+628123456789", null,
				false, MessageLanguage.ID, null), false);
	}

	private void completeCeremony() {
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, map_url)
				values ('CEREMONY', true, '2027-05-01', '08:00:00', 'Gedung Bahagia', 'Jakarta',
				'https://maps.example.test')
				""");
	}

	private String path(String url) {
		return URI.create(url).getRawPath();
	}

	private static byte[] calendar(String language, String invitationUrl) {
		return ("calendar-byte-probe-" + language.toLowerCase() + "\r\n" + invitationUrl + "\r\n").getBytes(UTF_8);
	}

	private enum UnavailableCase {
		DISABLED,
		HIDDEN,
		INCOMPLETE,
		INVALID_SIGNATURE,
		ARCHIVED,
		STALE_TOKEN,
		UNPUBLISHED,
		CLOSED
	}

	@TestConfiguration
	static class CalendarProbeConfiguration {
		@Bean
		@Primary
		CalendarService calendarService() {
			CalendarService real = new CalendarService();
			return new CalendarService() {
				@Override
				public Optional<CalendarFile> create(WeddingPreview preview, EventType type, String language,
						String invitationUrl, String timeZone) {
					if (real.create(preview, type, language, invitationUrl, timeZone).isEmpty()) return Optional.empty();
					byte[] content = calendar(language, invitationUrl);
					return Optional.of(new CalendarFile("wedding-" + type.name().toLowerCase() + ".ics", content));
				}
			};
		}
	}
}
