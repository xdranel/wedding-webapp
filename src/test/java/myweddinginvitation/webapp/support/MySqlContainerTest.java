package myweddinginvitation.webapp.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class MySqlContainerTest {
	@Container
	@ServiceConnection
	protected static final MySQLContainer<?> MYSQL =
			new MySQLContainer<>("mysql:8.4.10");
}
