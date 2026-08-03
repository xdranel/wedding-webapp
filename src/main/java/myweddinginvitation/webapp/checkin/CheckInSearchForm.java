package myweddinginvitation.webapp.checkin;

public record CheckInSearchForm(String q) {
	// Search text cannot usefully exceed the guest display-name column.
	public static final int MAX_QUERY_LENGTH = 160;

	public String query() {
		return q == null ? "" : q.strip();
	}

	public boolean isPhoneSuffix() {
		return query().matches("\\d{4}");
	}

	public boolean isValid() {
		return query().length() <= MAX_QUERY_LENGTH
				&& (isPhoneSuffix() || (query().length() >= 2 && query().codePoints().anyMatch(Character::isLetter)));
	}
}
