package myweddinginvitation.webapp.messaging;

import java.time.Instant;

import myweddinginvitation.webapp.guest.MessageLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "message_template")
public class MessageTemplate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "message_type", nullable = false, length = 30)
	private MessageType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 2)
	private MessageLanguage language;

	@Column(nullable = false, length = 4000)
	private String body;

	@Version
	private long version;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MessageTemplate() {
	}

	public Long getId() { return id; }
	public MessageType getType() { return type; }
	public MessageLanguage getLanguage() { return language; }
	public String getBody() { return body; }
	public long getVersion() { return version; }
	public Instant getUpdatedAt() { return updatedAt; }
}
