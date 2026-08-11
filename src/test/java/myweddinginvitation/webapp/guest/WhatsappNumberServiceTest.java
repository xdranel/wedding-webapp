package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WhatsappNumberServiceTest {
	private final WhatsappNumberService numbers = new WhatsappNumberService();

	@ParameterizedTest
	@CsvSource({
			"ID,'0812 3456 7890',+6281234567890",
			"DE,'01512 3456789',+4915123456789",
			"MY,'012-345 6789',+60123456789",
			"US,'202-555-0123',+12025550123",
			"ID,'+49 1512 3456789',+4915123456789"
	})
	void normalizesNationalAndExplicitInternationalNumbers(String region, String raw, String expected) {
		assertThat(numbers.normalize(raw, region)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "123", "not-a-number" })
	void rejectsImpossibleNumbers(String raw) {
		assertThatThrownBy(() -> numbers.normalize(raw, "ID"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Enter a valid WhatsApp number.");
	}

	@org.junit.jupiter.api.Test
	void detectsRegionAndFallsBackWhenItCannotBeDetermined() {
		assertThat(numbers.regionFor("+4915123456789", "ID")).isEqualTo("DE");
		assertThat(numbers.regionFor("invalid", "ID")).isEqualTo("ID");
	}

	@org.junit.jupiter.api.Test
	void exposesEnglishCountryLabelsInStableOrder() {
		assertThat(numbers.supportedRegions())
				.extracting(WhatsappNumberService.RegionOption::code)
				.contains("DE", "ID", "MY", "US");
		assertThat(numbers.supportedRegions())
				.extracting(WhatsappNumberService.RegionOption::label)
				.isSorted()
				.contains("Germany (+49)", "Indonesia (+62)");
	}

	@org.junit.jupiter.api.Test
	void rejectsUnsupportedSubmittedRegion() {
		assertThatThrownBy(() -> numbers.normalize("+4915123456789", "XX"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Select a valid phone country.");
	}

	@org.junit.jupiter.api.Test
	void rejectsNullSubmittedRegion() {
		assertThatThrownBy(() -> numbers.normalize("+4915123456789", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Select a valid phone country.");
	}

	@ParameterizedTest
	@CsvSource({ "+6281234567890,true", "+0,false", "+000,false", "+６２８１２３４５６７８９０,false" })
	void validatesCanonicalE164Numbers(String number, boolean valid) {
		assertThat(numbers.isValidE164(number)).isEqualTo(valid);
	}
}
