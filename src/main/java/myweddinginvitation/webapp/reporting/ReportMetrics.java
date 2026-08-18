package myweddinginvitation.webapp.reporting;

public record ReportMetrics(
		long invitations,
		long potentialPeople,
		long rsvpAttending,
		long rsvpDeclined,
		long rsvpMissing,
		long plannedPeople,
		long checkedInInvitations,
		long actualPeople,
		long attendingNotCheckedIn,
		long remainingPlannedPeople,
		long initialSent,
		long initialUnsent,
		long rsvpReminderSent,
		long rsvpReminderUnsent,
		long eventReminderSent,
		long eventReminderUnsent,
		long pendingGreetings) {

	public static ReportMetrics empty() {
		return new ReportMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
}
