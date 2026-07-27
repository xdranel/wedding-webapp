package myweddinginvitation.webapp.wedding;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeddingSettingsRepository extends JpaRepository<WeddingSettings, Byte> {
	default Optional<WeddingSettings> getSingleton() {
		return findById((byte) 1);
	}
}
