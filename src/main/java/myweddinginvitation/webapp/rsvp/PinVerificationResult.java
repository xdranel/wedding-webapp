package myweddinginvitation.webapp.rsvp;

import java.time.Instant;

public record PinVerificationResult(Status status, Instant retryAt) {
	public enum Status {
		SUCCESS, MALFORMED, INVALID, LOCKED, UNAVAILABLE
	}
}
