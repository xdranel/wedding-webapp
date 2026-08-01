package myweddinginvitation.webapp.rsvp;

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

@Entity
@Table(name = "rsvp")
public class Rsvp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceResponse response;

    @Column(name = "planned_attendee_count", nullable = false)
    private int plannedAttendeeCount;

    @Column(length = 500)
    private String greeting;

    @Column(name = "greeting_public_consent", nullable = false)
    private boolean greetingPublicConsent;

    @Enumerated(EnumType.STRING)
    @Column(name = "greeting_moderation_state", nullable = false, length = 20)
    private GreetingModerationState greetingModerationState;

    @Column(name = "private_organizer_note", length = 1000)
    private String privateOrganizerNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_source", nullable = false, length = 20)
    private RsvpUpdateSource updateSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_account_id")
    private UserAccount updatedByAccount;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Rsvp() {
    }

    static Rsvp create(Guest guest, AttendanceResponse response, int plannedAttendeeCount, String greeting,
                       boolean greetingPublicConsent, GreetingModerationState greetingModerationState,
                       String privateOrganizerNote, RsvpUpdateSource updateSource, UserAccount updatedByAccount,
                       Instant now) {
        Rsvp rsvp = new Rsvp();
        rsvp.guest = guest;
        rsvp.createdAt = now;
        rsvp.update(response, plannedAttendeeCount, greeting, greetingPublicConsent, greetingModerationState,
                privateOrganizerNote, updateSource, updatedByAccount, now);
        return rsvp;
    }

    void update(AttendanceResponse response, int plannedAttendeeCount, String greeting,
                boolean greetingPublicConsent, GreetingModerationState greetingModerationState,
                String privateOrganizerNote, RsvpUpdateSource updateSource, UserAccount updatedByAccount,
                Instant now) {
        this.response = response;
        this.plannedAttendeeCount = plannedAttendeeCount;
        this.greeting = greeting;
        this.greetingPublicConsent = greetingPublicConsent;
        this.greetingModerationState = greetingModerationState;
        this.privateOrganizerNote = privateOrganizerNote;
        this.updateSource = updateSource;
        this.updatedByAccount = updatedByAccount;
        updatedAt = now;
    }

    void moderate(GreetingModerationState state, Instant now) {
        greetingModerationState = state;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Guest getGuest() { return guest; }
    public AttendanceResponse getResponse() { return response; }
    public int getPlannedAttendeeCount() { return plannedAttendeeCount; }
    public String getGreeting() { return greeting; }
    public boolean isGreetingPublicConsent() { return greetingPublicConsent; }
    public GreetingModerationState getGreetingModerationState() { return greetingModerationState; }
    public String getPrivateOrganizerNote() { return privateOrganizerNote; }
    public RsvpUpdateSource getUpdateSource() { return updateSource; }
    public UserAccount getUpdatedByAccount() { return updatedByAccount; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
