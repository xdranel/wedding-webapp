package myweddinginvitation.webapp.guest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static myweddinginvitation.webapp.guest.GuestCsvIssue.Severity.ERROR;
import static myweddinginvitation.webapp.guest.GuestCsvIssue.Severity.WARNING;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

import myweddinginvitation.webapp.wedding.WeddingSettingsRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestCsvService {
	static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int MAX_ROWS = 2000;
	private static final List<String> CREATE_COLUMNS = List.of(
			"display_name",
			"whatsapp_number",
			"salutation",
			"category",
			"plus_one_allowed",
			"preferred_language",
			"internal_note");
	private static final String[] EXPORT_COLUMNS = {
			"display_name",
			"whatsapp_number",
			"salutation",
			"category",
			"plus_one_allowed",
			"preferred_language",
			"internal_note",
			"archive_state",
			"archived_at",
			"delivery_state",
			"first_sent_at",
			"last_sent_at",
			"created_at",
			"updated_at"
	};

	private final GuestRepository guests;
	private final GuestCategoryRepository categories;
	private final GuestService guestService;
	private final WhatsappNumberService numbers;
	private final WeddingSettingsRepository settings;

	public GuestCsvService(GuestRepository guests, GuestCategoryRepository categories, GuestService guestService,
			WhatsappNumberService numbers, WeddingSettingsRepository settings) {
		this.guests = guests;
		this.categories = categories;
		this.guestService = guestService;
		this.numbers = numbers;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public GuestCsvPreview preview(byte[] source) {
		return parse(source);
	}

	@Transactional
	public int importAll(GuestCsvPreview preview) {
		return importAll(preview, false);
	}

	@Transactional
	public int importAll(GuestCsvPreview preview, boolean acceptWarnings) {
		GuestCsvPreview current = parse(preview.source());
		if (current.hasErrors() || current.hasWarnings() && !acceptWarnings) {
			throw new IllegalArgumentException("CSV import contains unresolved issues.");
		}
		for (GuestCsvRow row : current.rows()) {
			guestService.create(new GuestForm(
					row.displayName(),
					row.salutation(),
					row.normalizedWhatsappNumber(),
					row.categoryId(),
					row.plusOneAllowed(),
					row.preferredLanguage(),
					row.internalNote()), true);
		}
		return current.rows().size();
	}

	public void template(OutputStream output) throws IOException {
		try (Writer writer = utf8BomWriter(output);
				CSVPrinter printer = new CSVPrinter(writer, outputFormat(CREATE_COLUMNS.toArray(String[]::new)))) {
			// Header only.
		}
	}

	@Transactional(readOnly = true)
	public void exportAll(OutputStream output) throws IOException {
		try (Writer writer = utf8BomWriter(output);
				CSVPrinter printer = new CSVPrinter(writer, outputFormat(EXPORT_COLUMNS))) {
			for (Guest guest : guests.findAllByOrderByDisplayNameAscIdAsc()) {
				printer.printRecord(
						spreadsheetText(guest.getDisplayName()),
						guest.getNormalizedWhatsappNumber(),
						spreadsheetText(guest.getSalutation()),
						guest.getCategory() == null ? "" : spreadsheetText(guest.getCategory().getDisplayName()),
						guest.isPlusOneAllowed(),
						guest.getPreferredLanguage(),
						spreadsheetText(guest.getInternalNote()),
						guest.isArchived() ? "ARCHIVED" : "ACTIVE",
						blankIfNull(guest.getArchivedAt()),
						guest.getDeliveryState(),
						blankIfNull(guest.getFirstSentAt()),
						blankIfNull(guest.getLastSentAt()),
						guest.getCreatedAt(),
						guest.getUpdatedAt());
			}
		}
	}

	private GuestCsvPreview parse(byte[] source) {
		List<GuestCsvRow> rows = new ArrayList<>();
		List<GuestCsvIssue> issues = new ArrayList<>();
		if (source.length > MAX_BYTES) {
			issues.add(new GuestCsvIssue(0, "file", "CSV files must be 2 MiB or smaller.", ERROR));
			return new GuestCsvPreview(source, rows, issues);
		}

		String text = new String(source, UTF_8);
		if (text.startsWith("\uFEFF")) {
			text = text.substring(1);
		}
		Character delimiter = delimiter(text);
		if (delimiter == null) {
			issues.add(new GuestCsvIssue(1, "file", "Use the exact seven-column CSV header.", ERROR));
			return new GuestCsvPreview(source, rows, issues);
		}

		Set<String> seenNumbers = new HashSet<>();
		try (CSVParser parser = CSVFormat.RFC4180.builder()
				.setDelimiter(delimiter)
				.setIgnoreEmptyLines(false)
				.get()
				.parse(new StringReader(text))) {
			boolean header = true;
			int dataRows = 0;
			for (CSVRecord record : parser) {
				if (header) {
					header = false;
					continue;
				}
				dataRows++;
				if (dataRows > MAX_ROWS) {
					issues.add(new GuestCsvIssue(2001, "file", "CSV files may contain at most 2,000 rows.", ERROR));
					break;
				}
				long rowNumber = dataRows + 1L;
				if (record.size() != CREATE_COLUMNS.size()) {
					issues.add(new GuestCsvIssue(rowNumber, "file", "Each row must contain exactly seven columns.", ERROR));
					continue;
				}
				rows.add(row(record, rowNumber, issues, seenNumbers));
			}
		} catch (IOException | RuntimeException exception) {
			issues.add(new GuestCsvIssue(0, "file", "The CSV file could not be parsed.", ERROR));
		}
		return new GuestCsvPreview(source, rows, issues);
	}

	private GuestCsvRow row(CSVRecord record, long rowNumber, List<GuestCsvIssue> issues, Set<String> seenNumbers) {
		String displayName = record.get(0).strip();
		String rawNumber = record.get(1).strip();
		String salutation = record.get(2).strip();
		String categoryName = record.get(3).strip();
		String plusOneText = record.get(4).strip();
		String languageText = record.get(5).strip();
		String note = emptyToNull(record.get(6));

		validateRequiredLength(displayName, 160, rowNumber, "display_name", issues);
		validateRequiredLength(rawNumber, 40, rowNumber, "whatsapp_number", issues);
		validateRequiredLength(salutation, 80, rowNumber, "salutation", issues);
		if (note != null && note.length() > 2000) {
			error(issues, rowNumber, "internal_note", "Must be 2,000 characters or fewer.");
		}

		String normalizedNumber = null;
		if (!rawNumber.isBlank() && rawNumber.length() <= 40) {
			try {
				normalizedNumber = numbers.normalize(rawNumber, defaultPhoneCountry());
				if (guests.existsByNormalizedWhatsappNumber(normalizedNumber) || !seenNumbers.add(normalizedNumber)) {
					issues.add(new GuestCsvIssue(rowNumber, "whatsapp_number",
							"This WhatsApp number is already used by a guest or earlier CSV row.", WARNING));
				}
			} catch (IllegalArgumentException exception) {
				error(issues, rowNumber, "whatsapp_number", exception.getMessage());
			}
		}

		Long categoryId = null;
		if (!categoryName.isBlank()) {
			GuestCategory category = categories
					.findByNormalizedName(GuestCategoryService.normalizeCategoryName(categoryName))
					.orElse(null);
			if (category == null) {
				error(issues, rowNumber, "category", "Category does not exist.");
			} else {
				categoryId = category.getId();
			}
		}

		boolean plusOne = false;
		if ("true".equalsIgnoreCase(plusOneText) || "false".equalsIgnoreCase(plusOneText)) {
			plusOne = Boolean.parseBoolean(plusOneText);
		} else {
			error(issues, rowNumber, "plus_one_allowed", "Use true or false.");
		}

		MessageLanguage language = null;
		try {
			language = MessageLanguage.valueOf(languageText.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			error(issues, rowNumber, "preferred_language", "Use ID or EN.");
		}

		return new GuestCsvRow(rowNumber, displayName, rawNumber, normalizedNumber, salutation, categoryName,
				categoryId, plusOne, language, note);
	}

	private Character delimiter(String text) {
		for (char candidate : new char[] { ',', ';' }) {
			try (CSVParser parser = CSVFormat.RFC4180.builder()
					.setDelimiter(candidate)
					.setIgnoreEmptyLines(false)
					.get()
					.parse(new StringReader(text))) {
				var iterator = parser.iterator();
				if (iterator.hasNext() && matchesHeader(iterator.next())) {
					return candidate;
				}
			} catch (IOException | RuntimeException exception) {
				// Try the other supported delimiter.
			}
		}
		return null;
	}

	private boolean matchesHeader(CSVRecord record) {
		if (record.size() != CREATE_COLUMNS.size()) {
			return false;
		}
		for (int index = 0; index < CREATE_COLUMNS.size(); index++) {
			if (!CREATE_COLUMNS.get(index).equals(record.get(index))) {
				return false;
			}
		}
		return true;
	}

	private String defaultPhoneCountry() {
		return settings.getSingleton().orElseThrow(NoSuchElementException::new).getDefaultPhoneCountry();
	}

	private void validateRequiredLength(String value, int max, long row, String column, List<GuestCsvIssue> issues) {
		if (value.isBlank()) {
			error(issues, row, column, "Must not be blank.");
		} else if (value.length() > max) {
			error(issues, row, column, "Must be " + max + " characters or fewer.");
		}
	}

	private void error(List<GuestCsvIssue> issues, long row, String column, String message) {
		issues.add(new GuestCsvIssue(row, column, message, ERROR));
	}

	private CSVFormat outputFormat(String[] header) {
		return CSVFormat.RFC4180.builder().setHeader(header).get();
	}

	private Writer utf8BomWriter(OutputStream output) throws IOException {
		output.write("\uFEFF".getBytes(UTF_8));
		return new OutputStreamWriter(output, UTF_8);
	}

	private Object blankIfNull(Object value) {
		return value == null ? "" : value;
	}

	private String spreadsheetText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return switch (value.charAt(0)) {
			case '=', '+', '-', '@', '\t', '\r', '\n' -> "'" + value;
			default -> value;
		};
	}

	private String emptyToNull(String value) {
		return value.isEmpty() ? null : value;
	}
}
