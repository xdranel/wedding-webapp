package myweddinginvitation.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.FileSystemResource;

class ProductionProfileTest {
	@Test
	void productionProfileFailsStartupWithoutDatabasePassword() {
		assertThatThrownBy(() -> contextWithProfile("prod"))
				.hasRootCauseMessage("Could not resolve placeholder 'DB_PASSWORD' in value \"${DB_PASSWORD}\"");
	}

	@Test
	void defaultProfileKeepsDevelopmentDatabasePasswordFallback() {
		try (ConfigurableApplicationContext context = contextWithProfile("default")) {
			assertThat(context.getBean(String.class)).isEqualTo("wedding");
		}
	}

	private ConfigurableApplicationContext contextWithProfile(String profile) {
		return new SpringApplicationBuilder(DatabasePasswordProbe.class)
				.web(WebApplicationType.NONE)
				.initializers(context -> {
					MutablePropertySources properties = context.getEnvironment().getPropertySources();
					loadYaml(properties, "application.yml", false);
					if (profile.equals("prod")) {
						loadYaml(properties, "application-prod.yml", true);
					}
				})
				.run();
	}

	private void loadYaml(MutablePropertySources properties, String filename, boolean overrides) {
		try {
			for (var source : new YamlPropertySourceLoader().load(filename,
					new FileSystemResource("src/main/resources/" + filename))) {
				if (overrides) {
					properties.addFirst(source);
				} else {
					properties.addLast(source);
				}
			}
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class DatabasePasswordProbe {
		@Bean
		String databasePassword(Environment environment) {
			return environment.resolveRequiredPlaceholders(
					environment.getRequiredProperty("spring.datasource.password"));
		}
	}
}
