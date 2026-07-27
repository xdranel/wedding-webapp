package myweddinginvitation.webapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class MyweddinginvitationWebappApplicationTests {

	@Test
	void contextLoads() {
	}

}
