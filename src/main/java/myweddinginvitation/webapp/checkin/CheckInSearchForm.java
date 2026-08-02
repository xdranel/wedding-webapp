package myweddinginvitation.webapp.checkin;

public record CheckInSearchForm(String q) {
	public String query() {
		return q == null ? "" : q.strip();
	}

	public boolean isPhoneSuffix() {
		return query().matches("\\d{4}");
	}

	public boolean isValid() {
		return isPhoneSuffix() || (query().length() >= 2 && query().codePoints().anyMatch(Character::isLetter));
	}
}
