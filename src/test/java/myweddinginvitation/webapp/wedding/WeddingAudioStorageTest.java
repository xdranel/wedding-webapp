package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class WeddingAudioStorageTest {
	@TempDir Path mediaDirectory;

	@Test
	void storesMpegAndId3PrefixedMp3UnchangedUnderRandomAudioNames() throws Exception {
		WeddingAudioStorage storage = new WeddingAudioStorage(mediaDirectory);
		byte[] mpeg = mpeg();
		byte[] id3 = id3(mpeg);

		String direct = storage.store(file("anything.mp3", "text/plain", mpeg));
		String tagged = storage.store(file("anything.else", "audio/wav", id3));

		assertThat(direct).matches("audio/[0-9a-f-]{36}\\.mp3");
		assertThat(tagged).matches("audio/[0-9a-f-]{36}\\.mp3");
		assertThat(Files.readAllBytes(storage.resolve(direct))).isEqualTo(mpeg);
		assertThat(Files.readAllBytes(storage.resolve(tagged))).isEqualTo(id3);
	}

	@Test
	void rejectsEmptyCorruptNonMp3AndId3WithoutImmediateMpegFrame() {
		WeddingAudioStorage storage = new WeddingAudioStorage(mediaDirectory);

		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(file("empty.mp3", "audio/mpeg", new byte[0])));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(file("corrupt.mp3", "audio/mpeg", new byte[] {1, 2, 3, 4})));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(file("random.mp3", "audio/mpeg", "not mp3".getBytes())));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(file("id3.mp3", "audio/mpeg",
				new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 3, 1, 2, 3, 0, 0, 0, 0})));
	}

	@Test
	void rejectsOversizedUploadBeforeOpeningItsStream() {
		assertThatIllegalArgumentException().isThrownBy(() -> new WeddingAudioStorage(mediaDirectory).store(oversizedFile()));
	}

	@Test
	void rejectsTraversalAndDeletesStoredAudio() throws Exception {
		WeddingAudioStorage storage = new WeddingAudioStorage(mediaDirectory);
		String stored = storage.store(file("track.mp3", "audio/mpeg", mpeg()));

		assertThatIllegalArgumentException().isThrownBy(() -> storage.resolve("../outside.mp3"));
		storage.delete(stored);

		assertThat(storage.resolve(stored)).doesNotExist();
	}

	private static MockMultipartFile file(String filename, String contentType, byte[] bytes) {
		return new MockMultipartFile("audio", filename, contentType, bytes);
	}

	private static byte[] mpeg() {
		return new byte[] {(byte) 0xff, (byte) 0xfb, (byte) 0x90, 0x64, 1, 2, 3};
	}

	private static byte[] id3(byte[] mpeg) {
		byte[] tagged = new byte[13 + mpeg.length];
		System.arraycopy(new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 3, 7, 8, 9}, 0, tagged, 0, 13);
		System.arraycopy(mpeg, 0, tagged, 13, mpeg.length);
		return tagged;
	}

	private static MultipartFile oversizedFile() {
		return new MultipartFile() {
			@Override public String getName() { return "audio"; }
			@Override public String getOriginalFilename() { return "large.mp3"; }
			@Override public String getContentType() { return "audio/mpeg"; }
			@Override public boolean isEmpty() { return false; }
			@Override public long getSize() { return 20L * 1024 * 1024 + 1; }
			@Override public byte[] getBytes() { throw new AssertionError("Oversized upload must not be read"); }
			@Override public InputStream getInputStream() { throw new AssertionError("Oversized upload must not be read"); }
			@Override public void transferTo(File destination) { throw new AssertionError("Oversized upload must not be read"); }
		};
	}
}
