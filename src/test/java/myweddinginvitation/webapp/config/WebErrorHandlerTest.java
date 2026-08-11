package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class WebErrorHandlerTest {
	@Test
	void oversizedMultipartRequestUsesNeutralPayloadTooLargePage() {
		var result = new WebErrorHandler().uploadTooLarge(new MaxUploadSizeExceededException(22_020_096));

		assertThat(result.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(result.getViewName()).isEqualTo("error/413");
		assertThat(result.getModel()).doesNotContainKey("reference");
	}
}
