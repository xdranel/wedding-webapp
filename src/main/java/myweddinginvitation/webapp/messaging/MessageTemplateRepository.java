package myweddinginvitation.webapp.messaging;

import java.util.Optional;

import myweddinginvitation.webapp.guest.MessageLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
	Optional<MessageTemplate> findByTypeAndLanguage(MessageType type, MessageLanguage language);
}
