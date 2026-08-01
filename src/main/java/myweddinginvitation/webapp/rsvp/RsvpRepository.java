package myweddinginvitation.webapp.rsvp;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RsvpRepository extends JpaRepository<Rsvp, Long> {
    Optional<Rsvp> findByGuestId(long guestId);
    Optional<Rsvp> findByGuestPublicId(UUID publicId);
    Page<Rsvp> findByGreetingModerationStateAndGreetingPublicConsentTrueAndGreetingIsNotNull(
            GreetingModerationState state, Pageable pageable);
    long countByResponse(AttendanceResponse response);
    long countByGreetingModerationState(GreetingModerationState state);

    @Query("select coalesce(sum(r.plannedAttendeeCount), 0) from Rsvp r where r.response = 'HADIR'")
    long sumPlannedAttendance();
}
