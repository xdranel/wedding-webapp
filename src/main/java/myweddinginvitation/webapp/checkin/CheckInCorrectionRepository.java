package myweddinginvitation.webapp.checkin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInCorrectionRepository extends JpaRepository<CheckInCorrection, Long> {
    List<CheckInCorrection> findByGuestIdOrderByCorrectedAtDescIdDesc(long guestId);
}
