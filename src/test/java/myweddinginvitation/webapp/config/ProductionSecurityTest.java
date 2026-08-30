package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.servlet.http.HttpSession;
import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026",
		"app.invitation.signing-secret=test-only-invitation-signing-secret",
		"DB_PASSWORD=test-only-password"
})
@ActiveProfiles("prod")
@Import({MySqlTestConfiguration.class, ProductionSecurityTest.SessionCookieProbe.class})
class ProductionSecurityTest {
	private static final HttpClient HTTP = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@Test
	void productionProfileSeparatesHealthAndUsesGracefulShutdown() throws IOException {
		String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
		assertThat(yaml).contains("shutdown: graceful", "timeout-per-shutdown-phase: 30s",
				"port: 8081", "timeout: 8h", "same-site: lax");
		assertThat(yaml).doesNotContain("secure: true");
	}

	@Test
	void productionSessionCookiesAreSecureOnlyForForwardedHttps() throws IOException, InterruptedException {
		assertThat(sessionCookie(false)).doesNotContain("Secure").contains("HttpOnly", "SameSite=Lax");
		assertThat(sessionCookie(true)).contains("Secure", "HttpOnly", "SameSite=Lax");
	}

	private String sessionCookie(boolean forwardedHttps) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
				+ "/i/session-cookie-probe"));
		if (forwardedHttps) {
			request.header("X-Forwarded-Proto", "https");
		}
		return HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.discarding()).headers()
				.firstValue("set-cookie").orElseThrow();
	}

	@TestConfiguration(proxyBeanMethods = false)
	@RestController
	static class SessionCookieProbe {
		@GetMapping("/i/session-cookie-probe")
		void createSession(HttpSession session) {
		}
	}
}
