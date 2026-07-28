package myweddinginvitation.webapp.guest;

public record GuestCsvIssue(long row, String column, String message, Severity severity) {
	public enum Severity {
		ERROR,
		WARNING
	}
}
