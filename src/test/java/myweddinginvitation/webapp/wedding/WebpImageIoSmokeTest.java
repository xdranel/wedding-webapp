package myweddinginvitation.webapp.wedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebpImageIoSmokeTest {
	@TempDir
	Path tempDirectory;

	@Test
	void writesAndReadsArgbWebp() throws IOException {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		Path output = tempDirectory.resolve("image.webp");

		assertThat(ImageIO.write(image, "webp", output.toFile())).isTrue();
		BufferedImage decoded = ImageIO.read(output.toFile());

		assertThat(decoded.getWidth()).isEqualTo(2);
		assertThat(decoded.getColorModel().hasAlpha()).isTrue();
	}
}
