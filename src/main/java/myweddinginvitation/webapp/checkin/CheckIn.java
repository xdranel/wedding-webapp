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
import jakarta.persistence.Version;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.rsvp.AttendanceResponse;

@Entity
@Table(name = "check_in")
public class CheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Column(name = "actual_attendee_count", nullable = false)
    private int actualAttendeeCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checked_in_by_account_id", nullable = false)
    private UserAccount checkedInByAccount;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Version
    private long version;

    @Column(name = "rsvp_auto_changed", nullable = false)
    private boolean rsvpAutoChanged;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_rsvp_response", length = 20)
    private AttendanceResponse previousRsvpResponse;

    @Column(name = "previous_planned_attendee_count")
    private Integer previousPlannedAttendeeCount;

    @Column(name = "rsvp_version_after_change")
    private Long rsvpVersionAfterChange;

    protected CheckIn() {
    }

    static CheckIn create(Guest guest, int actualAttendeeCount, UserAccount checkedInByAccount, Instant checkedInAt,
                          boolean rsvpAutoChanged, AttendanceResponse previousRsvpResponse,
                          Integer previousPlannedAttendeeCount, Long rsvpVersionAfterChange) {
        CheckIn checkIn = new CheckIn();
        checkIn.guest = guest;
        checkIn.actualAttendeeCount = actualAttendeeCount;
        checkIn.checkedInByAccount = checkedInByAccount;
        checkIn.checkedInAt = checkedInAt;
        checkIn.rsvpAutoChanged = rsvpAutoChanged;
        checkIn.previousRsvpResponse = previousRsvpResponse;
        checkIn.previousPlannedAttendeeCount = previousPlannedAttendeeCount;
        checkIn.rsvpVersionAfterChange = rsvpVersionAfterChange;
        return checkIn;
    }

    void correctActualAttendeeCount(int actualAttendeeCount) {
        this.actualAttendeeCount = actualAttendeeCount;
    }

    public Long getId() { return id; }
    public Guest getGuest() { return guest; }
    public int getActualAttendeeCount() { return actualAttendeeCount; }
    public UserAccount getCheckedInByAccount() { return checkedInByAccount; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public long getVersion() { return version; }
    public boolean isRsvpAutoChanged() { return rsvpAutoChanged; }
    public AttendanceResponse getPreviousRsvpResponse() { return previousRsvpResponse; }
    public Integer getPreviousPlannedAttendeeCount() { return previousPlannedAttendeeCount; }
    public Long getRsvpVersionAfterChange() { return rsvpVersionAfterChange; }
}
