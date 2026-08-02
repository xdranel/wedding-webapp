package myweddinginvitation.webapp.checkin;

public record CheckInOutcome(boolean duplicate, CheckInService.CheckInView checkIn) {
	static CheckInOutcome checkedIn(CheckInService.CheckInView checkIn) {
		return new CheckInOutcome(false, checkIn);
	}

	static CheckInOutcome duplicate(CheckInService.CheckInView checkIn) {
		return new CheckInOutcome(true, checkIn);
	}
}
