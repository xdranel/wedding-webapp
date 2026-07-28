package myweddinginvitation.webapp.wedding;

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

	void publish() {
		publicationState = PublicationState.PUBLISHED;
	}

	void returnToDraft() {
		publicationState = PublicationState.DRAFT;
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
	}
}
