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
	void staffPagesUseFocusedCheckInShell() throws IOException {
		for (String page : new String[] {"home", "preview", "result"}) {
			String html = resource("templates/checkin/" + page + ".html");
			assertThat(html).contains("/css/app.css", "fragments/staff-header", "staff-shell");
		}
	}

	@Test
	void searchTabStopsCameraThroughSharedLifecycle() throws IOException {
		String home = resource("templates/checkin/home.html");
		assertThat(home).contains("id=\"scan-tab\"", "id=\"search-tab\"", "aria-controls=\"scan-panel\"",
				"aria-controls=\"search-panel\"", "id=\"scanner-start\"", "id=\"scanner-stop\"",
				"id=\"scanner-video\"", "id=\"scanner-input\"");

		String scanner = resource("static/js/check-in-scanner.js");
		assertThat(scanner).containsSubsequence("function activateTab(",
				"if (activeTab.id !== 'scan-tab') stop();",
				"tab.setAttribute('aria-selected'", ".hidden = !selected");
	}

	@Test
	void administratorPagesUseExactActivePageKeys() throws IOException {
		String navigation = resource("templates/fragments/admin-navigation.html");
		for (String[] page : new String[][] {
				{"admin/home.html", "overview"},
				{"admin/wedding/overview.html", "wedding-settings"},
				{"admin/wedding/settings.html", "wedding-settings"},
				{"admin/wedding/event-status.html", "wedding-settings"},
				{"admin/wedding/event-status-confirm.html", "wedding-settings"},
				{"admin/wedding/partners.html", "wedding-partners"},
				{"admin/wedding/events.html", "wedding-events"},
				{"admin/wedding/story.html", "wedding-story"},
				{"admin/wedding/media.html", "wedding-media"},
				{"admin/wedding/preview-form.html", "wedding-preview"},
				{"admin/guests/detail.html", "guest-list"},
				{"admin/guests/form.html", "guest-list"},
				{"admin/guests/import.html", "guest-import-export"},
				{"admin/guests/rsvp.html", "rsvp"},
				{"admin/guest-categories/list.html", "guest-categories"},
				{"admin/message-templates/edit.html", "message-templates"},
				{"admin/message-templates/list.html", "message-templates"},
				{"admin/reminders/list.html", "reminders"},
				{"admin/greetings/list.html", "greetings"},
				{"admin/accounts/form.html", "staff-accounts"},
				{"admin/accounts/list.html", "staff-accounts"},
				{"admin/reports/index.html", "reports"},
				{"admin/system-status.html", "system-status"}
		}) {
			assertThat(resource("templates/" + page[0])).as(page[0]).contains("sidebar('" + page[1] + "')");
			assertThat(occurrences(navigation, "activePage == '" + page[1] + "'"))
					.as(page[1] + " active navigation mapping").isEqualTo(1);
		}
	}

	@Test
	void administratorNavigationExposesEveryApprovedDestinationInOrder() throws IOException {
		String navigation = resource("templates/fragments/admin-navigation.html");
		assertThat(navigation).containsSubsequence(
				">Dashboard</a>",
				"<p class=\"nav-heading\">Wedding</p>",
				"th:href=\"@{/admin/wedding/settings}\"", ">Settings</a>",
				"th:href=\"@{/admin/wedding/partners}\"", ">Partners</a>",
				"th:href=\"@{/admin/wedding/events}\"", ">Events</a>",
				"th:href=\"@{/admin/wedding/story}\"", ">Story</a>",
				"th:href=\"@{/admin/wedding/media}\"", ">Media</a>",
				"th:href=\"@{/admin/wedding/preview}\"", ">Preview</a>",
				"<p class=\"nav-heading\">Guests</p>",
				">Guest List</a>", ">Categories</a>", ">Import/Export</a>",
				"<p class=\"nav-heading\">Communication</p>",
				">Templates</a>",
				">Invitations</a>",
				">Reminders</a>",
				"<p class=\"nav-heading\">Attendance</p>",
				">RSVP</a>",
				">Greetings</a>",
				">Check-ins</a>",
				"<p class=\"nav-heading\">Operations</p>",
				">Reports</a>",
				">Staff Accounts</a>",
				">System Status</a>")
				.doesNotContain("@{/admin/wedding/event-status}", ">Event status</a>");
		for (String activePage : new String[] {"overview", "wedding-settings", "wedding-partners",
				"wedding-events", "wedding-story", "wedding-media", "wedding-preview", "guest-list",
				"guest-categories", "guest-import-export", "message-templates", "invitations", "reminders",
				"rsvp", "greetings", "check-ins", "reports", "staff-accounts", "system-status"}) {
			assertThat(occurrences(navigation, "activePage == '" + activePage + "'"))
					.as(activePage + " active navigation mapping").isEqualTo(1);
		}
		assertThat(resource("templates/admin/guests/list.html")).contains("sidebar(${navigationPage})");
		assertThat(resource("templates/admin/wedding/settings.html")).contains("@{/admin/wedding/event-status}");
	}

	@Test
	void administratorStylesSupportResponsiveWorkspaceComponents() throws IOException {
		String css = resource("static/css/app.css");
		assertThat(css).contains(".filter-panel", ".action-cluster", ".mobile-card-list", "content: attr(data-label)",
				".page-content img {", "max-inline-size: 100%;", "block-size: auto;",
				".app-navigation", "position: sticky", ".navigation-toggle", ".filter-disclosure",
				".action-overflow");
		String navigation = resource("templates/fragments/admin-navigation.html");
		assertThat(navigation).contains("<details th:fragment=\"sidebar(activePage)\" class=\"app-navigation\" open>",
				"<summary class=\"navigation-toggle\">Administrator menu</summary>");
		for (String page : new String[] {"admin/guests/list.html", "admin/reminders/list.html", "admin/reports/index.html"}) {
			assertThat(resource("templates/" + page)).as(page)
					.contains("<details class=\"filter-disclosure\" open>", "<summary>Filters</summary>");
		}
		for (String page : new String[] {"admin/guests/detail.html", "admin/reminders/list.html",
				"admin/accounts/list.html", "admin/reports/index.html"}) {
			assertThat(resource("templates/" + page)).as(page).contains("class=\"action-overflow\"");
		}
	}

	@Test
	void passwordFieldsConditionallyAssociateAndRenderErrors() throws IOException {
		String password = resource("templates/account/password.html");
		fieldError(password, "currentPassword", "current-password-error");
		fieldError(password, "newPassword", "new-password-error");
		fieldError(password, "confirmPassword", "confirm-password-error");
		assertThat(password).contains("th:if=\"${#fields.hasErrors('currentPassword')}\"",
				"th:if=\"${#fields.hasErrors('newPassword')}\"",
				"th:if=\"${#fields.hasErrors('confirmPassword')}\"");
	}

	@Test
	void internalControlBordersMeetStableContrastContract() throws IOException {
		String css = resource("static/css/app.css");
		assertThat(css).contains("--color-border: #6b7280;",
				".button-secondary { background: transparent; border-color: var(--color-border); color: inherit; }",
				"input:hover, select:hover, textarea:hover,",
				"input:focus, select:focus, textarea:focus");
	}

	@Test
	void checkInSearchResultNameKeepsTouchTarget() throws IOException {
		assertThat(resource("static/css/app.css"))
				.contains(".result-name {", "min-block-size: 44px;");
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

	@Test
	void accountAndErrorPagesUseInternalDesignSystem() throws IOException {
		for (String page : new String[] {"login.html", "account/password.html", "error/403.html", "error/413.html", "error/500.html"}) {
			assertThat(resource("templates/" + page)).as(page).contains("/css/app.css");
		}
	}

	@Test
	void passwordSignOutUsesPrimaryButton() throws IOException {
		assertThat(resource("templates/account/password.html"))
				.contains("<button class=\"button\" type=\"submit\">Sign out</button>")
				.doesNotContain("<button class=\"button button-secondary\" type=\"submit\">Sign out</button>");
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
