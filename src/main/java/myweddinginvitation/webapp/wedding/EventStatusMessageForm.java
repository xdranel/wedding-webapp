package myweddinginvitation.webapp.wedding;

import jakarta.validation.constraints.Size;

public class EventStatusMessageForm {
	private Long version;

	@Size(max = 160)
	private String titleId;

	@Size(max = 160)
	private String titleEn;

	@Size(max = 1000)
	private String messageId;

	@Size(max = 1000)
	private String messageEn;

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public String getTitleId() {
		return titleId;
	}

	public void setTitleId(String titleId) {
		this.titleId = normalized(titleId);
	}

	public String getTitleEn() {
		return titleEn;
	}

	public void setTitleEn(String titleEn) {
		this.titleEn = normalized(titleEn);
	}

	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId = normalized(messageId);
	}

	public String getMessageEn() {
		return messageEn;
	}

	public void setMessageEn(String messageEn) {
		this.messageEn = normalized(messageEn);
	}

	private static String normalized(String value) {
		if (value == null) return null;
		String normalized = value.strip();
		return normalized.isEmpty() ? null : normalized;
	}
}
