package myweddinginvitation.webapp.wedding;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class EventStatusChangeForm {
	@NotNull
	private Long version;

	@AssertTrue(message = "Confirmation is required")
	private boolean confirmed;

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}
}
