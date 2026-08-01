package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
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
		assertThat(new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
				new BufferedImageLuminanceSource(displayImage))),
				Map.of(DecodeHintType.CHARACTER_SET, "UTF-8")).getText()).isEqualTo(payload);
	}

	@Test
	void rejectsSizesOtherThanTheTwoPublicVariants() {
		assertThatIllegalArgumentException().isThrownBy(() -> images.png("payload", 319));
		assertThatIllegalArgumentException().isThrownBy(() -> images.png("payload", 1025));
	}

	private static final class BufferedImageLuminanceSource extends LuminanceSource {
		private final byte[] luminance;

		private BufferedImageLuminanceSource(BufferedImage image) {
			super(image.getWidth(), image.getHeight());
			luminance = new byte[getWidth() * getHeight()];
			for (int y = 0; y < getHeight(); y++) {
				for (int x = 0; x < getWidth(); x++) {
					luminance[y * getWidth() + x] = (byte) image.getRGB(x, y);
				}
			}
		}

		@Override
		public byte[] getRow(int y, byte[] row) {
			if (row == null || row.length < getWidth()) row = new byte[getWidth()];
			System.arraycopy(luminance, y * getWidth(), row, 0, getWidth());
			return row;
		}

		@Override
		public byte[] getMatrix() {
			return luminance;
		}
	}
}
