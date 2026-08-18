package myweddinginvitation.webapp.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.NoSuchElementException;

import jakarta.persistence.EntityManagerFactory;
import myweddinginvitation.webapp.checkin.CheckInRepository;
import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestCategoryService;
import myweddinginvitation.webapp.guest.GuestForm;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.guest.GuestService;
import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import(MySqlTestConfiguration.class)
class ReportServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

	@Autowired ReportService reports;
	@Autowired GuestService guestService;
	@Autowired GuestCategoryService categoryService;
	@Autowired GuestRepository guests;
	@Autowired RsvpService rsvpService;
	@Autowired CheckInService checkInService;
	@Autowired CheckInRepository checkIns;
	@Autowired EntityManagerFactory entityManagerFactory;
	@Autowired JdbcTemplate jdbc;

	private long familyId;
	private long friendsId;
	private long emptyId;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from check_in_correction");
		jdbc.update("delete from check_in");
		jdbc.update("delete from rsvp");
		jdbc.update("delete from guest");
		jdbc.update("delete from guest_category");
		jdbc.update("""
				update wedding_settings set publication_state = 'PUBLISHED', event_closed = false,
					greetings_enabled = true, rsvp_deadline = '2030-08-18 00:00:00' where id = 1
				""");
		categoryService.create("Family");
		categoryService.create("Friends");
		categoryService.create("Empty");
		familyId = categoryService.findAll().stream().filter(c -> c.getDisplayName().equals("Family"))
				.findFirst().orElseThrow().getId();
		friendsId = categoryService.findAll().stream().filter(c -> c.getDisplayName().equals("Friends"))
				.findFirst().orElseThrow().getId();
		emptyId = categoryService.findAll().stream().filter(c -> c.getDisplayName().equals("Empty"))
				.findFirst().orElseThrow().getId();
	}

	@Test
	void calculatesActiveCurrentStateByCategoryAndPrintRows() {
		Guest alpha = guest("Alpha", friendsId, true);
		Guest bravo = guest("Bravo", familyId, false);
		Guest charlie = guest("Charlie", null, false);
		Guest delta = guest("Delta", friendsId, false);
		Guest echo = guest("Echo", friendsId, false);
		Guest archived = guest("Archived", friendsId, true);

		rsvp(alpha, AttendanceResponse.HADIR, 2, "Congratulations", true);
		rsvp(bravo, AttendanceResponse.TIDAK_HADIR, 0, null, false);
		rsvp(delta, AttendanceResponse.HADIR, 1, null, false);
		rsvp(echo, AttendanceResponse.HADIR, 1, null, false);
		rsvp(archived, AttendanceResponse.HADIR, 2, "Archived greeting", true);
		guestService.confirmSent(alpha.getId(), alpha.getVersion(), NOW);
		guestService.confirmSent(delta.getId(), delta.getVersion(), NOW);
		jdbc.update("update guest set last_rsvp_reminder_sent_at = ? where id = ?", NOW, charlie.getId());
		jdbc.update("update guest set last_event_reminder_sent_at = ? where id = ?", NOW, bravo.getId());
		checkInService.confirmGuest(alpha.getId(), guest(alpha.getId()).getVersion(), 2, false, "test-admin");
		var alphaCheckIn = checkIns.findByGuestId(alpha.getId()).orElseThrow();
		checkInService.correct(alpha.getId(), alphaCheckIn.getVersion(), 1, "Corrected", "test-admin");
		checkInService.confirmGuest(echo.getId(), guest(echo.getId()).getVersion(), 1, false, "test-admin");
		var echoCheckIn = checkIns.findByGuestId(echo.getId()).orElseThrow();
		checkInService.cancel(echo.getId(), echoCheckIn.getVersion(), "Cancelled", "test-admin");
		guestService.archive(archived.getId(), guest(archived.getId()).getVersion());

		ReportView all = reports.snapshot(null);
		assertThat(all.totals()).isEqualTo(new ReportMetrics(5, 6, 3, 1, 1, 4, 1, 1, 2, 3,
				2, 3, 1, 4, 1, 4, 1));
		assertThat(all.categories()).extracting(ReportCategoryView::categoryName)
				.containsExactly("Family", "Friends", "Uncategorized");
		assertThat(all.categories().get(1).metrics()).isEqualTo(new ReportMetrics(3, 4, 3, 0, 0, 4, 1, 1,
				2, 3, 2, 1, 0, 3, 0, 3, 1));

		ReportView friends = reports.snapshot(friendsId);
		assertThat(friends.totals()).isEqualTo(all.categories().get(1).metrics());
		assertThat(reports.snapshot(emptyId).totals()).isEqualTo(ReportMetrics.empty());
		assertThatThrownBy(() -> reports.snapshot(999_999L)).isInstanceOf(NoSuchElementException.class);

		assertThat(reports.printRows(null)).extracting(ReportPrintRow::displayName)
				.containsExactly("Alpha", "Bravo", "Charlie", "Delta", "Echo");
		ReportPrintRow alphaRow = reports.printRows(friendsId).getFirst();
		assertThat(alphaRow.displayName()).isEqualTo("Alpha");
		assertThat(alphaRow.categoryName()).isEqualTo("Friends");
		assertThat(alphaRow.rsvpResponse()).isEqualTo(AttendanceResponse.HADIR);
		assertThat(alphaRow.plannedPeople()).isEqualTo(2);
		assertThat(alphaRow.checkedIn()).isTrue();
		assertThat(alphaRow.actualPeople()).isEqualTo(1);
		assertThat(alphaRow.checkedInAt()).isNotNull();
	}

	@Test
	void keepsPreparedStatementCountBoundedAsGuestCountChanges() {
		Guest alpha = guest("Alpha", friendsId, false);
		Guest bravo = guest("Bravo", familyId, false);
		rsvp(alpha, AttendanceResponse.HADIR, 1, null, false);
		rsvp(bravo, AttendanceResponse.TIDAK_HADIR, 0, null, false);
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		reports.snapshot(null);
		long initialStatements = statistics.getPrepareStatementCount();

		guest("Charlie", null, false);
		statistics.clear();
		reports.snapshot(null);

		assertThat(initialStatements).isLessThanOrEqualTo(4);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(initialStatements);
	}

	private Guest guest(String name, Long categoryId, boolean plusOne) {
		return guestService.create(new GuestForm(name, "Guest", "ID", "+628123%05d".formatted(guests.count() + 1),
				categoryId, plusOne, MessageLanguage.ID, null), false);
	}

	private Guest guest(long id) {
		return guestService.get(id);
	}

	private void rsvp(Guest guest, AttendanceResponse response, int plannedPeople, String greeting, boolean consent) {
		rsvpService.submitGuest(guest.getId(), -1,
				new RsvpSubmission(response, plannedPeople, greeting, consent, null));
	}
}
