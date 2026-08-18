package myweddinginvitation.webapp.reporting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import myweddinginvitation.webapp.checkin.CheckIn;
import myweddinginvitation.webapp.checkin.CheckInRepository;
import myweddinginvitation.webapp.guest.DeliveryState;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestCategory;
import myweddinginvitation.webapp.guest.GuestCategoryRepository;
import myweddinginvitation.webapp.guest.GuestRepository;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.GreetingModerationState;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
	private static final int MAX_GUESTS = 2_000;
	private static final String UNCATEGORIZED = "Uncategorized";

	private final GuestRepository guests;
	private final GuestCategoryRepository categories;
	private final RsvpRepository rsvps;
	private final CheckInRepository checkIns;

	public ReportService(GuestRepository guests, GuestCategoryRepository categories,
			RsvpRepository rsvps, CheckInRepository checkIns) {
		this.guests = guests;
		this.categories = categories;
		this.rsvps = rsvps;
		this.checkIns = checkIns;
	}

	@Transactional(readOnly = true)
	public ReportView snapshot(Long categoryId) {
		return scan(categoryId).view();
	}

	@Transactional(readOnly = true)
	public List<ReportPrintRow> printRows(Long categoryId) {
		return scan(categoryId).rows();
	}

	private Scan scan(Long categoryId) {
		if (categoryId != null && !categories.existsById(categoryId)) throw new NoSuchElementException("Category not found");
		// ponytail: bounded single-wedding scan; replace with aggregate SQL only if the documented 2,000-guest ceiling changes.
		List<Guest> activeGuests = guests.findAllActiveForReport(PageRequest.of(0, MAX_GUESTS + 1));
		if (activeGuests.size() > MAX_GUESTS) throw new IllegalStateException("Reports support at most 2,000 active guests.");
		List<Guest> selectedGuests = activeGuests.stream()
				.filter(guest -> categoryId == null || Objects.equals(categoryId, categoryId(guest)))
				.toList();
		if (selectedGuests.isEmpty()) return new Scan(new ReportView(ReportMetrics.empty(), List.of()), List.of());

		List<Long> guestIds = selectedGuests.stream().map(Guest::getId).toList();
		Map<Long, Rsvp> rsvpByGuestId = rsvps.findByGuestIdIn(guestIds).stream()
				.collect(Collectors.toMap(rsvp -> rsvp.getGuest().getId(), rsvp -> rsvp));
		Map<Long, CheckIn> checkInByGuestId = checkIns.findByGuestIdIn(guestIds).stream()
				.collect(Collectors.toMap(checkIn -> checkIn.getGuest().getId(), checkIn -> checkIn));

		Metrics total = new Metrics();
		Map<Category, Metrics> byCategory = new HashMap<>();
		List<ReportPrintRow> rows = selectedGuests.stream().map(guest -> {
			Rsvp rsvp = rsvpByGuestId.get(guest.getId());
			CheckIn checkIn = checkInByGuestId.get(guest.getId());
			total.add(guest, rsvp, checkIn);
			byCategory.computeIfAbsent(category(guest), ignored -> new Metrics()).add(guest, rsvp, checkIn);
			return new ReportPrintRow(guest.getDisplayName(), category(guest).name(),
					rsvp == null ? null : rsvp.getResponse(), rsvp == null ? 0 : rsvp.getPlannedAttendeeCount(),
					checkIn != null, checkIn == null ? 0 : checkIn.getActualAttendeeCount(),
					checkIn == null ? null : checkIn.getCheckedInAt());
		}).toList();
		List<ReportCategoryView> categoryViews = byCategory.entrySet().stream()
				.sorted(Map.Entry.comparingByKey(Comparator.comparing(Category::uncategorized)
						.thenComparing(Category::name)))
				.map(entry -> new ReportCategoryView(entry.getKey().id(), entry.getKey().name(), entry.getValue().view()))
				.toList();
		return new Scan(new ReportView(total.view(), categoryViews), rows);
	}

	private Long categoryId(Guest guest) {
		return guest.getCategory() == null ? null : guest.getCategory().getId();
	}

	private Category category(Guest guest) {
		GuestCategory category = guest.getCategory();
		return category == null ? new Category(null, UNCATEGORIZED, true)
				: new Category(category.getId(), category.getDisplayName(), false);
	}

	private record Scan(ReportView view, List<ReportPrintRow> rows) {
		private Scan {
			rows = List.copyOf(rows);
		}
	}

	private record Category(Long id, String name, boolean uncategorized) {
	}

	private static final class Metrics {
		private long invitations;
		private long potentialPeople;
		private long rsvpAttending;
		private long rsvpDeclined;
		private long rsvpMissing;
		private long plannedPeople;
		private long checkedInInvitations;
		private long actualPeople;
		private long attendingNotCheckedIn;
		private long initialSent;
		private long initialUnsent;
		private long rsvpReminderSent;
		private long rsvpReminderUnsent;
		private long eventReminderSent;
		private long eventReminderUnsent;
		private long pendingGreetings;

		private void add(Guest guest, Rsvp rsvp, CheckIn checkIn) {
			invitations++;
			potentialPeople += guest.isPlusOneAllowed() ? 2 : 1;
			if (rsvp == null) rsvpMissing++;
			else if (rsvp.getResponse() == AttendanceResponse.HADIR) {
				rsvpAttending++;
				plannedPeople += rsvp.getPlannedAttendeeCount();
				if (checkIn == null) attendingNotCheckedIn++;
			} else rsvpDeclined++;
			if (checkIn != null) {
				checkedInInvitations++;
				actualPeople += checkIn.getActualAttendeeCount();
			}
			if (guest.getDeliveryState() == DeliveryState.SENT) initialSent++; else initialUnsent++;
			if (guest.getLastRsvpReminderSentAt() != null) rsvpReminderSent++; else rsvpReminderUnsent++;
			if (guest.getLastEventReminderSentAt() != null) eventReminderSent++; else eventReminderUnsent++;
			if (rsvp != null && rsvp.isGreetingPublicConsent() && rsvp.getGreeting() != null
					&& rsvp.getGreetingModerationState() == GreetingModerationState.PENDING) pendingGreetings++;
		}

		private ReportMetrics view() {
			return new ReportMetrics(invitations, potentialPeople, rsvpAttending, rsvpDeclined, rsvpMissing,
					plannedPeople, checkedInInvitations, actualPeople, attendingNotCheckedIn,
					Math.max(plannedPeople - actualPeople, 0), initialSent, initialUnsent,
					rsvpReminderSent, rsvpReminderUnsent, eventReminderSent, eventReminderUnsent, pendingGreetings);
		}
	}
}
