package myweddinginvitation.webapp.guest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestRepository extends JpaRepository<Guest, Long>, JpaSpecificationExecutor<Guest> {
	Optional<Guest> findByPublicId(UUID publicId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Guest g where g.publicId = :publicId")
	Optional<Guest> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

	boolean existsByNormalizedWhatsappNumber(String normalizedWhatsappNumber);
	List<Guest> findAllByOrderByDisplayNameAscIdAsc();
	long countByCategoryIsNull();
	long countByArchivedFalse();
}
