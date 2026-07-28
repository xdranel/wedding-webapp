package myweddinginvitation.webapp.guest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import jakarta.persistence.criteria.Predicate;
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

	public GuestService(GuestRepository guests, GuestCategoryRepository categories, WhatsappNumberService numbers) {
		this.guests = guests;
		this.categories = categories;
		this.numbers = numbers;
	}

	@Transactional
	public Guest create(GuestForm form, boolean acceptDuplicate) {
		String normalizedNumber = numbers.normalize(form.whatsappNumber(), "ID");
		requireDuplicateAccepted(guests.existsByNormalizedWhatsappNumber(normalizedNumber), acceptDuplicate);
		return guests.saveAndFlush(Guest.create(form, normalizedNumber, category(form.categoryId())));
	}

	@Transactional(readOnly = true)
	public boolean requiresDuplicateConfirmation(GuestForm form) {
		return guests.existsByNormalizedWhatsappNumber(numbers.normalize(form.whatsappNumber(), "ID"));
	}

	@Transactional
	public Guest update(long id, long version, GuestForm form, boolean acceptDuplicate) {
		Guest guest = guest(id);
		requireVersion(guest, version);
		String normalizedNumber = numbers.normalize(form.whatsappNumber(), "ID");
		requireDuplicateAccepted(!normalizedNumber.equals(guest.getNormalizedWhatsappNumber())
				&& guests.existsByNormalizedWhatsappNumber(normalizedNumber), acceptDuplicate);
		guest.update(form, normalizedNumber, category(form.categoryId()));
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

	static class DuplicateWhatsappNumberException extends IllegalStateException {
		DuplicateWhatsappNumberException() {
			super(DUPLICATE);
		}
	}
}
