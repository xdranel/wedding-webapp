package myweddinginvitation.webapp.wedding;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, Long> {
    List<GalleryPhoto> findAllByOrderByPositionAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select photo from GalleryPhoto photo where photo.id = :id")
    Optional<GalleryPhoto> findByIdForUpdate(@Param("id") long id);
}
