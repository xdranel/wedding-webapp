package myweddinginvitation.webapp.wedding;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoryEntryRepository extends JpaRepository<StoryEntry, Long> {
    List<StoryEntry> findAllByOrderByDisplayOrderAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from StoryEntry entry order by entry.displayOrder")
    List<StoryEntry> findAllByOrderByDisplayOrderAscForUpdate();
}
