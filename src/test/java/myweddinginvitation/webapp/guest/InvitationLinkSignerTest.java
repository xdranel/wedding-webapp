package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InvitationLinkSignerTest {
	private static final UUID PUBLIC_ID = UUID.fromString("77a3ecbf-f719-44b9-ae55-cb2364340746");
	private static final String SECRET = "0123456789abcdef0123456789abcdef";
	private static final String SIGNATURE = "VCMAlUrZY_OBa8SzcwrWqvRX8mLbnaErOazGxUvsHDQ";

	private final InvitationLinkSigner signer = new InvitationLinkSigner(
			URI.create("https://invite.example/i"), SECRET);

	@Test
	void signsDeterministicallyAndRejectsChangedOrMalformedValues() {
		assertThat(signer.sign(PUBLIC_ID, 3)).isEqualTo(SIGNATURE);
		assertThat(signer.verify(PUBLIC_ID, 3, SIGNATURE)).isTrue();
		assertThat(signer.verify(PUBLIC_ID, 4, SIGNATURE)).isFalse();
		assertThat(signer.verify(UUID.fromString("77a3ecbf-f719-44b9-ae55-cb2364340747"), 3, SIGNATURE)).isFalse();
		assertThat(signer.verify(PUBLIC_ID, 3, "not+a+url-safe+signature")).isFalse();
	}

	@Test
	void buildsUrlWithOnlyPublicIdVersionAndSignature() {
		assertThat(signer.url(PUBLIC_ID, 3)).isEqualTo(
				"https://invite.example/i/77a3ecbf-f719-44b9-ae55-cb2364340746/3/" + SIGNATURE);
	}
}
