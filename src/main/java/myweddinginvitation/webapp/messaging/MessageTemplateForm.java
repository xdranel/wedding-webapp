package myweddinginvitation.webapp.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MessageTemplateForm {
	private Long version;

	@NotBlank
	@Size(max = 4000)
	private String body;

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
