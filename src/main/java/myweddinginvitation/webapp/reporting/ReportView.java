package myweddinginvitation.webapp.reporting;

import java.util.List;

public record ReportView(ReportMetrics totals, List<ReportCategoryView> categories) {
	public ReportView {
		categories = List.copyOf(categories);
	}
}
