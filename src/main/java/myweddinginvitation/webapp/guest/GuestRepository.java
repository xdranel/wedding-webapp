package myweddinginvitation.webapp.guest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface GuestRepository extends JpaRepository<Guest, Long>, JpaSpecificationExecutor<Guest> {
	Optional<Guest> findByPublicId(UUID publicId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Guest g where g.id = :id")
	Optional<Guest> findByIdForUpdate(@Param("id") long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Guest g where g.publicId = :publicId")
	Optional<Guest> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

	boolean existsByNormalizedWhatsappNumber(String normalizedWhatsappNumber);

	@Query("""
			select g from Guest g
			where g.archived = false and (
				(:phoneSuffix = true and function('right', g.normalizedWhatsappNumber, 4) = :query)
				or (:phoneSuffix = false and lower(g.displayName) like lower(concat('%', :query, '%')))
			)
			order by g.displayName asc, g.id asc
			""")
	@EntityGraph(attributePaths = "category")
	List<Guest> findActiveForCheckIn(@Param("query") String query, @Param("phoneSuffix") boolean phoneSuffix,
			Pageable pageable);

	List<Guest> findAllByOrderByDisplayNameAscIdAsc();

	@EntityGraph(attributePaths = "category")
	@Query("select g from Guest g order by g.displayName asc, g.id asc")
	List<Guest> findAllForReminderQueue(Pageable pageable);

	@EntityGraph(attributePaths = "category")
	@Query("select g from Guest g where g.archived = false order by g.displayName asc, g.id asc")
	List<Guest> findAllActiveForReport(Pageable pageable);

	long countByCategoryIsNull();
	long countByArchivedFalse();
}
