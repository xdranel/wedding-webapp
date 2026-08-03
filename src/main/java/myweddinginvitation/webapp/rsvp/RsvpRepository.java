package myweddinginvitation.webapp.rsvp;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RsvpRepository extends JpaRepository<Rsvp, Long> {
    Optional<Rsvp> findByGuestId(long guestId);
	@Query("select r.guest.id from Rsvp r where r.id = :id")
	Optional<Long> findGuestIdById(@Param("id") long id);
    List<Rsvp> findByGuestIdIn(Collection<Long> guestIds);
    Optional<Rsvp> findByGuestPublicId(UUID publicId);
    @EntityGraph(attributePaths = "guest")
    Page<Rsvp> findByGreetingModerationStateAndGreetingPublicConsentTrueAndGreetingIsNotNull(
            GreetingModerationState state, Pageable pageable);
	@EntityGraph(attributePaths = "guest")
	Page<Rsvp> findByGreetingModerationStateAndGreetingPublicConsentTrueAndGreetingIsNotNullAndGuestArchivedFalse(
			GreetingModerationState state, Pageable pageable);
    long countByResponse(AttendanceResponse response);
    long countByGreetingModerationState(GreetingModerationState state);
    long countByResponseAndGuestArchivedFalse(AttendanceResponse response);
    long countByGreetingModerationStateAndGuestArchivedFalse(GreetingModerationState state);

    @Query("select coalesce(sum(r.plannedAttendeeCount), 0) from Rsvp r where r.response = 'HADIR'")
    long sumPlannedAttendance();

	@Query("select coalesce(sum(r.plannedAttendeeCount), 0) from Rsvp r "
			+ "where r.response = :response and r.guest.archived = false")
	long sumPlannedAttendanceForActiveGuests(@Param("response") AttendanceResponse response);

	default long sumPlannedAttendanceForActiveGuests() {
		return sumPlannedAttendanceForActiveGuests(AttendanceResponse.HADIR);
	}
}
