package myweddinginvitation.webapp.wedding;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

public class WeddingSettingsForm {
    @NotNull
    private Long version;

    @Size(max = 160)
    private String coupleTitle;

    @NotBlank
    @Size(max = 2000)
    private String openingTextId;

    @Size(max = 2000)
    private String openingTextEn;

    @NotBlank
    @Size(max = 2000)
    private String closingTextId;

    @Size(max = 2000)
    private String closingTextEn;

    @NotBlank
    private String timeZone = "Asia/Jakarta";

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime rsvpDeadline;

    @Pattern(regexp = "[A-Z]{2}")
    private String defaultPhoneCountry = "ID";

    @Pattern(regexp = "#[0-9A-Fa-f]{6}")
    private String accentColor = "#7A5C48";

    @NotNull
    private FontPreset fontPreset = FontPreset.CLASSIC;

    private boolean eventClosed;

    private boolean greetingsEnabled = true;

    private boolean privateOrganizerNoteEnabled;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getCoupleTitle() {
        return coupleTitle;
    }

    public void setCoupleTitle(String coupleTitle) {
        this.coupleTitle = coupleTitle;
    }

    public String getOpeningTextId() {
        return openingTextId;
    }

    public void setOpeningTextId(String openingTextId) {
        this.openingTextId = openingTextId;
    }

    public String getOpeningTextEn() {
        return openingTextEn;
    }

    public void setOpeningTextEn(String openingTextEn) {
        this.openingTextEn = openingTextEn;
    }

    public String getClosingTextId() {
        return closingTextId;
    }

    public void setClosingTextId(String closingTextId) {
        this.closingTextId = closingTextId;
    }

    public String getClosingTextEn() {
        return closingTextEn;
    }

    public void setClosingTextEn(String closingTextEn) {
        this.closingTextEn = closingTextEn;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public LocalDateTime getRsvpDeadline() {
        return rsvpDeadline;
    }

    public void setRsvpDeadline(LocalDateTime rsvpDeadline) {
        this.rsvpDeadline = rsvpDeadline;
    }

    public String getDefaultPhoneCountry() {
        return defaultPhoneCountry;
    }

    public void setDefaultPhoneCountry(String defaultPhoneCountry) {
        this.defaultPhoneCountry = defaultPhoneCountry;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public FontPreset getFontPreset() {
        return fontPreset;
    }

    public void setFontPreset(FontPreset fontPreset) {
        this.fontPreset = fontPreset;
    }

    public boolean isEventClosed() {
        return eventClosed;
    }

    public void setEventClosed(boolean eventClosed) {
        this.eventClosed = eventClosed;
    }

    public boolean isGreetingsEnabled() {
        return greetingsEnabled;
    }

    public void setGreetingsEnabled(boolean greetingsEnabled) {
        this.greetingsEnabled = greetingsEnabled;
    }

    public boolean isPrivateOrganizerNoteEnabled() {
        return privateOrganizerNoteEnabled;
    }

    public void setPrivateOrganizerNoteEnabled(boolean privateOrganizerNoteEnabled) {
        this.privateOrganizerNoteEnabled = privateOrganizerNoteEnabled;
    }
}
