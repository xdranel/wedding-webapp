package myweddinginvitation.webapp.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PresentationStructureTest {
	private static final String[] ADMIN_WORKSPACE_PAGES = {
			"admin/wedding/overview.html",
			"admin/wedding/settings.html",
			"admin/wedding/partners.html",
			"admin/wedding/events.html",
			"admin/wedding/story.html",
			"admin/wedding/media.html",
			"admin/wedding/preview-form.html",
			"admin/wedding/event-status.html",
			"admin/wedding/event-status-confirm.html",
			"admin/guests/list.html",
			"admin/guests/detail.html",
			"admin/guests/form.html",
			"admin/guests/import.html",
			"admin/guests/rsvp.html",
			"admin/guest-categories/list.html",
			"admin/message-templates/edit.html",
			"admin/message-templates/list.html",
			"admin/reminders/list.html",
			"admin/greetings/list.html",
			"admin/accounts/form.html",
			"admin/accounts/list.html",
			"admin/reports/index.html",
			"admin/system-status.html"
	};

	@Test
	void adminHomeUsesSharedNavigationAndInternalStyles() throws IOException {
		String html = resource("templates/admin/home.html");
		assertThat(html).contains("/css/app.css", "fragments/admin-navigation", "app-shell");
	}

	@Test
	void administratorWorkspacePagesUseSharedShell() throws IOException {
		for (String page : ADMIN_WORKSPACE_PAGES) {
			String html = resource("templates/" + page);
			assertThat(html).as(page).contains("/css/app.css", "fragments/admin-navigation", "app-shell");
		}
	}

	@Test
	void taskFivePagesUseExactActivePageKeys() throws IOException {
		String navigation = resource("templates/fragments/admin-navigation.html");
		for (String[] page : new String[][] {
				{"admin/message-templates/edit.html", "messages"},
				{"admin/message-templates/list.html", "messages"},
				{"admin/reminders/list.html", "reminders"},
				{"admin/greetings/list.html", "greetings"},
				{"admin/accounts/form.html", "accounts"},
				{"admin/accounts/list.html", "accounts"},
				{"admin/reports/index.html", "reports"},
				{"admin/system-status.html", "system-status"}
		}) {
			assertThat(resource("templates/" + page[0])).as(page[0]).contains("sidebar('" + page[1] + "')");
			assertThat(occurrences(navigation, "activePage == '" + page[1] + "'"))
					.as(page[1] + " active navigation mapping").isEqualTo(1);
		}
	}

	@Test
	void administratorNavigationKeepsApprovedGroupsAndLinkOrder() throws IOException {
		assertThat(resource("templates/fragments/admin-navigation.html")).containsSubsequence(
				"<p class=\"nav-heading\">Communication</p>",
				">Message templates</a>",
				">Reminders</a>",
				"<p class=\"nav-heading\">Attendance</p>",
				">Greetings</a>",
				"<p class=\"nav-heading\">Operations</p>",
				">Reports</a>",
				">Staff accounts</a>",
				">System status</a>");
	}

	@Test
	void administratorStylesSupportResponsiveWorkspaceComponents() throws IOException {
		String css = resource("static/css/app.css");
		assertThat(css).contains(".filter-panel", ".action-cluster", ".mobile-card-list", "content: attr(data-label)",
				".page-content img {", "max-inline-size: 100%;", "block-size: auto;");
	}

	@Test
	void administratorWorkspaceFormsConditionallyAssociateFieldErrors() throws IOException {
		String settings = resource("templates/admin/wedding/settings.html");
		fieldError(settings, "coupleTitle", "couple-title-error");
		fieldError(settings, "openingTextId", "opening-text-id-error");
		fieldError(settings, "openingTextEn", "opening-text-en-error");
		fieldError(settings, "closingTextId", "closing-text-id-error");
		fieldError(settings, "closingTextEn", "closing-text-en-error");
		fieldError(settings, "timeZone", "time-zone-error");
		fieldError(settings, "rsvpDeadline", "rsvp-deadline-error");
		fieldError(settings, "defaultPhoneCountry", "phone-country-error");
		fieldError(settings, "accentColor", "accent-color-error");
		fieldError(settings, "fontPreset", "font-preset-error");

		String partners = resource("templates/admin/wedding/partners.html");
		fieldError(partners, "fullName", "full-name-error");
		fieldError(partners, "nickname", "nickname-error");
		fieldError(partners, "photo", "photo-error");
		fieldError(partners, "childOfLabelId", "child-of-label-id-error");
		fieldError(partners, "childOfLabelEn", "child-of-label-en-error");
		fieldError(partners, "parentsNamesId", "parents-names-id-error");
		fieldError(partners, "parentsNamesEn", "parents-names-en-error");
		fieldError(partners, "instagramUrl", "instagram-url-error");

		String events = resource("templates/admin/wedding/events.html");
		fieldError(events, "eventDate", "ceremony-date-error");
		fieldError(events, "startTime", "ceremony-start-error");
		fieldError(events, "endTime", "ceremony-end-error");
		fieldError(events, "venueName", "ceremony-venue-error");
		fieldError(events, "addressId", "ceremony-address-id-error");
		fieldError(events, "mapUrl", "ceremony-map-error");
		fieldError(events, "eventDate", "reception-date-error");
		fieldError(events, "startTime", "reception-start-error");
		fieldError(events, "endTime", "reception-end-error");
		fieldError(events, "venueName", "reception-venue-error");
		fieldError(events, "addressId", "reception-address-id-error");
		fieldError(events, "mapUrl", "reception-map-error");

		String story = resource("templates/admin/wedding/story.html");
		fieldError(story, "titleId", "story-title-id-error", 2);
		fieldError(story, "titleEn", "story-title-en-error", 2);
		fieldError(story, "bodyId", "story-body-id-error", 2);
		fieldError(story, "bodyEn", "story-body-en-error", 2);

		String media = resource("templates/admin/wedding/media.html");
		fieldError(media, "altText", "photo-alt-error");
		fieldError(media, "altText", "photo-alt-edit-error");

		String preview = resource("templates/admin/wedding/preview-form.html");
		fieldError(preview, "salutation", "salutation-error");
		fieldError(preview, "guestName", "guest-name-error");
		fieldError(preview, "language", "language-error");

		String eventStatus = resource("templates/admin/wedding/event-status.html");
		fieldError(eventStatus, "titleId", "closed-title-id-error");
		fieldError(eventStatus, "titleEn", "closed-title-en-error");
		fieldError(eventStatus, "messageId", "closed-message-id-error");
		fieldError(eventStatus, "messageEn", "closed-message-en-error");
		fieldError(resource("templates/admin/wedding/event-status-confirm.html"), "confirmed", "confirmed-error");

		String guest = resource("templates/admin/guests/form.html");
		fieldError(guest, "displayName", "display-name-error");
		fieldError(guest, "salutation", "guest-salutation-error");
		fieldError(guest, "phoneRegion", "phone-region-error");
		fieldError(guest, "whatsappNumber", "whatsapp-number-error");

		String rsvp = resource("templates/admin/guests/rsvp.html");
		fieldError(rsvp, "response", "response-error");
		fieldError(rsvp, "plannedAttendeeCount", "planned-attendee-count-error");

		String categories = resource("templates/admin/guest-categories/list.html");
		fieldError(categories, "name", "category-name-error");
		fieldError(categories, "name", "category-edit-name-error");

		fieldError(resource("templates/admin/message-templates/edit.html"), "body", "template-body-error");

		String accounts = resource("templates/admin/accounts/form.html");
		fieldError(accounts, "username", "username-error");
		fieldError(accounts, "temporaryPassword", "temporary-password-error");
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

	private void fieldError(String html, String field, String errorId) {
		fieldError(html, field, errorId, 1);
	}

	private void fieldError(String html, String field, String errorId, int expectedOccurrences) {
		String description = "aria-describedby=${#fields.hasErrors('" + field + "')} ? '" + errorId + "' : null";
		assertThat(occurrences(html, description)).as(field + " described by " + errorId)
				.isEqualTo(expectedOccurrences);
		assertThat(occurrences(html, "id=\"" + errorId + "\"")).as(errorId)
				.isEqualTo(expectedOccurrences);
	}

	private int occurrences(String text, String marker) {
		int count = 0;
		for (int index = text.indexOf(marker); index >= 0; index = text.indexOf(marker, index + marker.length())) count++;
		return count;
	}
}
