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

	@Test
	void guestFallbackPagesUseInvitationDesign() throws IOException {
		for (String page : new String[] {"closed", "unavailable", "home"}) {
			assertThat(resource("templates/guest/" + page + ".html"))
					.contains("/css/invitation.css", "guest-state");
		}
	}

	@Test
	void invitationUsesCorrectHeadingsImagesAndActionTargets() throws IOException {
		String guest = resource("templates/guest/invitation.html");
		String preview = resource("templates/admin/wedding/preview.html");
		assertThat(guest).contains("<h3 th:id=\"${'event-' + event.type}\"", "class=\"action-link\"")
				.doesNotContain("<h2 th:id=\"${'event-' + event.type}\"", "th:srcset=", "sizes=\"");
		assertThat(preview).contains("<h3 th:id=\"${'event-' + event.type}\"")
				.doesNotContain("<h2 th:id=\"${'event-' + event.type}\"", "th:srcset=", "sizes=\"");
	}

	@Test
	void invitationInteractionStylesProtectContrastTouchAndMotion() throws IOException {
		String css = resource("static/css/invitation.css");
		String js = resource("static/js/invitation-media.js");
		assertThat(css).contains(".cover-photo .cover-content {\n    background: rgb(12 20 16 / 82%)",
				"box-shadow: 0 0 0 5px #111 !important",
				"touch-action: pan-y", ".action-link", "min-height: 2.75rem");
		assertThat(js).contains("prefers-reduced-motion", "pointerdown", "pointerup",
				"setPointerCapture", "releasePointerCapture");
	}

	private String resource(String path) throws IOException {
		try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as(path).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
