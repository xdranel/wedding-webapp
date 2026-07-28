package myweddinginvitation.webapp.wedding;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WeddingMediaController {
    private final PartnerPhotoStorage storage;

    public WeddingMediaController(PartnerPhotoStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/admin/wedding/media/{filename:.+}")
    ResponseEntity<FileSystemResource> photo(@PathVariable String filename) {
        try {
            Path path = storage.resolve(filename);
            if (!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(storage.contentType(filename))
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new FileSystemResource(path));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
