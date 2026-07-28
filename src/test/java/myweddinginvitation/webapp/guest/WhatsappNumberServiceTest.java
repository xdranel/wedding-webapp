package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WhatsappNumberServiceTest {
	private final WhatsappNumberService numbers = new WhatsappNumberService();

	@ParameterizedTest
	@CsvSource({
			"'0812 3456 7890',+6281234567890",
			"'62-812-3456-7890',+6281234567890",
			"'+62 812 3456 7890',+6281234567890"
	})
	void normalizesIndonesianNumbers(String raw, String expected) {
		assertThat(numbers.normalize(raw, "ID")).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "123", "not-a-number" })
	void rejectsImpossibleNumbers(String raw) {
		assertThatThrownBy(() -> numbers.normalize(raw, "ID"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Enter a valid WhatsApp number.");
	}
}
