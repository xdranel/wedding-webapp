package myweddinginvitation.webapp.guest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GuestRepository extends JpaRepository<Guest, Long>, JpaSpecificationExecutor<Guest> {
	Optional<Guest> findByPublicId(UUID publicId);
	boolean existsByNormalizedWhatsappNumber(String normalizedWhatsappNumber);
	List<Guest> findAllByOrderByDisplayNameAscIdAsc();
	long countByCategoryIsNull();
}
