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
import org.springframework.web.multipart.MultipartFile;

@Component
public class WeddingAudioStorage {
	private static final Logger logger = LoggerFactory.getLogger(WeddingAudioStorage.class);
	private static final long MAX_SIZE = 20L * 1024 * 1024;
	private final Path mediaDirectory;
	private final Path audioDirectory;

	@Autowired
	public WeddingAudioStorage(AppProperties properties) {
		this(properties.mediaDirectory());
	}

	WeddingAudioStorage(Path mediaDirectory) {
		this.mediaDirectory = mediaDirectory.toAbsolutePath().normalize();
		this.audioDirectory = this.mediaDirectory.resolve("audio").normalize();
	}

	public String store(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE)
			throw new IllegalArgumentException("Audio must be a non-empty MP3 of at most 20 MiB");
		validate(file);
		Path destination = audioDirectory.resolve(UUID.randomUUID() + ".mp3");
		try {
			Files.createDirectories(audioDirectory);
			try (InputStream input = file.getInputStream()) {
				Files.copy(input, destination);
			}
			return mediaDirectory.relativize(destination).toString().replace('\\', '/');
		} catch (IOException exception) {
			deletePath(destination);
			throw new IllegalStateException("Could not store audio", exception);
		}
	}

	public Path resolve(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("Invalid audio path");
		Path path = mediaDirectory.resolve(relativePath).normalize();
		if (!path.startsWith(audioDirectory)) throw new IllegalArgumentException("Invalid audio path");
		return path;
	}

	public void delete(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) return;
		deletePath(resolve(relativePath));
	}

	public void deleteAfterCommit(String relativePath) {
		try {
			delete(relativePath);
		} catch (RuntimeException exception) {
			logger.warn("Could not delete obsolete wedding audio {}", relativePath, exception);
		}
	}

	private void validate(MultipartFile file) {
		try (InputStream input = file.getInputStream()) {
			byte[] header = input.readNBytes(10);
			if (isMpegFrame(header)) return;
			if (!isId3(header)) throw new IllegalArgumentException("Audio must be an MP3");
			input.skipNBytes(id3Size(header) + ((header[5] & 0x10) == 0 ? 0 : 10));
			if (!isMpegFrame(input.readNBytes(4))) throw new IllegalArgumentException("Audio must be an MP3");
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read audio", exception);
		}
	}

	private static boolean isId3(byte[] header) {
		return header.length == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3';
	}

	private static long id3Size(byte[] header) {
		long size = 0;
		for (int index = 6; index < 10; index++) {
			if ((header[index] & 0x80) != 0) throw new IllegalArgumentException("Audio must be an MP3");
			size = (size << 7) | header[index];
		}
		return size;
	}

	private static boolean isMpegFrame(byte[] header) {
		return header.length >= 2 && (header[0] & 0xff) == 0xff && (header[1] & 0xe0) == 0xe0;
	}

	private void deletePath(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not delete audio", exception);
		}
	}
}
