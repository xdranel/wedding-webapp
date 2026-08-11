package myweddinginvitation.webapp.wedding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import myweddinginvitation.webapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PartnerPhotoStorage {
    private static final Logger logger = LoggerFactory.getLogger(PartnerPhotoStorage.class);
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private final Path mediaDirectory;

    @Autowired
    public PartnerPhotoStorage(AppProperties properties) {
        this(properties.mediaDirectory());
    }

    PartnerPhotoStorage(Path mediaDirectory) {
        this.mediaDirectory = mediaDirectory.toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE)
            throw new IllegalArgumentException("Photo must be at most 10 MiB");
        String extension = photoType(file).extension();
        Path destination = mediaDirectory.resolve("partner").resolve(UUID.randomUUID() + extension).normalize();
        if (!destination.startsWith(mediaDirectory)) throw new IllegalArgumentException("Invalid photo path");
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(file.getInputStream(), destination);
            return mediaDirectory.relativize(destination).toString();
        } catch (IOException exception) {
            deletePath(destination);
            throw new IllegalStateException("Could not store photo", exception);
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        Path path = mediaDirectory.resolve(relativePath).normalize();
        if (!path.startsWith(mediaDirectory)) throw new IllegalArgumentException("Invalid photo path");
        deletePath(path);
    }

    void deleteAfterCommit(String relativePath) {
        try {
            delete(relativePath);
        } catch (RuntimeException exception) {
            logger.warn("Could not delete replaced partner photo {} after commit", relativePath, exception);
        }
    }

    Path resolve(String relativePath) {
        Path path = mediaDirectory.resolve(relativePath).normalize();
        if (!path.startsWith(mediaDirectory)) throw new IllegalArgumentException("Invalid photo path");
        return path;
    }

    MediaType contentType(String relativePath) {
        try (InputStream input = Files.newInputStream(resolve(relativePath))) {
            return photoType(input).mediaType();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read photo", exception);
        }
    }

    private PhotoType photoType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return photoType(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read photo", exception);
        }
    }

    private PhotoType photoType(InputStream input) throws IOException {
        byte[] header = input.readNBytes(12);
        if (matchesAt(header, 0, 0xff, 0xd8, 0xff)) return new PhotoType(".jpg", MediaType.IMAGE_JPEG);
        if (matchesAt(header, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            return new PhotoType(".png", MediaType.IMAGE_PNG);
        if (matchesAt(header, 0, 'R', 'I', 'F', 'F') && matchesAt(header, 8, 'W', 'E', 'B', 'P'))
            return new PhotoType(".webp", MediaType.valueOf("image/webp"));
        throw new IllegalArgumentException("Photo must be a JPEG, PNG, or WebP image");
    }

    private boolean matchesAt(byte[] bytes, int offset, int... values) {
        if (bytes.length < offset + values.length) return false;
        for (int index = 0; index < values.length; index++) {
            if ((bytes[offset + index] & 0xff) != values[index]) return false;
        }
        return true;
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete photo", exception);
        }
    }

    private record PhotoType(String extension, MediaType mediaType) {
    }
}
