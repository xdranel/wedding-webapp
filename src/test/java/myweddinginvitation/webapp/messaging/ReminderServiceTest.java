package myweddinginvitation.webapp.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLDecoder;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import(MySqlTestConfiguration.class)
class ReminderServiceTest {
	private static final Instant FIRST = Instant.parse("2026-08-12T06:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-08-12T07:00:00Z");

	@Autowired ReminderService reminders;
	@Autowired GuestService guestService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvps;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManagerFactory entityManagerFactory;

	private int phoneSuffix;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from rsvp");
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("delete from event_part");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', rsvp_deadline = '2030-08-12 12:00:00',
				couple_title = 'Rama & Shinta', default_phone_country = 'ID', event_closed = false where id = 1
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
				update message_template set body = 'ACARA {{guest_name}} {{ceremony_date}} {{ceremony_location}} {{invitation_link}}'
				where message_type = 'EVENT_REMINDER' and language = 'ID'
				""");
		jdbc.update("""
				update message_template set body = 'EVENT {{guest_name}} {{ceremony_date}} {{ceremony_location}} {{invitation_link}}'
				where message_type = 'EVENT_REMINDER' and language = 'EN'
				""");
		phoneSuffix = 0;
	}

	@Test
	void rsvpQueueContainsOnlyGuestsWithoutRsvpAndOrdersNeverSentBeforeSent() {
		Guest later = guest("zulu", null, MessageLanguage.ID);
		Guest first = guest("Alpha", null, MessageLanguage.ID);
		Guest sent = guest("Aaron", null, MessageLanguage.ID);
		Guest attending = guest("Already replied", null, MessageLanguage.ID);
		rsvp(attending, AttendanceResponse.HADIR);
		reminders.confirmSent(sent.getId(), sent.getVersion(), ReminderKind.RSVP, null, FIRST);

		assertThat(reminders.queue(ReminderKind.RSVP, null))
				.extracting(ReminderGuestView::id, ReminderGuestView::lastSentAt)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(first.getId(), null),
						org.assertj.core.groups.Tuple.tuple(later.getId(), null),
						org.assertj.core.groups.Tuple.tuple(sent.getId(), FIRST));
	}

	@Test
	void eventQueueContainsOnlyHadirGuests() {
		completeVisibleEvent();
		Guest hadir = guest("Hadir", null, MessageLanguage.ID);
		Guest declined = guest("Tidak hadir", null, MessageLanguage.ID);
		Guest unanswered = guest("Belum menjawab", null, MessageLanguage.ID);
		rsvp(hadir, AttendanceResponse.HADIR);
		rsvp(declined, AttendanceResponse.TIDAK_HADIR);

		assertThat(reminders.queue(ReminderKind.EVENT, null))
				.extracting(ReminderGuestView::id).containsExactly(hadir.getId());
	}

	@Test
	void queueRequiresPublishedWeddingAndReminderContent() {
		Guest guest = guest("Ready", null, MessageLanguage.ID);

		jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");
		assertThatThrownBy(() -> reminders.queue(ReminderKind.RSVP, null)).isInstanceOf(IllegalStateException.class);
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED', rsvp_deadline = null where id = 1");
		assertThatThrownBy(() -> reminders.queue(ReminderKind.RSVP, null)).isInstanceOf(IllegalStateException.class);
		jdbc.update("update wedding_settings set rsvp_deadline = '2030-08-12 12:00:00' where id = 1");
		assertThat(reminders.queue(ReminderKind.RSVP, null)).extracting(ReminderGuestView::id).containsExactly(guest.getId());
		assertThatThrownBy(() -> reminders.queue(ReminderKind.EVENT, null)).isInstanceOf(IllegalStateException.class);
		completeVisibleEvent();
		jdbc.update("update event_part set map_url = 'not-a-url'");
		assertThatThrownBy(() -> reminders.queue(ReminderKind.EVENT, null)).isInstanceOf(IllegalStateException.class);
		jdbc.update("update event_part set map_url = 'https://maps.example/event'");
		rsvp(guest, AttendanceResponse.HADIR);
		assertThat(reminders.queue(ReminderKind.EVENT, null)).extracting(ReminderGuestView::id).containsExactly(guest.getId());
		jdbc.update("update event_part set visible = false");
		assertThatThrownBy(() -> reminders.queue(ReminderKind.EVENT, null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void closedWeddingRejectsReminderQueue() {
		guest("Ready", null, MessageLanguage.ID);
		jdbc.update("update wedding_settings set event_closed = true where id = 1");

		assertThatThrownBy(() -> reminders.queue(ReminderKind.RSVP, null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void closedWeddingRejectsWhatsappOpenWithoutMutation() {
		Guest guest = guest("Ready", null, MessageLanguage.ID);
		jdbc.update("update wedding_settings set event_closed = true where id = 1");

		assertThatThrownBy(() -> reminders.whatsappUri(guest.getId(), ReminderKind.RSVP, MessageLanguage.ID))
				.isInstanceOf(IllegalStateException.class);
		assertThat(reload(guest.getId()).getLastRsvpReminderSentAt()).isNull();
	}

	@Test
	void closedWeddingRejectsConfirmationWithoutMutation() {
		Guest guest = guest("Ready", null, MessageLanguage.ID);
		jdbc.update("update wedding_settings set event_closed = true where id = 1");

		assertThatThrownBy(() -> reminders.confirmSent(
				guest.getId(), guest.getVersion(), ReminderKind.RSVP, null, FIRST))
				.isInstanceOf(IllegalStateException.class);
		assertThat(reload(guest.getId()).getLastRsvpReminderSentAt()).isNull();
	}

	@Test
	void queueRejectsArchivedAndUnusableNumbers() {
		Guest archived = guest("Archived", null, MessageLanguage.ID);
		Guest valid = guest("Valid", null, MessageLanguage.ID);
		guestService.archive(archived.getId(), archived.getVersion());
		for (String malformed : List.of("", "+0", "+000", "+６２８１２３４５６７８９０")) {
			jdbc.update("update guest set normalized_whatsapp_number = ? where id = ?", malformed, valid.getId());

			assertThat(reminders.queue(ReminderKind.RSVP, null)).isEmpty();
			assertThatThrownBy(() -> reminders.whatsappUri(valid.getId(), ReminderKind.RSVP, MessageLanguage.ID))
					.isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void queueFiltersCategoryAndOrdersNamesCaseInsensitivelyThenIds() {
		jdbc.update("insert into guest_category (display_name, normalized_name) values ('Family', 'family')");
		jdbc.update("insert into guest_category (display_name, normalized_name) values ('Friends', 'friends')");
		long familyId = jdbc.queryForObject("select id from guest_category where normalized_name = 'family'", Long.class);
		long friendsId = jdbc.queryForObject("select id from guest_category where normalized_name = 'friends'", Long.class);
		Guest first = guest("alex", familyId, MessageLanguage.ID);
		Guest second = guest("Alex", familyId, MessageLanguage.ID);
		guest("Other", friendsId, MessageLanguage.ID);

		assertThat(reminders.queue(ReminderKind.RSVP, familyId))
				.extracting(ReminderGuestView::id).containsExactly(first.getId(), second.getId());
	}

	@Test
	void openingWhatsappRendersRequestedLanguageAndPersonalizedLinkWithoutQrOrMutation() {
		Guest guest = guest("Sari", null, MessageLanguage.ID);

		URI uri = reminders.whatsappUri(guest.getId(), ReminderKind.RSVP, MessageLanguage.EN);

		assertThat(uri).hasScheme("https").hasHost("wa.me").hasPath("/6281200000001");
		String message = URLDecoder.decode(uri.getRawQuery().substring("text=".length()), UTF_8);
		assertThat(message).contains("EN Sari", "2030-08-12T12:00", "https://invite.example/i/", "?language=EN")
				.doesNotContain("/qr.png", "/qr-download.png", "data:image");
		String indonesian = URLDecoder.decode(reminders.whatsappUri(guest.getId(), ReminderKind.RSVP, MessageLanguage.ID)
				.getRawQuery().substring("text=".length()), UTF_8);
		assertThat(indonesian).contains("ID Sari", "?language=ID");
		assertThat(reload(guest.getId()))
				.extracting(Guest::getPreferredLanguage, Guest::getLastRsvpReminderSentAt, Guest::getLastEventReminderSentAt)
				.containsExactly(MessageLanguage.ID, null, null);
	}

	@Test
	void confirmationResendsOnlyItsTimestampAndReturnsNextEligibleGuest() {
		Guest first = guest("First", null, MessageLanguage.ID);
		Guest second = guest("Second", null, MessageLanguage.ID);

		Long next = reminders.confirmSent(first.getId(), first.getVersion(), ReminderKind.RSVP, null, FIRST);
		Guest once = reload(first.getId());
		Long afterResend = reminders.confirmSent(once.getId(), once.getVersion(), ReminderKind.RSVP, null, SECOND);

		assertThat(reload(first.getId()))
				.extracting(Guest::getLastRsvpReminderSentAt, Guest::getLastEventReminderSentAt)
				.containsExactly(SECOND, null);
		assertThat(next).isEqualTo(second.getId());
		assertThat(afterResend).isEqualTo(second.getId());
	}

	@Test
	void eventConfirmationChangesOnlyTheEventReminderTimestamp() {
		completeVisibleEvent();
		Guest guest = guest("Attending", null, MessageLanguage.ID);
		rsvp(guest, AttendanceResponse.HADIR);

		reminders.confirmSent(guest.getId(), guest.getVersion(), ReminderKind.EVENT, null, FIRST);

		assertThat(reload(guest.getId()))
				.extracting(Guest::getLastRsvpReminderSentAt, Guest::getLastEventReminderSentAt)
				.containsExactly(null, FIRST);
	}

	@Test
	void confirmationRejectsStaleGuestAndChangedRsvpWithoutMutatingTimestamp() {
		Guest guest = guest("Current", null, MessageLanguage.ID);
		Guest stale = guest("Stale", null, MessageLanguage.ID);
		reminders.confirmSent(stale.getId(), stale.getVersion(), ReminderKind.RSVP, null, FIRST);

		assertThatThrownBy(() -> reminders.confirmSent(stale.getId(), stale.getVersion(), ReminderKind.RSVP, null, SECOND))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(reload(stale.getId()).getLastRsvpReminderSentAt()).isEqualTo(FIRST);
		rsvp(guest, AttendanceResponse.HADIR);

		assertThatThrownBy(() -> reminders.confirmSent(guest.getId(), guest.getVersion(), ReminderKind.RSVP, null, FIRST))
				.isInstanceOf(IllegalStateException.class);
		assertThat(reload(guest.getId()).getLastRsvpReminderSentAt()).isNull();
	}

	@Test
	void confirmationRevalidatesPublishedDeadlineAndVisibleEventContent() {
		Guest rsvpGuest = guest("RSVP", null, MessageLanguage.ID);
		reminders.whatsappUri(rsvpGuest.getId(), ReminderKind.RSVP, MessageLanguage.ID);
		jdbc.update("update wedding_settings set publication_state = 'DRAFT' where id = 1");
		assertThatThrownBy(() -> reminders.confirmSent(rsvpGuest.getId(), rsvpGuest.getVersion(), ReminderKind.RSVP, null, FIRST))
				.isInstanceOf(IllegalStateException.class);
		jdbc.update("update wedding_settings set publication_state = 'PUBLISHED', rsvp_deadline = null where id = 1");
		assertThatThrownBy(() -> reminders.confirmSent(rsvpGuest.getId(), rsvpGuest.getVersion(), ReminderKind.RSVP, null, FIRST))
				.isInstanceOf(IllegalStateException.class);
		assertThat(reload(rsvpGuest.getId()).getLastRsvpReminderSentAt()).isNull();

		jdbc.update("update wedding_settings set rsvp_deadline = '2030-08-12 12:00:00' where id = 1");
		completeVisibleEvent();
		Guest eventGuest = guest("Event", null, MessageLanguage.ID);
		rsvp(eventGuest, AttendanceResponse.HADIR);
		reminders.whatsappUri(eventGuest.getId(), ReminderKind.EVENT, MessageLanguage.ID);
		jdbc.update("update event_part set visible = false");
		assertThatThrownBy(() -> reminders.confirmSent(eventGuest.getId(), eventGuest.getVersion(), ReminderKind.EVENT, null, FIRST))
				.isInstanceOf(IllegalStateException.class);
		assertThat(reload(eventGuest.getId()).getLastEventReminderSentAt()).isNull();
	}

	@Test
	void confirmationKeepsCategoryFilterWhenSelectingTheNextGuest() {
		long familyId = category("Family", "family");
		long friendsId = category("Friends", "friends");
		Guest selected = guest("Selected", familyId, MessageLanguage.ID);
		Guest nextFamily = guest("Zed", familyId, MessageLanguage.ID);
		guest("Alpha friend", friendsId, MessageLanguage.ID);

		Long next = reminders.confirmSent(selected.getId(), selected.getVersion(), ReminderKind.RSVP, familyId, FIRST);

		assertThat(next).isEqualTo(nextFamily.getId());
	}

	@Test
	void queueAllowsTwoThousandGuestsAndRejectsMoreBeforeBulkRsvpLoading() {
		insertGuests(2_000);

		assertThat(reminders.queue(ReminderKind.RSVP, null)).isEmpty();

		insertGuests(1);
		assertThatThrownBy(() -> reminders.queue(ReminderKind.RSVP, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2,000");
	}

	@Test
	void queueFetchesRsvpsAndCategoriesWithoutPerGuestQueries() {
		long categoryId = category("Family", "family");
		for (int index = 0; index < 3; index++) guest("Guest " + index, categoryId, MessageLanguage.ID);
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		assertThat(reminders.queue(ReminderKind.RSVP, null)).hasSize(3);

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
	}

	private Guest guest(String name, Long categoryId, MessageLanguage language) {
		phoneSuffix++;
		return guestService.create(new GuestForm(name, "Ibu", "ID", "08120000%04d".formatted(phoneSuffix),
				categoryId, false, language, null), false);
	}

	private void rsvp(Guest guest, AttendanceResponse response) {
		RsvpView current = rsvps.view(guest.getId()).orElse(null);
		rsvps.correctByAdmin(guest.getId(), current == null ? -1 : current.version(),
				new RsvpSubmission(response, 1, null, false, null), "test-admin");
	}

	private Guest reload(long id) {
		return guests.findById(id).orElseThrow();
	}

	private long category(String name, String normalized) {
		jdbc.update("insert into guest_category (display_name, normalized_name) values (?, ?)", name, normalized);
		return jdbc.queryForObject("select id from guest_category where normalized_name = ?", Long.class, normalized);
	}

	private void insertGuests(int count) {
		int start = jdbc.queryForObject("select count(*) from guest", Integer.class);
		jdbc.batchUpdate("""
				insert into guest (public_id, display_name, salutation, normalized_whatsapp_number)
				values (uuid_to_bin(uuid()), ?, 'Ibu', ?)
				""", java.util.stream.IntStream.range(start, start + count)
				.mapToObj(index -> new Object[] { "Bulk %04d".formatted(index), "+000%012d".formatted(index) }).toList());
	}

	private void completeVisibleEvent() {
		jdbc.update("""
				insert into event_part (event_type, visible, event_date, start_time, venue_name, address_id, map_url, version)
				values ('CEREMONY', true, '2030-08-12', '10:00:00', 'Gedung', 'Jakarta', 'https://maps.example/event', 0)
				""");
	}
}
