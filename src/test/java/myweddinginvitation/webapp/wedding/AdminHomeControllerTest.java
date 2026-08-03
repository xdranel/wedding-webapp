package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import myweddinginvitation.webapp.checkin.CheckInService;
import myweddinginvitation.webapp.rsvp.RsvpService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class AdminHomeControllerTest {
	@Test
	void dashboardIncludesCheckInSummary() {
		var model = new ConcurrentModel();
		CheckInService checkIns = mock(CheckInService.class);
		CheckInService.CheckInSummary summary = new CheckInService.CheckInSummary(3, 4);
		when(checkIns.summary()).thenReturn(summary);

		new AdminHomeController(mock(RsvpService.class), checkIns).home(model);

		assertThat(model).containsEntry("checkInSummary", summary);
	}
}
