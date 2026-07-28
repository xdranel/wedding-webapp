package myweddinginvitation.webapp.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import myweddinginvitation.webapp.guest.MessageLanguage;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class MessageTemplateServiceTest {
	@Autowired
	MessageTemplateService service;

	@Autowired
	MessageTemplateRepository templates;

	@Test
	void rendersApprovedPlaceholdersAndEmptiesMissingOptionalValues() {
		String rendered = service.renderBody(
				"Untuk {{salutation}} {{guest_name}}, {{invitation_link}} {{ceremony_date}} {{reception_location}}",
				values());

		assertThat(rendered).isEqualTo("Untuk Ibu Sari, https://invite.example/i/x  ");
	}

	@Test
	void rejectsUnsupportedOrInvalidTemplateBodies() {
		assertThatThrownBy(() -> service.validate("Hello {{guest_name}} {{custom_script}}"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("custom_script");
		assertThatThrownBy(() -> service.validate("Hello {{guest_name"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.validate(" "))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.validate("x".repeat(4001)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void readsTheExactlySixSeededTypeLanguageTemplates() {
		assertThat(service.findAll())
				.extracting(MessageTemplate::getType, MessageTemplate::getLanguage)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple(MessageType.INVITATION, MessageLanguage.ID),
						org.assertj.core.groups.Tuple.tuple(MessageType.INVITATION, MessageLanguage.EN),
						org.assertj.core.groups.Tuple.tuple(MessageType.RSVP_REMINDER, MessageLanguage.ID),
						org.assertj.core.groups.Tuple.tuple(MessageType.RSVP_REMINDER, MessageLanguage.EN),
						org.assertj.core.groups.Tuple.tuple(MessageType.EVENT_REMINDER, MessageLanguage.ID),
						org.assertj.core.groups.Tuple.tuple(MessageType.EVENT_REMINDER, MessageLanguage.EN));
	}

	@Test
	void staleUpdateDoesNotOverwriteTheCurrentTemplate() {
		MessageTemplate template = templates.findByTypeAndLanguage(MessageType.INVITATION, MessageLanguage.EN).orElseThrow();
		service.update(template.getId(), template.getVersion(), "Dear {{guest_name}}");

		assertThatThrownBy(() -> service.update(template.getId(), template.getVersion(), "Stale body"))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(templates.findById(template.getId()).orElseThrow().getBody()).isEqualTo("Dear {{guest_name}}");
	}

	private MessageTemplateValues values() {
		return new MessageTemplateValues("Ibu", "Sari", "Rama & Shinta", "https://invite.example/i/x",
				null, null, null, null, null);
	}
}
