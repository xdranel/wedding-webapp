package myweddinginvitation.webapp.guest;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestCategoryService {
	private static final String DUPLICATE = "A category with this name already exists.";
	private final GuestCategoryRepository categories;
	private final GuestRepository guests;

	public GuestCategoryService(GuestCategoryRepository categories, GuestRepository guests) {
		this.categories = categories;
		this.guests = guests;
	}

	@Transactional(readOnly = true)
	public List<GuestCategory> findAll() {
		return categories.findAll(Sort.by("displayName"));
	}

	@Transactional(readOnly = true)
	public long withoutCategoryCount() {
		return guests.countByCategoryIsNull();
	}

	@Transactional
	public void create(String name) {
		String displayName = displayName(name);
		String normalizedName = normalizeCategoryName(name);
		requireUnique(normalizedName, null);
		try {
			categories.saveAndFlush(GuestCategory.create(displayName, normalizedName));
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException(DUPLICATE, exception);
		}
	}

	@Transactional
	public void rename(long id, long version, String name) {
		GuestCategory category = categories.findById(id).orElseThrow();
		if (category.getVersion() != version) {
			throw new OptimisticLockingFailureException("Guest category has changed");
		}
		String normalizedName = normalizeCategoryName(name);
		requireUnique(normalizedName, id);
		category.rename(displayName(name), normalizedName);
		try {
			categories.saveAndFlush(category);
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException(DUPLICATE, exception);
		}
	}

	@Transactional
	public void delete(long id, long version) {
		GuestCategory category = categories.findById(id).orElseThrow();
		if (category.getVersion() != version) {
			throw new OptimisticLockingFailureException("Guest category has changed");
		}
		categories.delete(category);
		categories.flush();
	}

	static String normalizeCategoryName(String value) {
		return displayName(value).toLowerCase(Locale.ROOT);
	}

	private static String displayName(String value) {
		return value.strip().replaceAll("\\s+", " ");
	}

	private void requireUnique(String normalizedName, Long currentId) {
		categories.findByNormalizedName(normalizedName)
				.filter(category -> !Objects.equals(category.getId(), currentId))
				.ifPresent(category -> {
					throw new IllegalArgumentException(DUPLICATE);
				});
	}
}
