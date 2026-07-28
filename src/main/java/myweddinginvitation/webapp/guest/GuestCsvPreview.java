package myweddinginvitation.webapp.guest;

import java.util.List;

public final class GuestCsvPreview {
	private final byte[] source;
	private final List<GuestCsvRow> rows;
	private final List<GuestCsvIssue> issues;

	GuestCsvPreview(byte[] source, List<GuestCsvRow> rows, List<GuestCsvIssue> issues) {
		this.source = source.clone();
		this.rows = List.copyOf(rows);
		this.issues = List.copyOf(issues);
	}

	public List<GuestCsvRow> rows() {
		return rows;
	}

	public List<GuestCsvIssue> issues() {
		return issues;
	}

	public List<GuestCsvIssue> errors() {
		return issues.stream().filter(issue -> issue.severity() == GuestCsvIssue.Severity.ERROR).toList();
	}

	public List<GuestCsvIssue> getErrors() {
		return errors();
	}

	public List<GuestCsvIssue> warnings() {
		return issues.stream().filter(issue -> issue.severity() == GuestCsvIssue.Severity.WARNING).toList();
	}

	public boolean hasErrors() {
		return !errors().isEmpty();
	}

	public boolean hasWarnings() {
		return !warnings().isEmpty();
	}

	byte[] source() {
		return source.clone();
	}
}
