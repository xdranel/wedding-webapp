package myweddinginvitation.webapp.guest;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestCategoryRepository extends JpaRepository<GuestCategory, Long> {
	Optional<GuestCategory> findByNormalizedName(String normalizedName);
}
