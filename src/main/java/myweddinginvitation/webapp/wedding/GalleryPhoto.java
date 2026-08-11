package myweddinginvitation.webapp.wedding;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "gallery_photo")
public class GalleryPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int position;

    @Column(name = "main_path", nullable = false, length = 500)
    private String mainPath;

    @Column(name = "thumbnail_path", nullable = false, length = 500)
    private String thumbnailPath;

    @Column(name = "alt_text", nullable = false, length = 300)
    private String altText;

    @Column(name = "caption_id", length = 500)
    private String captionId;

    @Column(name = "caption_en", length = 500)
    private String captionEn;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GalleryPhoto() {
    }

    static GalleryPhoto create(int position, String mainPath, String thumbnailPath,
            String altText, String captionId, String captionEn) {
        GalleryPhoto photo = new GalleryPhoto();
        photo.position = position;
        photo.mainPath = mainPath;
        photo.thumbnailPath = thumbnailPath;
        photo.altText = altText;
        photo.captionId = captionId;
        photo.captionEn = captionEn;
        photo.createdAt = Instant.now();
        photo.updatedAt = photo.createdAt;
        return photo;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getMainPath() { return mainPath; }
    public String getThumbnailPath() { return thumbnailPath; }
    public String getAltText() { return altText; }
    public String getCaptionId() { return captionId; }
    public String getCaptionEn() { return captionEn; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void updateMetadata(String altText, String captionId, String captionEn) {
        this.altText = altText;
        this.captionId = captionId;
        this.captionEn = captionEn;
        updatedAt = Instant.now();
    }

    void replacePaths(String mainPath, String thumbnailPath) {
        this.mainPath = mainPath;
        this.thumbnailPath = thumbnailPath;
        updatedAt = Instant.now();
    }

    void moveTo(int position) {
        this.position = position;
        updatedAt = Instant.now();
    }
}
