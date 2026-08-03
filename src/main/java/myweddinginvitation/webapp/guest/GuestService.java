package myweddinginvitation.webapp.guest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import myweddinginvitation.webapp.checkin.CheckIn;
import myweddinginvitation.webapp.checkin.CheckInRepository;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;
import myweddinginvitation.webapp.rsvp.Rsvp;
import myweddinginvitation.webapp.rsvp.RsvpService;
import myweddinginvitation.webapp.rsvp.RsvpSubmission;
import myweddinginvitation.webapp.rsvp.RsvpView;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestService {
	private static final String DUPLICATE = "This WhatsApp number already belongs to another guest. Confirm to continue.";
	private final GuestRepository guests;
	private final GuestCategoryRepository categories;
	private final WhatsappNumberService numbers;
	private final RsvpService rsvps;
	private final CheckInRepository checkIns;

	public GuestService(GuestRepository guests, GuestCategoryRepository categories, WhatsappNumberService numbers,
			RsvpService rsvps, CheckInRepository checkIns) {
		this.guests = guests;
		this.categories = categories;
		this.numbers = numbers;
		this.rsvps = rsvps;
		this.checkIns = checkIns;
	}

	@Transactional
	public Guest create(GuestForm form, boolean acceptDuplicate) {
		String normalizedNumber = numbers.normalize(form.whatsappNumber(), form.phoneRegion());
		requireDuplicateAccepted(guests.existsByNormalizedWhatsappNumber(normalizedNumber), acceptDuplicate);
		return guests.saveAndFlush(Guest.create(form, normalizedNumber, category(form.categoryId())));
	}

	@Transactional(readOnly = true)
	public boolean requiresDuplicateConfirmation(GuestForm form) {
		return guests.existsByNormalizedWhatsappNumber(numbers.normalize(form.whatsappNumber(), form.phoneRegion()));
	}

	@Transactional
	public Guest update(long id, long version, GuestForm form, boolean acceptDuplicate) {
		return update(id, version, form, acceptDuplicate, false, null);
	}

	@Transactional
	public Guest update(long id, long version, GuestForm form, boolean acceptDuplicate,
			boolean reducePlannedAttendance, String username) {
		Guest guest = guests.findByIdForUpdate(id).orElseThrow(NoSuchElementException::new);
		requireVersion(guest, version);
		String normalizedNumber = numbers.normalize(form.whatsappNumber(), form.phoneRegion());
		requireDuplicateAccepted(!normalizedNumber.equals(guest.getNormalizedWhatsappNumber())
				&& guests.existsByNormalizedWhatsappNumber(normalizedNumber), acceptDuplicate);
		boolean phoneChanged = !normalizedNumber.equals(guest.getNormalizedWhatsappNumber());
		if (guest.isPlusOneAllowed() && !form.plusOneAllowed()
				&& checkIns.findByGuestId(id).map(checkIn -> checkIn.getActualAttendeeCount() == 2).orElse(false)) {
			throw new CheckInAllowanceReductionForbiddenException();
		}
		RsvpView rsvp = rsvps.view(id).orElse(null);
		boolean reductionRequired = guest.isPlusOneAllowed() && !form.plusOneAllowed()
				&& rsvp != null && rsvp.response() == AttendanceResponse.HADIR
				&& rsvp.plannedAttendeeCount() == 2;
		if (reductionRequired && !reducePlannedAttendance) {
			throw new PlannedAttendanceReductionRequiredException();
		}
		if (reductionRequired) {
			rsvps.correctByAdmin(id, rsvp.version(),
					new RsvpSubmission(AttendanceResponse.HADIR, 1, null, false, null), username);
		}
		guest.update(form, normalizedNumber, category(form.categoryId()));
		if (phoneChanged) guest.resetPinSecurity();
		return guests.saveAndFlush(guest);
	}

	@Transactional
	public void archive(long id, long version) {
		Guest guest = guest(id);
		requireVersion(guest, version);
		guest.archive();
		guests.saveAndFlush(guest);
	}

	@Transactional
	public void restore(long id, long version) {
		Guest guest = guest(id);
		requireVersion(guest, version);
		guest.restore();
		guests.saveAndFlush(guest);
	}

	@Transactional
	public void deleteInactive(long id, long version) {
		Guest guest = guest(id);
		requireVersion(guest, version);
		if (guest.getDeliveryState() == DeliveryState.SENT) {
			throw new IllegalStateException("Sent guests must be archived instead of permanently deleted.");
		}
		guests.delete(guest);
		guests.flush();
	}

	@Transactional
	public void confirmSent(long id, long version, Instant sentAt) {
		Guest guest = guest(id);
		requireActive(guest);
		requireVersion(guest, version);
		guest.confirmSent(sentAt);
		guests.saveAndFlush(guest);
	}

	@Transactional
	public Guest regenerateInvitation(long id, long version, Instant regeneratedAt) {
		Guest guest = guest(id);
		requireActive(guest);
		requireVersion(guest, version);
		guest.regenerateInvitation(regeneratedAt);
		return guests.saveAndFlush(guest);
	}

	@Transactional(readOnly = true)
	public Guest get(long id) {
		return guest(id);
	}

	@Transactional(readOnly = true)
	public Page<Guest> search(GuestListQuery filters, Pageable pageable) {
		return guests.findAll(specification(filters), pageable);
	}

	private Specification<Guest> specification(GuestListQuery filters) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (filters.query() != null && !filters.query().isBlank()) {
				predicates.add(builder.like(builder.lower(root.get("displayName")),
						"%" + filters.query().strip().toLowerCase(Locale.ROOT) + "%"));
			}
			if (filters.delivery() != null) {
				predicates.add(builder.equal(root.get("deliveryState"), filters.delivery()));
			}
			if (filters.archived() != null) {
				predicates.add(builder.equal(root.get("archived"), filters.archived()));
			}
			if (filters.categoryId() != null) {
				predicates.add(builder.equal(root.get("category").get("id"), filters.categoryId()));
			}
			if (filters.rsvp() != null || filters.noRsvp()) {
				Subquery<Long> matchingRsvp = query.subquery(Long.class);
				Root<Rsvp> rsvp = matchingRsvp.from(Rsvp.class);
				List<Predicate> rsvpPredicates = new ArrayList<>();
				rsvpPredicates.add(builder.equal(rsvp.get("guest"), root));
				if (filters.rsvp() != null) {
					rsvpPredicates.add(builder.equal(rsvp.get("response"), filters.rsvp()));
				}
				matchingRsvp.select(rsvp.get("id")).where(rsvpPredicates.toArray(Predicate[]::new));
				predicates.add(filters.noRsvp() ? builder.not(builder.exists(matchingRsvp))
						: builder.exists(matchingRsvp));
			}
			if (filters.checkedIn() != null) {
				Subquery<Long> matchingCheckIn = query.subquery(Long.class);
				Root<CheckIn> checkIn = matchingCheckIn.from(CheckIn.class);
				matchingCheckIn.select(checkIn.get("id"))
						.where(builder.equal(checkIn.get("guest"), root));
				predicates.add(filters.checkedIn() ? builder.exists(matchingCheckIn)
						: builder.not(builder.exists(matchingCheckIn)));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private Guest guest(long id) {
		return guests.findById(id).orElseThrow(NoSuchElementException::new);
	}

	private GuestCategory category(Long categoryId) {
		return categoryId == null ? null : categories.findById(categoryId).orElseThrow(NoSuchElementException::new);
	}

	private void requireDuplicateAccepted(boolean duplicate, boolean acceptDuplicate) {
		if (duplicate && !acceptDuplicate) {
			throw new DuplicateWhatsappNumberException();
		}
	}

	private void requireVersion(Guest guest, long version) {
		if (guest.getVersion() != version) {
			throw new OptimisticLockingFailureException("Guest has changed");
		}
	}

	private void requireActive(Guest guest) {
		if (guest.isArchived()) {
			throw new IllegalStateException("Delivery is disabled for archived guests.");
		}
	}

	static class DuplicateWhatsappNumberException extends IllegalStateException {
		DuplicateWhatsappNumberException() {
			super(DUPLICATE);
		}
	}

	static class PlannedAttendanceReductionRequiredException extends IllegalStateException {
		PlannedAttendanceReductionRequiredException() {
			super("Confirm reducing planned attendance to one before disabling +1.");
		}
	}

	static class CheckInAllowanceReductionForbiddenException extends IllegalStateException {
		CheckInAllowanceReductionForbiddenException() {
			super("Cannot disable +1 after two people have checked in.");
		}
	}
}
