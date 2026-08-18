package myweddinginvitation.webapp.config;

import java.time.Instant;
import java.util.List;

public record SystemStatusView(List<SystemCheck> checks, Instant checkedAt) {
	public SystemStatusView {
		checks = List.copyOf(checks);
	}
}
