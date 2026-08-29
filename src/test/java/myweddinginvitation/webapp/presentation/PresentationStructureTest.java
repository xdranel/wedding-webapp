package myweddinginvitation.webapp.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
				{"admin/wedding/overview.html", "wedding-publication"},
				{"admin/wedding/settings.html", "wedding-settings"},
				{"admin/wedding/event-status.html", "wedding-publication"},
				{"admin/wedding/event-status-confirm.html", "wedding-publication"},
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
					.as(page[1] + " active navigation mapping").isGreaterThanOrEqualTo(1);
		}
	}

	@Test
	void administratorNavigationExposesEveryApprovedDestinationInOrder() throws IOException {
		String navigation = resource("templates/fragments/admin-navigation.html");
		assertThat(navigation).containsSubsequence(
				">Dashboard</a>",
				"<summary>Wedding</summary>",
				">Publication &amp; Event Status</a>",
				"th:href=\"@{/admin/wedding/settings}\"", ">Settings</a>",
				"th:href=\"@{/admin/wedding/partners}\"", ">Partners</a>",
				"th:href=\"@{/admin/wedding/events}\"", ">Events</a>",
				"th:href=\"@{/admin/wedding/story}\"", ">Story</a>",
				"th:href=\"@{/admin/wedding/media}\"", ">Media</a>",
				"th:href=\"@{/admin/wedding/preview}\"", ">Preview</a>",
				"<summary>Guests</summary>",
				">Guest List</a>", ">Categories</a>", ">Import/Export</a>",
				"<summary>Communication</summary>",
				">Templates</a>",
				">Invitations</a>",
				">Reminders</a>",
				"<summary>Attendance</summary>",
				">RSVP</a>",
				">Greetings</a>",
				">Check-ins</a>",
				"<summary>Operations</summary>",
				">Reports</a>",
				">Staff Accounts</a>",
				">System Status</a>")
				.doesNotContain("@{/admin/wedding/event-status}", ">Event status</a>");
		for (String activePage : new String[] {"overview", "wedding-publication", "wedding-settings", "wedding-partners",
				"wedding-events", "wedding-story", "wedding-media", "wedding-preview", "guest-list",
				"guest-categories", "guest-import-export", "message-templates", "invitations", "reminders",
				"rsvp", "greetings", "check-ins", "reports", "staff-accounts", "system-status"}) {
			assertThat(occurrences(navigation, "activePage == '" + activePage + "'"))
					.as(activePage + " active navigation mapping").isGreaterThanOrEqualTo(1);
		}
		assertThat(resource("templates/admin/guests/list.html")).contains("sidebar(${navigationPage})");
		assertThat(resource("templates/admin/wedding/settings.html")).contains("@{/admin/wedding/event-status}");
	}

	@Test
	void administratorNavigationIsVisibleOnDesktopAndClosesTheMobileDrawer() throws IOException {
		String navigation = resource("templates/fragments/admin-navigation.html");
		assertThat(navigation)
				.contains("class=\"app-navigation\" open", "/js/admin-navigation.js")
				.contains("class=\"nav-group\"")
				.contains("th:open=\"${activePage == 'wedding-publication' or activePage == 'wedding-settings' or activePage == 'wedding-partners' or activePage == 'wedding-events' or activePage == 'wedding-story' or activePage == 'wedding-media' or activePage == 'wedding-preview'}\"")
				.contains("th:open=\"${activePage == 'guest-list' or activePage == 'guest-categories' or activePage == 'guest-import-export'}\"")
				.contains("th:open=\"${activePage == 'message-templates' or activePage == 'invitations' or activePage == 'reminders'}\"")
				.contains("th:open=\"${activePage == 'rsvp' or activePage == 'greetings' or activePage == 'check-ins'}\"")
				.contains("th:open=\"${activePage == 'reports' or activePage == 'staff-accounts' or activePage == 'system-status'}\"");
		assertThat(resource("static/js/admin-navigation.js"))
				.contains("matchMedia('(min-width: 48.001rem)')", "navigation.open = desktop.matches",
						"desktop.addEventListener?.('change', sync)");
	}

	@Test
	void dashboardSearchSubmitsExistingGuestQuery() throws IOException {
		assertThat(resource("templates/admin/home.html"))
				.contains("method=\"get\"", "th:action=\"@{/admin/guests}\"",
						"name=\"query\"", "type=\"submit\"");
	}

	@Test
	void administratorStylesSupportResponsiveWorkspaceComponents() throws IOException {
		String css = resource("static/css/app.css");
		assertThat(css).contains(".filter-panel", ".action-cluster", ".mobile-card-list", "content: attr(data-label)",
				".page-content img {", "max-inline-size: 100%;", "block-size: auto;",
				".page-content > * { inline-size: 100%; max-inline-size: none; }", ".table-card { inline-size: 100%;",
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
	void passwordPageRendersRoleIdentityText() throws IOException {
		String password = resource("templates/account/password.html");
		assertThat(password)
				.contains("password-page", "password-brand", "auth-identity",
						"th:text=\"${accountRole == T(myweddinginvitation.webapp.account.AccountRole).ADMIN} ? 'Administrator' : 'Staff Check-in'\"");
		assertThat(resource("static/css/app.css"))
				.contains(".password-page", ".password-brand");
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
	void nativeChoiceControlsKeepCompactVisualsAndSeparateLabelTouchTargets() throws IOException {
		assertThat(resource("static/css/app.css"))
				.contains("input[type=\"checkbox\"], input[type=\"radio\"] { inline-size: 1.25rem; block-size: 1.25rem; min-block-size: 0; }",
						"label:has(input[type=\"checkbox\"], input[type=\"radio\"])",
						"input[type=\"checkbox\"] + label, input[type=\"radio\"] + label { display: flex; align-items: center; min-block-size: 44px;");
	}

	@Test
	void checkInSearchResultNameKeepsTouchTarget() throws IOException {
		assertThat(resource("static/css/app.css"))
				.contains(".result-name {", "min-block-size: 44px;");
	}

	@Test
	void checkInControlsUseCompactLabelsWithoutChangingBindings() throws IOException {
		String preview = resource("templates/checkin/preview.html");
		assertThat(preview).contains(
				"<label class=\"segmented-choice\" th:if=\"${preview.plusOneAllowed}\"><input type=\"radio\" th:field=\"*{actualCount}\" value=\"1\"> 1</label>",
				"<label class=\"segmented-choice\" th:if=\"${preview.plusOneAllowed}\"><input type=\"radio\" th:field=\"*{actualCount}\" value=\"2\"> 2</label>",
				"<input th:unless=\"${preview.plusOneAllowed}\" type=\"hidden\" th:field=\"*{actualCount}\" value=\"1\">",
				"<label class=\"check-control\" th:if=\"${preview.rsvpChangeRequired}\"><input type=\"checkbox\" th:field=\"*{acceptRsvpChange}\"> I accept the RSVP change</label>");
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
		assertThat(html).contains("guest-navigation", "cover-content", "section-heading", "noscript",
				"<details class=\"language-switch\"", "id=\"welcome\" tabindex=\"-1\"");
		assertThat(html).contains("id=\"couple\"", "id=\"events\"",
				"id=\"rsvp\" class=\"invitation-section rsvp rsvp-card\"");
	}

	@Test
	void invitationCssProvidesFallbackAndReducedMotion() throws IOException {
		String css = resource("static/css/invitation.css");
		assertThat(css).contains(".cover-fallback", "prefers-reduced-motion", ":focus-visible",
				".rsvp-card,\n.qr-card {\n    display: block;\n    margin-inline: auto;",
				".qr-card {\n    text-align: center;", ".qr-card img {\n    margin-inline: auto;");
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
	void previewPagesKeepAdminAndNewTabActions() throws IOException {
		for (String page : new String[] {"preview-form.html", "preview.html"}) {
			assertThat(resource("templates/admin/wedding/" + page)).as(page)
					.contains("Back to Wedding Admin", "Open in new tab", "formtarget=\"_blank\"");
		}
	}

	@Test
	void previewControlsUseReadableDarkHeaderContrast() throws IOException {
		assertThat(resource("static/css/invitation.css"))
				.contains(".preview-controls a { color: white; }",
						".preview-controls button { background: white; color: #17201c; }",
						".preview-page { background: var(--ink); }");
	}

	@Test
	void musicControlAppearsAfterRevealAsAnAccessibleIcon() throws IOException {
		String guest = resource("templates/guest/invitation.html");
		String preview = resource("templates/admin/wedding/preview.html");
		String css = resource("static/css/invitation.css");
		for (String html : new String[] {guest, preview}) {
			assertThat(html).contains("id=\"audio-toggle\"", "aria-label=",
					"data-play-label=", "data-pause-label=", "class=\"audio-icon audio-icon-on\"",
					"class=\"audio-icon audio-icon-off\"")
					.doesNotContain("🔊", "🔇");
		}
		assertThat(css).contains("#audio-toggle {\n    align-items: center;", "display: none;",
				".invitation-open #audio-toggle {\n    display: inline-flex;",
				"#audio-toggle[aria-pressed=\"true\"] .audio-icon-on",
				"#audio-toggle[aria-pressed=\"true\"] .audio-icon-off");
	}

	@Test
	void invitationInteractionStylesProtectContrastTouchAndMotion() throws IOException {
		String css = resource("static/css/invitation.css");
		String js = resource("static/js/invitation-media.js");
		assertThat(css).contains(".cover-photo .cover-content {\n    background: rgb(12 20 16 / 82%)",
				"box-shadow: 0 0 0 5px #111 !important",
				"touch-action: pan-y", ".action-link", "min-height: 2.75rem");
		assertThat(js).contains("prefers-reduced-motion", "pointerdown", "pointerup",
				"document.addEventListener('visibilitychange'",
				"if (document.hidden && audio && !audio.paused) audio.pause()",
				"welcome.focus({ preventScroll: true })", "event.target.closest('.gallery-controls')")
				.doesNotContain("dialog.setPointerCapture", "dialog.releasePointerCapture");
	}

	@Test
	void accountAndErrorPagesUseInternalDesignSystem() throws IOException {
		for (String page : new String[] {"login.html", "account/password.html", "error/403.html", "error/413.html", "error/500.html"}) {
			assertThat(resource("templates/" + page)).as(page).contains("/css/app.css");
		}
	}

	@Test
	void passwordSignOutIsSeparatedAsASecondaryAction() throws IOException {
		assertThat(resource("templates/account/password.html"))
				.contains("password-sign-out",
						"<button class=\"button button-secondary\" type=\"submit\">Sign out</button>");
		assertThat(resource("static/css/app.css"))
				.contains(".password-sign-out {", "border-block-start:", "margin-block-start:");
	}

	@Test
	void designDocumentsNativeLanguageDisclosureWithoutBrowserStorage() throws IOException {
		String design = Files.readString(Path.of("docs/DESIGN.md"));
		assertThat(design)
				.contains("native disclosure", "language=ID", "language=EN")
				.doesNotContain("ID | EN switch", "stored on the device");
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
