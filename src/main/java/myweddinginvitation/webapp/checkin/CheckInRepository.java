package myweddinginvitation.webapp.checkin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    Optional<CheckIn> findByGuestId(long guestId);
    List<CheckIn> findByGuestIdIn(Collection<Long> guestIds);
    long countByGuestArchivedFalse();

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from CheckIn c where c.id = :id")
	void deleteCurrentById(@Param("id") long id);

    @Query("select coalesce(sum(c.actualAttendeeCount), 0) from CheckIn c where c.guest.archived = false")
    long sumActualAttendanceForActiveGuests();
}
