package myweddinginvitation.webapp.guest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import myweddinginvitation.webapp.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class InvitationLinkSigner {
	private static final String ALGORITHM = "HmacSHA256";
	private final URI baseUri;
	private final SecretKeySpec key;

	@Autowired
	public InvitationLinkSigner(AppProperties properties) {
		this(URI.create(properties.invitation().baseUrl()), properties.invitation().signingSecret());
	}

	InvitationLinkSigner(URI baseUri, String signingSecret) {
		this.baseUri = baseUri;
		key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public String urlFor(Guest guest) {
		return url(guest.getPublicId(), guest.getInvitationTokenVersion());
	}

	public String urlFor(Guest guest, MessageLanguage language) {
		return UriComponentsBuilder.fromUriString(urlFor(guest))
				.queryParam("language", language.name())
				.build().encode().toUriString();
	}

	String url(UUID publicId, long version) {
		return UriComponentsBuilder.fromUri(baseUri)
				.pathSegment(publicId.toString(), Long.toString(version), sign(publicId, version))
				.build().encode().toUriString();
	}

	String sign(UUID publicId, long version) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(signature(publicId, version));
	}

	public boolean verify(UUID publicId, long version, String signature) {
		try {
			return MessageDigest.isEqual(this.signature(publicId, version), Base64.getUrlDecoder().decode(signature));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private byte[] signature(UUID publicId, long version) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal((publicId + ":" + version).getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
