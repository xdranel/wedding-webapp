package myweddinginvitation.webapp.wedding;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryEntryRepository extends JpaRepository<StoryEntry, Long> {
	List<StoryEntry> findAllByOrderByDisplayOrderAsc();
}
