package myweddinginvitation.webapp.wedding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventPartRepository extends JpaRepository<EventPart, Long> {
    Optional<EventPart> findByType(EventType type);

    List<EventPart> findAllByOrderByTypeAsc();
}
