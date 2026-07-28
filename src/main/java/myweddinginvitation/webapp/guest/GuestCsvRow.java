package myweddinginvitation.webapp.guest;

public record GuestCsvRow(
		long rowNumber,
		String displayName,
		String whatsappNumber,
		String normalizedWhatsappNumber,
		String salutation,
		String category,
		Long categoryId,
		boolean plusOneAllowed,
		MessageLanguage preferredLanguage,
		String internalNote) {
}
