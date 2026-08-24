package myweddinginvitation.webapp.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PresentationStructureTest {
	@Test
	void adminHomeUsesSharedNavigationAndInternalStyles() throws IOException {
		String html = resource("templates/admin/home.html");
		assertThat(html).contains("/css/app.css", "fragments/admin-navigation", "app-shell");
	}

	@Test
	void invitationProvidesCinematicCoverAndProgressiveNavigation() throws IOException {
		String html = resource("templates/guest/invitation.html");
		assertThat(html).contains("guest-navigation", "cover-content", "section-heading", "noscript");
		assertThat(html).contains("id=\"couple\"", "id=\"events\"", "id=\"rsvp\"");
	}

	@Test
	void invitationCssProvidesFallbackAndReducedMotion() throws IOException {
		String css = resource("static/css/invitation.css");
		assertThat(css).contains(".cover-fallback", "prefers-reduced-motion", ":focus-visible");
	}

	private String resource(String path) throws IOException {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as(path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
