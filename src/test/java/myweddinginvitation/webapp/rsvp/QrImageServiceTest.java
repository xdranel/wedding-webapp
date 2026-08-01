package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class QrImageServiceTest {
	private final QrImageService images = new QrImageService();

	@Test
	void createsExactDisplayAndDownloadPngSizes() throws Exception {
		byte[] display = images.png("W1.example.1.signature", 320);
		byte[] download = images.png("W1.example.1.signature", 1024);

		assertThat(display).startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
		assertThat(ImageIO.read(new ByteArrayInputStream(display)).getWidth()).isEqualTo(320);
		assertThat(ImageIO.read(new ByteArrayInputStream(display)).getHeight()).isEqualTo(320);
		assertThat(ImageIO.read(new ByteArrayInputStream(download)).getWidth()).isEqualTo(1024);
		assertThat(ImageIO.read(new ByteArrayInputStream(download)).getHeight()).isEqualTo(1024);
	}

	@Test
	void rejectsSizesOtherThanTheTwoPublicVariants() {
		assertThatIllegalArgumentException().isThrownBy(() -> images.png("payload", 319));
		assertThatIllegalArgumentException().isThrownBy(() -> images.png("payload", 1025));
	}
}
