package myweddinginvitation.webapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			RoleSessionAuthenticationSuccessHandler roleSessionAuthenticationSuccessHandler)
			throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/i/**", "/login", "/error", "/actuator/health",
								"/css/**", "/js/**", "/images/**").permitAll()
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/check-in/**").hasAnyRole("ADMIN", "STAFF")
						.anyRequest().denyAll())
				.csrf(Customizer.withDefaults())
				.formLogin(form -> form
						.loginPage("/login")
						.successHandler(roleSessionAuthenticationSuccessHandler)
						.permitAll())
				.logout(logout -> logout.logoutSuccessUrl("/login?logout"))
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
