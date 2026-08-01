package myweddinginvitation.webapp.guest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "guest")
public class Guest {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, columnDefinition = "binary(16)")
	private UUID publicId;

	@Column(name = "display_name", nullable = false, length = 160)
	private String displayName;

	@Column(nullable = false, length = 80)
	private String salutation;

	@Column(name = "normalized_whatsapp_number", nullable = false, length = 20)
	private String normalizedWhatsappNumber;

	@ManyToOne
	@JoinColumn(name = "category_id")
	private GuestCategory category;

	@Column(name = "internal_note", length = 2000)
	private String internalNote;

	@Column(name = "plus_one_allowed", nullable = false)
	private boolean plusOneAllowed;

	@Enumerated(EnumType.STRING)
	@Column(name = "preferred_language", nullable = false, length = 2)
	private MessageLanguage preferredLanguage = MessageLanguage.ID;

	@Column(name = "invitation_token_version", nullable = false)
	private long invitationTokenVersion = 1;

	@Column(name = "token_regenerated_at")
	private Instant tokenRegeneratedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_state", nullable = false, length = 20)
	private DeliveryState deliveryState = DeliveryState.UNSENT;

	@Column(name = "first_sent_at")
	private Instant firstSentAt;

	@Column(name = "last_sent_at")
	private Instant lastSentAt;

	@Column(nullable = false)
	private boolean archived;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "failed_pin_count", nullable = false)
	private int failedPinCount;

	@Column(name = "pin_locked_until")
	private Instant pinLockedUntil;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Guest() {
	}

	static Guest create(GuestForm form, String normalizedWhatsappNumber, GuestCategory category) {
		Guest guest = new Guest();
		guest.publicId = UUID.randomUUID();
		guest.createdAt = Instant.now();
		guest.updatedAt = guest.createdAt;
		guest.update(form, normalizedWhatsappNumber, category);
		return guest;
	}

	void update(GuestForm form, String normalizedWhatsappNumber, GuestCategory category) {
		displayName = form.displayName().strip();
		salutation = form.salutation().strip();
		this.normalizedWhatsappNumber = normalizedWhatsappNumber;
		this.category = category;
		internalNote = form.internalNote();
		plusOneAllowed = form.plusOneAllowed();
		preferredLanguage = form.preferredLanguage();
		updatedAt = Instant.now();
	}

	void archive() {
		archived = true;
		archivedAt = Instant.now();
		updatedAt = archivedAt;
	}

	void restore() {
		archived = false;
		archivedAt = null;
		updatedAt = Instant.now();
	}

	void confirmSent(Instant sentAt) {
		deliveryState = DeliveryState.SENT;
		if (firstSentAt == null) {
			firstSentAt = sentAt;
		}
		lastSentAt = sentAt;
		updatedAt = sentAt;
	}

	void regenerateInvitation(Instant regeneratedAt) {
		invitationTokenVersion++;
		tokenRegeneratedAt = regeneratedAt;
		deliveryState = DeliveryState.UNSENT;
		firstSentAt = null;
		lastSentAt = null;
		updatedAt = regeneratedAt;
	}

	public void pinFailed(Instant now) {
		if (pinLockedUntil != null && !now.isBefore(pinLockedUntil)) clearPinLock();
		if (pinLockedUntil != null) return;
		failedPinCount++;
		if (failedPinCount == 5) pinLockedUntil = now.plus(Duration.ofMinutes(15));
	}

	public void pinSucceeded() {
		resetPinSecurity();
	}

	public void clearPinLock() {
		failedPinCount = 0;
		pinLockedUntil = null;
	}

	public void resetPinSecurity() {
		clearPinLock();
	}

	public Long getId() { return id; }
	public UUID getPublicId() { return publicId; }
	public String getDisplayName() { return displayName; }
	public String getSalutation() { return salutation; }
	public String getNormalizedWhatsappNumber() { return normalizedWhatsappNumber; }
	public GuestCategory getCategory() { return category; }
	public String getInternalNote() { return internalNote; }
	public boolean isPlusOneAllowed() { return plusOneAllowed; }
	public MessageLanguage getPreferredLanguage() { return preferredLanguage; }
	public long getInvitationTokenVersion() { return invitationTokenVersion; }
	public Instant getTokenRegeneratedAt() { return tokenRegeneratedAt; }
	public DeliveryState getDeliveryState() { return deliveryState; }
	public Instant getFirstSentAt() { return firstSentAt; }
	public Instant getLastSentAt() { return lastSentAt; }
	public boolean isArchived() { return archived; }
	public Instant getArchivedAt() { return archivedAt; }
	public int getFailedPinCount() { return failedPinCount; }
	public Instant getPinLockedUntil() { return pinLockedUntil; }
	public long getVersion() { return version; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
