package myweddinginvitation.webapp.wedding;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import myweddinginvitation.webapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class GalleryImageStorage {
    private static final Logger logger = LoggerFactory.getLogger(GalleryImageStorage.class);
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000;
    private static final int MAIN_MAX_SIDE = 1920;
    private static final int THUMBNAIL_MAX_SIDE = 480;
    private final Path mediaDirectory;
    private final Path galleryDirectory;
    private final Path coverDirectory;
    private final BiConsumer<BufferedImage, Path> webpWriter;

    @Autowired
    public GalleryImageStorage(AppProperties properties) {
        this(properties.mediaDirectory());
    }

    GalleryImageStorage(Path mediaDirectory) {
        this(mediaDirectory, GalleryImageStorage::writeWebp);
    }

    GalleryImageStorage(Path mediaDirectory, BiConsumer<BufferedImage, Path> webpWriter) {
        this.mediaDirectory = mediaDirectory.toAbsolutePath().normalize();
        this.galleryDirectory = this.mediaDirectory.resolve("gallery").normalize();
        this.coverDirectory = this.mediaDirectory.resolve("cover").normalize();
        this.webpWriter = webpWriter;
    }

    public StoredGalleryImage store(MultipartFile file) {
        return store(file, galleryDirectory, true);
    }

    public String storeCover(MultipartFile file) {
        return store(file, coverDirectory, false).mainPath();
    }

    private StoredGalleryImage store(MultipartFile file, Path directory, boolean thumbnailRequired) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE)
            throw new IllegalArgumentException("Image must be at most 10 MiB");

        String name = UUID.randomUUID().toString();
        Path main = directory.resolve(name + ".webp");
        Path thumbnail = thumbnailRequired ? directory.resolve(name + "-thumbnail.webp") : null;
        Path mainTemporary = null;
        Path thumbnailTemporary = null;
        try {
            Files.createDirectories(directory);
            BufferedImage source = decode(file);
            mainTemporary = Files.createTempFile(directory, "upload-", ".webp");
            webpWriter.accept(resize(source, MAIN_MAX_SIDE), mainTemporary);
            if (thumbnailRequired) {
                thumbnailTemporary = Files.createTempFile(directory, "upload-", ".webp");
                webpWriter.accept(resize(source, THUMBNAIL_MAX_SIDE), thumbnailTemporary);
            }
            move(mainTemporary, main);
            mainTemporary = null;
            if (thumbnailRequired) {
                move(thumbnailTemporary, thumbnail);
                thumbnailTemporary = null;
            }
            return new StoredGalleryImage(relative(main), thumbnailRequired ? relative(thumbnail) : null);
        } catch (IOException | RuntimeException exception) {
            deletePath(mainTemporary);
            deletePath(thumbnailTemporary);
            deletePath(main);
            deletePath(thumbnail);
            if (exception instanceof IllegalArgumentException argumentException) throw argumentException;
            throw new IllegalArgumentException("Could not process gallery image", exception);
        }
    }

    public Path resolve(String relativePath) {
        return resolve(relativePath, galleryDirectory, "Invalid gallery image path");
    }

    public Path resolveCover(String relativePath) {
        return resolve(relativePath, coverDirectory, "Invalid invitation cover path");
    }

    private Path resolve(String relativePath, Path directory, String message) {
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException(message);
        Path path = mediaDirectory.resolve(relativePath).normalize();
        if (!path.startsWith(directory)) throw new IllegalArgumentException(message);
        return path;
    }

    public void delete(StoredGalleryImage image) {
        if (image == null) return;
        deletePath(resolve(image.mainPath()));
        deletePath(resolve(image.thumbnailPath()));
    }

    public void deleteAfterCommit(StoredGalleryImage image) {
        try {
            delete(image);
        } catch (RuntimeException exception) {
            logger.warn("Could not delete obsolete gallery image {}", image, exception);
        }
    }

    public void deleteCover(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        deletePath(resolveCover(relativePath));
    }

    public void deleteCoverAfterCommit(String relativePath) {
        try {
            deleteCover(relativePath);
        } catch (RuntimeException exception) {
            logger.warn("Could not delete obsolete invitation cover {}", relativePath, exception);
        }
    }

    private BufferedImage decode(MultipartFile file) throws IOException {
        try (InputStream source = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(source)) {
            if (input == null) throw new IllegalArgumentException("Image could not be decoded");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Image must be a JPEG, PNG, or WebP image");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("webp"))
                    throw new IllegalArgumentException("Image must be a JPEG, PNG, or WebP image");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Image must contain at most 40,000,000 pixels");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Image could not be decoded");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image could not be decoded", exception);
        }
    }

    private static BufferedImage resize(BufferedImage source, int maximumSide) {
        int longestSide = Math.max(source.getWidth(), source.getHeight());
        double scale = Math.min(1, (double) maximumSide / longestSide);
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static void writeWebp(BufferedImage image, Path path) {
        try {
            if (!ImageIO.write(image, "webp", path.toFile())) throw new IllegalStateException("WebP writer unavailable");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write WebP image", exception);
        }
    }

    private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private String relative(Path path) {
        return mediaDirectory.relativize(path).toString().replace('\\', '/');
    }

    private void deletePath(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            logger.warn("Could not clean up gallery image {}", path, exception);
        }
    }
}
