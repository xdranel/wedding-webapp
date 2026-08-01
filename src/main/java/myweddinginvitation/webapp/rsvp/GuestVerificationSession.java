package myweddinginvitation.webapp.rsvp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpSession;
import myweddinginvitation.webapp.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GuestVerificationSession {
	private static final String ALGORITHM = "HmacSHA256";
	private static final String ATTRIBUTE = GuestVerificationSession.class.getName() + ".verified";
	private static final Duration VALID_FOR = Duration.ofMinutes(30);

	private final SecretKeySpec key;
	private final Clock clock;

	@Autowired
	public GuestVerificationSession(AppProperties properties, Clock clock) {
		this(properties.invitation().signingSecret(), clock);
	}

	GuestVerificationSession(String signingSecret, Clock clock) {
		key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
		this.clock = clock;
	}

	public void grant(HttpSession session, UUID publicId, long tokenVersion, String normalizedWhatsappNumber) {
		synchronized (session) {
			Map<UUID, Verification> entries = entries(session);
			entries.put(publicId, new Verification(tokenVersion, fingerprint(normalizedWhatsappNumber), clock.instant()));
			session.setAttribute(ATTRIBUTE, entries);
		}
	}

	public boolean verified(HttpSession session, UUID publicId, long tokenVersion, String normalizedWhatsappNumber) {
		synchronized (session) {
			Verification verification = entries(session).get(publicId);
			return verification != null
					&& verification.tokenVersion() == tokenVersion
					&& MessageDigest.isEqual(verification.phoneFingerprint().getBytes(StandardCharsets.UTF_8),
							fingerprint(normalizedWhatsappNumber).getBytes(StandardCharsets.UTF_8))
					&& clock.instant().isBefore(verification.verifiedAt().plus(VALID_FOR));
		}
	}

	private Map<UUID, Verification> entries(HttpSession session) {
		Object stored = session.getAttribute(ATTRIBUTE);
		if (!(stored instanceof Map<?, ?> map)) return new HashMap<>();
		Map<UUID, Verification> entries = new HashMap<>();
		map.forEach((id, verification) -> {
			if (id instanceof UUID uuid && verification instanceof Verification value) entries.put(uuid, value);
		});
		return entries;
	}

	private String fingerprint(String normalizedWhatsappNumber) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
					("guest-pin-session:" + normalizedWhatsappNumber).getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record Verification(long tokenVersion, String phoneFingerprint, Instant verifiedAt) {
	}
}
