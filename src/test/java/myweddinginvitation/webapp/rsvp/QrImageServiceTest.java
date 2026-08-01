package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

class QrImageServiceTest {
	private final QrImageService images = new QrImageService();

	@Test
	void createsExactDisplayAndDownloadPngSizes() throws Exception {
		String payload = "W1.example.1.signature";
		byte[] display = images.png(payload, 320);
		byte[] download = images.png(payload, 1024);
		BufferedImage displayImage = ImageIO.read(new ByteArrayInputStream(display));

		assertThat(display).startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
		assertThat(displayImage.getWidth()).isEqualTo(320);
		assertThat(displayImage.getHeight()).isEqualTo(320);
		assertThat(ImageIO.read(new ByteArrayInputStream(download)).getWidth()).isEqualTo(1024);
		assertThat(ImageIO.read(new ByteArrayInputStream(download)).getHeight()).isEqualTo(1024);
		int[] pixels = displayImage.getRGB(0, 0, 320, 320, null, 0, 320);
		assertThat(new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
				new RGBLuminanceSource(320, 320, pixels))),
				Map.of(DecodeHintType.CHARACTER_SET, "UTF-8")).getText()).isEqualTo(payload);
	}

	@Test
	void rejectsSizesOtherThanTheTwoPublicVariants() {
		assertThatIllegalArgumentException().isThrownBy(() -> images.png("payload", 319));
		assertThatIllegalArgumentException().isThrownBy(() -> images.png("payload", 1025));
	}
}
