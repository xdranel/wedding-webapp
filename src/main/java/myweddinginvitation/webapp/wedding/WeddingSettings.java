package myweddinginvitation.webapp.wedding;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "wedding_settings")
public class WeddingSettings {
    @Id
    @Column(columnDefinition = "tinyint")
    private Byte id = 1;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false, length = 20)
    private PublicationState publicationState = PublicationState.DRAFT;

    @Column(name = "couple_title", length = 160)
    private String coupleTitle;

    @Column(name = "opening_text_id", length = 2000)
    private String openingTextId;

    @Column(name = "opening_text_en", length = 2000)
    private String openingTextEn;

    @Column(name = "closing_text_id", length = 2000)
    private String closingTextId;

    @Column(name = "closing_text_en", length = 2000)
    private String closingTextEn;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "Asia/Jakarta";

    @Column(name = "rsvp_deadline")
    private LocalDateTime rsvpDeadline;

    @Column(name = "default_phone_country", nullable = false, length = 2)
    private String defaultPhoneCountry = "ID";

    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor = "#7A5C48";

    @Enumerated(EnumType.STRING)
    @Column(name = "font_preset", nullable = false, length = 30)
    private FontPreset fontPreset = FontPreset.CLASSIC;

    @Column(name = "event_closed", nullable = false)
    private boolean eventClosed;

    @Column(name = "event_status_changed_at")
    private Instant eventStatusChangedAt;

    @Column(name = "event_status_changed_by", length = 100)
    private String eventStatusChangedBy;

    @Column(name = "closed_title_id", length = 160)
    private String closedTitleId;

    @Column(name = "closed_title_en", length = 160)
    private String closedTitleEn;

    @Column(name = "closed_message_id", length = 1000)
    private String closedMessageId;

    @Column(name = "closed_message_en", length = 1000)
    private String closedMessageEn;

    @Column(name = "greetings_enabled", nullable = false)
    private boolean greetingsEnabled = true;

    @Column(name = "private_organizer_note_enabled", nullable = false)
    private boolean privateOrganizerNoteEnabled;

    @Column(name = "calendar_downloads_enabled", nullable = false)
    private boolean calendarDownloadsEnabled;

    @Column(name = "gallery_enabled", nullable = false)
    private boolean galleryEnabled;

    @Column(name = "background_audio_enabled", nullable = false)
    private boolean backgroundAudioEnabled;

    @Column(name = "background_audio_path", length = 500)
    private String backgroundAudioPath;

    @Column(name = "invitation_cover_path", length = 500)
    private String invitationCoverPath;

    protected WeddingSettings() {
    }

    static WeddingSettings initial() {
        return new WeddingSettings();
    }

    public Byte getId() {
        return id;
    }

    public PublicationState getPublicationState() {
        return publicationState;
    }

    public long getVersion() {
        return version;
    }

    public String getCoupleTitle() {
        return coupleTitle;
    }

    public String getOpeningTextId() {
        return openingTextId;
    }

    public String getOpeningTextEn() {
        return openingTextEn;
    }

    public String getClosingTextId() {
        return closingTextId;
    }

    public String getClosingTextEn() {
        return closingTextEn;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public LocalDateTime getRsvpDeadline() {
        return rsvpDeadline;
    }

    public String getDefaultPhoneCountry() {
        return defaultPhoneCountry;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public FontPreset getFontPreset() {
        return fontPreset;
    }

    public boolean isEventClosed() {
        return eventClosed;
    }

    public Instant getEventStatusChangedAt() {
        return eventStatusChangedAt;
    }

    public String getEventStatusChangedBy() {
        return eventStatusChangedBy;
    }

    public String getClosedTitleId() {
        return closedTitleId;
    }

    public String getClosedTitleEn() {
        return closedTitleEn;
    }

    public String getClosedMessageId() {
        return closedMessageId;
    }

    public String getClosedMessageEn() {
        return closedMessageEn;
    }

    public boolean isGreetingsEnabled() {
        return greetingsEnabled;
    }

    public boolean isPrivateOrganizerNoteEnabled() {
        return privateOrganizerNoteEnabled;
    }

    public boolean isCalendarDownloadsEnabled() {
        return calendarDownloadsEnabled;
    }

    public boolean isGalleryEnabled() {
        return galleryEnabled;
    }

    public boolean isBackgroundAudioEnabled() {
        return backgroundAudioEnabled;
    }

    public String getBackgroundAudioPath() {
        return backgroundAudioPath;
    }

    public String getInvitationCoverPath() {
        return invitationCoverPath;
    }

    void publish() {
        publicationState = PublicationState.PUBLISHED;
    }

    void returnToDraft() {
        publicationState = PublicationState.DRAFT;
    }

    void setGalleryEnabled(boolean enabled, boolean hasPhotos) {
        if (enabled && !hasPhotos) throw new IllegalStateException("Gallery requires at least one photo");
        galleryEnabled = enabled;
    }

    void setBackgroundAudioEnabled(boolean enabled) {
        if (enabled && backgroundAudioPath == null) throw new IllegalStateException("Background audio is missing");
        backgroundAudioEnabled = enabled;
    }

    void replaceBackgroundAudio(String relativePath) {
        backgroundAudioPath = relativePath;
    }

    String removeBackgroundAudio() {
        String removed = backgroundAudioPath;
        backgroundAudioPath = null;
        backgroundAudioEnabled = false;
        return removed;
    }

    void replaceInvitationCover(String relativePath) {
        invitationCoverPath = relativePath;
    }

    String removeInvitationCover() {
        String removed = invitationCoverPath;
        invitationCoverPath = null;
        return removed;
    }

    void closeEvent(String username, Instant changedAt) {
        eventClosed = true;
        eventStatusChangedBy = username;
        eventStatusChangedAt = changedAt;
    }

    void reopenEvent(String username, Instant changedAt) {
        eventClosed = false;
        eventStatusChangedBy = username;
        eventStatusChangedAt = changedAt;
    }

    void updateEventStatusMessage(EventStatusMessageForm form) {
        closedTitleId = nullableCopy(form.getTitleId(), 160);
        closedTitleEn = nullableCopy(form.getTitleEn(), 160);
        closedMessageId = nullableCopy(form.getMessageId(), 1000);
        closedMessageEn = nullableCopy(form.getMessageEn(), 1000);
    }

    void update(WeddingSettingsForm form) {
        coupleTitle = form.getCoupleTitle();
        openingTextId = form.getOpeningTextId();
        openingTextEn = form.getOpeningTextEn();
        closingTextId = form.getClosingTextId();
        closingTextEn = form.getClosingTextEn();
        timeZone = form.getTimeZone();
        rsvpDeadline = form.getRsvpDeadline();
        defaultPhoneCountry = form.getDefaultPhoneCountry();
        accentColor = form.getAccentColor();
        fontPreset = form.getFontPreset();
        greetingsEnabled = form.isGreetingsEnabled();
        privateOrganizerNoteEnabled = form.isPrivateOrganizerNoteEnabled();
        calendarDownloadsEnabled = form.isCalendarDownloadsEnabled();
    }

    private String nullableCopy(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Event status message is too long");
        return normalized;
    }
}
