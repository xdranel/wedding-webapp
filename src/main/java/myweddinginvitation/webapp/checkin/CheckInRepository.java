package myweddinginvitation.webapp.checkin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    Optional<CheckIn> findByGuestId(long guestId);
    List<CheckIn> findByGuestIdIn(Collection<Long> guestIds);
    long countByGuestArchivedFalse();

    @Query("select coalesce(sum(c.actualAttendeeCount), 0) from CheckIn c where c.guest.archived = false")
    long sumActualAttendanceForActiveGuests();
}
