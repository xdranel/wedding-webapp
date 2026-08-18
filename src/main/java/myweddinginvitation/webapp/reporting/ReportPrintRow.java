package myweddinginvitation.webapp.reporting;

import java.time.Instant;

import myweddinginvitation.webapp.rsvp.AttendanceResponse;

public record ReportPrintRow(
		String displayName,
		String categoryName,
		AttendanceResponse rsvpResponse,
		int plannedPeople,
		boolean checkedIn,
		int actualPeople,
		Instant checkedInAt) {
}
