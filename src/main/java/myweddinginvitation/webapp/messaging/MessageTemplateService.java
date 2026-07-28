package myweddinginvitation.webapp.messaging;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import myweddinginvitation.webapp.guest.MessageLanguage;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageTemplateService {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");
	private static final Set<String> ALLOWED = Set.of(
			"salutation", "guest_name", "couple_name", "invitation_link",
			"rsvp_deadline", "ceremony_date", "ceremony_location",
			"reception_date", "reception_location");
	private final MessageTemplateRepository templates;

	public MessageTemplateService(MessageTemplateRepository templates) {
		this.templates = templates;
	}

	@Transactional(readOnly = true)
	public List<MessageTemplate> findAll() {
		return templates.findAll(Sort.by("type").and(Sort.by("language")));
	}

	@Transactional(readOnly = true)
	public MessageTemplate get(long id) {
		return templates.findById(id).orElseThrow();
	}

	@Transactional
	public void update(long id, long version, String body) {
		validate(body);
		MessageTemplate template = get(id);
		if (template.getVersion() != version) {
			throw new OptimisticLockingFailureException("Message template has changed");
		}
		template.update(body);
		templates.saveAndFlush(template);
	}

	@Transactional(readOnly = true)
	public String render(MessageType type, MessageLanguage language, MessageTemplateValues values) {
		return renderBody(templates.findByTypeAndLanguage(type, language).orElseThrow().getBody(), values);
	}

	public void validate(String body) {
		if (body == null || body.isBlank()) {
			throw new IllegalArgumentException("Template body is required.");
		}
		if (body.length() > 4000) {
			throw new IllegalArgumentException("Template body must be at most 4000 characters.");
		}
		Matcher matcher = PLACEHOLDER.matcher(body);
		while (matcher.find()) {
			if (!ALLOWED.contains(matcher.group(1))) {
				throw new IllegalArgumentException("Unsupported placeholder: " + matcher.group(1));
			}
		}
		String withoutPlaceholders = matcher.reset().replaceAll("");
		if (withoutPlaceholders.contains("{{") || withoutPlaceholders.contains("}}")) {
			throw new IllegalArgumentException("Invalid placeholder.");
		}
	}

	public String renderBody(String body, MessageTemplateValues values) {
		validate(body);
		Matcher matcher = PLACEHOLDER.matcher(body);
		StringBuffer rendered = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(rendered, Matcher.quoteReplacement(values.value(matcher.group(1))));
		}
		matcher.appendTail(rendered);
		return rendered.toString();
	}
}
