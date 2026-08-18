package myweddinginvitation.webapp.wedding;

import java.time.Instant;

public record EventStatusView(
		boolean closed,
		long version,
		Instant changedAt,
		String changedBy,
		String titleId,
		String titleEn,
		String messageId,
		String messageEn) {
}
