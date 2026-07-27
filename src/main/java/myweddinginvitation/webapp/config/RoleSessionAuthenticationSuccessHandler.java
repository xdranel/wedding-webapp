package myweddinginvitation.webapp.config;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class RoleSessionAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
	private static final String ADMIN_ROLE = "ROLE_ADMIN";

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		boolean administrator = authentication.getAuthorities().stream()
				.anyMatch(authority -> ADMIN_ROLE.equals(authority.getAuthority()));
		request.getSession().setMaxInactiveInterval(administrator ? 1800 : 43200);
		response.sendRedirect(administrator ? "/admin" : "/check-in");
	}
}
