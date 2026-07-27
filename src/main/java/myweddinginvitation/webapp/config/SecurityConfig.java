package myweddinginvitation.webapp.config;

import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			RoleSessionAuthenticationSuccessHandler roleSessionAuthenticationSuccessHandler,
			AuthenticationFailureHandler authenticationFailureHandler,
			UserAccountRepository accounts)
			throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/i/**", "/login", "/error", "/forbidden",
								"/actuator/health",
								"/css/**", "/js/**", "/images/**").permitAll()
						.requestMatchers("/account/password").authenticated()
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/check-in/**").hasAnyRole("ADMIN", "STAFF")
						.anyRequest().denyAll())
				.csrf(Customizer.withDefaults())
				.formLogin(form -> form
						.loginPage("/login")
						.successHandler(roleSessionAuthenticationSuccessHandler)
						.failureHandler(authenticationFailureHandler)
						.permitAll())
				.exceptionHandling(exceptions -> exceptions.accessDeniedPage("/forbidden"))
				.logout(logout -> logout.logoutSuccessUrl("/login?logout"))
				.addFilterBefore(new AccountSessionFilter(accounts), AuthorizationFilter.class)
				.build();
	}

	@Bean
	AuthenticationFailureHandler authenticationFailureHandler(
			AccountSecurityService accountSecurity) {
		return (request, response, exception) -> {
			if (exception instanceof BadCredentialsException) {
				accountSecurity.loginFailed(request.getParameter("username"));
			}
			response.sendRedirect("/login?error");
		};
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
