package myweddinginvitation.webapp.rsvp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import myweddinginvitation.webapp.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckInQrSigner {
	private static final String ALGORITHM = "HmacSHA256";
	private static final String FORMAT = "W1";
	private static final String PURPOSE = "check-in-qr:W1:";

	private final SecretKeySpec key;

	@Autowired
	public CheckInQrSigner(AppProperties properties) {
		this(properties.invitation().signingSecret());
	}

	CheckInQrSigner(String signingSecret) {
		key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String payload(UUID publicId, long tokenVersion) {
		if (tokenVersion < 1) throw new IllegalArgumentException("Token version must be positive.");
		return FORMAT + "." + publicId + "." + tokenVersion + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(signature(publicId, tokenVersion));
	}

	public Optional<QrReference> verify(String payload) {
		if (payload == null) return Optional.empty();
		String[] parts = payload.split("\\.", -1);
		if (parts.length != 4 || !FORMAT.equals(parts[0])) return Optional.empty();
		try {
			UUID publicId = UUID.fromString(parts[1]);
			long tokenVersion = Long.parseLong(parts[2]);
			if (tokenVersion < 1) return Optional.empty();
			byte[] supplied = Base64.getUrlDecoder().decode(parts[3]);
			return MessageDigest.isEqual(signature(publicId, tokenVersion), supplied)
					? Optional.of(new QrReference(publicId, tokenVersion)) : Optional.empty();
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private byte[] signature(UUID publicId, long tokenVersion) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal((PURPOSE + publicId + ":" + tokenVersion).getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

record QrReference(UUID publicId, long tokenVersion) {
}
