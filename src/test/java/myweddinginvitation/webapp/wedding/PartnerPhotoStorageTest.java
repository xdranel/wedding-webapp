package myweddinginvitation.webapp.wedding;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

class PartnerPhotoStorageTest {
	@TempDir
	Path mediaDirectory;

	@Test
	void acceptsKnownImageSignatureAndUsesGeneratedName() {
		PartnerPhotoStorage storage = new PartnerPhotoStorage(mediaDirectory);
		var file = new MockMultipartFile("photo", "face.jpg", "text/plain",
				new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});

		String path = storage.store(file);

		assertThat(path).endsWith(".jpg").doesNotContain("face");
		assertThat(mediaDirectory.resolve(path)).exists();
	}

	@Test
	void rejectsUnknownOrOversizedContent() {
		PartnerPhotoStorage storage = new PartnerPhotoStorage(mediaDirectory);
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(new MockMultipartFile(
				"photo", "fake.jpg", "image/jpeg", "not-image".getBytes(UTF_8))));
		assertThatIllegalArgumentException().isThrownBy(() -> storage.store(new MockMultipartFile(
				"photo", "large.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1])));
	}

	@Test
	void acceptsPhotoAtExactTenMiBLimit() {
		byte[] photo = new byte[10 * 1024 * 1024];
		photo[0] = (byte) 0xff;
		photo[1] = (byte) 0xd8;
		photo[2] = (byte) 0xff;

		String path = new PartnerPhotoStorage(mediaDirectory).store(new MockMultipartFile("photo", "limit.jpg", "image/jpeg", photo));

		assertThat(mediaDirectory.resolve(path)).exists();
	}

	@ParameterizedTest
	@MethodSource("validSignatures")
	void acceptsEveryRequiredPhotoFormat(String filename, byte[] signature) {
		String path = new PartnerPhotoStorage(mediaDirectory).store(new MockMultipartFile(
				"photo", filename, "application/octet-stream", signature));

		assertThat(path).endsWith(filename.substring(filename.lastIndexOf('.')));
	}

	static Stream<Arguments> validSignatures() {
		return Stream.of(
				arguments("photo.jpg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}),
				arguments("photo.png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
				arguments("photo.webp", "RIFF0000WEBP".getBytes(US_ASCII)));
	}
}
