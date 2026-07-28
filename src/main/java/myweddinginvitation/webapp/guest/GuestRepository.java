package myweddinginvitation.webapp.guest;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {
	Optional<Guest> findByPublicId(UUID publicId);
	boolean existsByNormalizedWhatsappNumber(String normalizedWhatsappNumber);
	long countByCategoryIsNull();
}
