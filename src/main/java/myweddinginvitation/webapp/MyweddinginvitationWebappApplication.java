package myweddinginvitation.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import myweddinginvitation.webapp.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MyweddinginvitationWebappApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyweddinginvitationWebappApplication.class, args);
	}

}
