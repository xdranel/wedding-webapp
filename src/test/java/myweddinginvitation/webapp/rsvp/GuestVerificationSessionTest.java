package myweddinginvitation.webapp.rsvp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class GuestVerificationSessionTest {
	private static final String SECRET = "0123456789abcdef0123456789abcdef";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final UUID A = UUID.fromString("77a3ecbf-f719-44b9-ae55-cb2364340746");
	private static final UUID B = UUID.fromString("77a3ecbf-f719-44b9-ae55-cb2364340747");

	@Test
	void grantsRemainScopedToIndependentInvitations() {
		MockHttpSession http = new MockHttpSession();
		GuestVerificationSession session = at(Duration.ZERO);

		session.grant(http, A, 1, "+6281234567890");
		assertThat(session.verified(http, B, 1, "+4915123456789")).isFalse();
		session.grant(http, B, 1, "+4915123456789");

		assertThat(session.verified(http, A, 1, "+6281234567890")).isTrue();
		assertThat(session.verified(http, B, 1, "+4915123456789")).isTrue();
	}

	@Test
	void verificationExpiresAtThirtyMinutesWithoutSliding() {
		MockHttpSession http = new MockHttpSession();
		at(Duration.ZERO).grant(http, A, 1, "+6281234567890");

		assertThat(at(Duration.ofMinutes(29).plusSeconds(59))
				.verified(http, A, 1, "+6281234567890")).isTrue();
		assertThat(at(Duration.ofMinutes(30))
				.verified(http, A, 1, "+6281234567890")).isFalse();
	}

	@Test
	void tokenOrPhoneChangeInvalidatesExistingVerification() {
		MockHttpSession http = new MockHttpSession();
		GuestVerificationSession session = at(Duration.ZERO);
		session.grant(http, A, 3, "+6281234567890");

		assertThat(session.verified(http, A, 4, "+6281234567890")).isFalse();
		assertThat(session.verified(http, A, 3, "+6281234567891")).isFalse();
		assertThat(Collections.list(http.getAttributeNames()))
				.noneMatch(name -> String.valueOf(http.getAttribute(name)).contains("+6281234567890"));
	}

	private GuestVerificationSession at(Duration offset) {
		return new GuestVerificationSession(SECRET,
				Clock.fixed(NOW.plus(offset), ZoneOffset.UTC));
	}
}
