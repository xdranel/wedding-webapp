package myweddinginvitation.webapp.wedding;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WeddingSettingsRepository extends JpaRepository<WeddingSettings, Byte> {
    default Optional<WeddingSettings> getSingleton() {
        return findById((byte) 1);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settings from WeddingSettings settings where settings.id = 1")
    Optional<WeddingSettings> findSingletonForUpdate();
}
