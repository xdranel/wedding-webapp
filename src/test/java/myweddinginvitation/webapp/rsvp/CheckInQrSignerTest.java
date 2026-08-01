package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CheckInQrSignerTest {
	private static final UUID PUBLIC_ID = UUID.fromString("77a3ecbf-f719-44b9-ae55-cb2364340746");
	private static final String SECRET = "0123456789abcdef0123456789abcdef";
	private static final String SIGNATURE = "mYmvX5pvqe-J6NjO9deLR5WeWZMLTnszwqBp66uZzg8";

	private final CheckInQrSigner signer = new CheckInQrSigner(SECRET);

	@Test
	void signsPurposeSeparatedPayloadAndRoundTripsItsReference() {
		String payload = signer.payload(PUBLIC_ID, 7);

		assertThat(payload).isEqualTo("W1." + PUBLIC_ID + ".7." + SIGNATURE);
		assertThat(signer.verify(payload)).contains(new QrReference(PUBLIC_ID, 7));
		assertThat(payload).doesNotContain("Ada", "+62", "HADIR");
	}

	@Test
	void rejectsTamperingWrongPurposeAndUntrustedTextWithoutThrowing() {
		String payload = signer.payload(PUBLIC_ID, 7);

		assertThat(signer.verify(payload.replace(".7.", ".8."))).isEmpty();
		assertThat(signer.verify("W1." + PUBLIC_ID + ".7.gMnDK28Qn-hKIqWVF4HdXKvz1pdzRBdjsxuNziP-tVo"))
				.isEmpty();
		assertThat(signer.verify("W2." + PUBLIC_ID + ".7." + SIGNATURE)).isEmpty();
		assertThat(signer.verify("W1.not-a-uuid.7." + SIGNATURE)).isEmpty();
		assertThat(signer.verify("W1." + PUBLIC_ID + ".0." + SIGNATURE)).isEmpty();
		assertThat(signer.verify("W1." + PUBLIC_ID + ".7." + SIGNATURE + ".extra")).isEmpty();
		assertThat(signer.verify(null)).isEmpty();
	}
}
