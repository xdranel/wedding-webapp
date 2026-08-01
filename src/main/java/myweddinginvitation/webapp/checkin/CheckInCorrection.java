package myweddinginvitation.webapp.checkin;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.guest.Guest;

@Entity
@Table(name = "check_in_correction")
public class CheckInCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_id")
    private CheckIn checkIn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInCorrectionAction action;

    @Column(name = "before_actual_attendee_count", nullable = false)
    private int beforeActualAttendeeCount;

    @Column(name = "after_actual_attendee_count")
    private Integer afterActualAttendeeCount;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by_account_id", nullable = false)
    private UserAccount correctedByAccount;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    @Column(name = "original_checked_in_at", nullable = false)
    private Instant originalCheckedInAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_checked_in_by_account_id")
    private UserAccount originalCheckedInByAccount;

    @Column(name = "original_checked_in_by_username", nullable = false, length = 100)
    private String originalCheckedInByUsername;

    protected CheckInCorrection() {
    }

    static CheckInCorrection create(Guest guest, CheckIn checkIn, CheckInCorrectionAction action,
                                    int beforeActualAttendeeCount, Integer afterActualAttendeeCount, String reason,
                                    UserAccount correctedByAccount, Instant correctedAt, Instant originalCheckedInAt,
                                    UserAccount originalCheckedInByAccount, String originalCheckedInByUsername) {
        CheckInCorrection correction = new CheckInCorrection();
        correction.guest = guest;
        correction.checkIn = checkIn;
        correction.action = action;
        correction.beforeActualAttendeeCount = beforeActualAttendeeCount;
        correction.afterActualAttendeeCount = afterActualAttendeeCount;
        correction.reason = reason;
        correction.correctedByAccount = correctedByAccount;
        correction.correctedAt = correctedAt;
        correction.originalCheckedInAt = originalCheckedInAt;
        correction.originalCheckedInByAccount = originalCheckedInByAccount;
        correction.originalCheckedInByUsername = originalCheckedInByUsername;
        return correction;
    }

    public Long getId() { return id; }
    public Guest getGuest() { return guest; }
    public CheckIn getCheckIn() { return checkIn; }
    public CheckInCorrectionAction getAction() { return action; }
    public int getBeforeActualAttendeeCount() { return beforeActualAttendeeCount; }
    public Integer getAfterActualAttendeeCount() { return afterActualAttendeeCount; }
    public String getReason() { return reason; }
    public UserAccount getCorrectedByAccount() { return correctedByAccount; }
    public Instant getCorrectedAt() { return correctedAt; }
    public Instant getOriginalCheckedInAt() { return originalCheckedInAt; }
    public UserAccount getOriginalCheckedInByAccount() { return originalCheckedInByAccount; }
    public String getOriginalCheckedInByUsername() { return originalCheckedInByUsername; }
}
