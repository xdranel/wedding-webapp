package myweddinginvitation.webapp.rsvp;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

@Service
public class QrImageService {
	public byte[] png(String payload, int size) {
		if (size != 320 && size != 1024) throw new IllegalArgumentException("Unsupported QR size.");
		try {
			BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, Map.of(
					EncodeHintType.CHARACTER_SET, "UTF-8",
					EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
					EncodeHintType.MARGIN, 4));
			BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
				}
			}
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "PNG", output)) throw new IllegalStateException("PNG writer unavailable.");
			return output.toByteArray();
		} catch (WriterException | IOException exception) {
			throw new IllegalStateException("Could not create QR image.", exception);
		}
	}
}
