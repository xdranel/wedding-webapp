package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class WebErrorHandlerTest {
	@Test
	void oversizedMultipartRequestUsesNeutralPayloadTooLargePage() {
		var result = new WebErrorHandler().uploadTooLarge(new MaxUploadSizeExceededException(22_020_096));

		assertThat(result.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(result.getViewName()).isEqualTo("error/413");
		assertThat(result.getModel()).doesNotContainKey("reference");
	}

	@Test
	void unsupportedMethodUsesNeutralNotFoundInsteadOfInternalError() throws Exception {
		MockMvcBuilders.standaloneSetup(new ErrorProbeController()).setControllerAdvice(new WebErrorHandler()).build()
				.perform(post("/only-get"))
				.andExpect(status().isNotFound())
				.andExpect(view().name("guest/unavailable"))
				.andExpect(model().attributeDoesNotExist("reference"));
	}

	@Test
	void unrelatedExceptionsRemainNeutralInternalErrors() throws Exception {
		MockMvcBuilders.standaloneSetup(new ErrorProbeController()).setControllerAdvice(new WebErrorHandler()).build()
				.perform(get("/failure"))
				.andExpect(status().isInternalServerError())
				.andExpect(view().name("error/500"))
				.andExpect(model().attributeExists("reference"));
	}

	@RestController
	static class ErrorProbeController {
		@GetMapping("/only-get")
		void onlyGet() {
		}

		@GetMapping("/failure")
		void failure() {
			throw new IllegalStateException("private-probe");
		}
	}
}
