package myweddinginvitation.webapp.config;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import myweddinginvitation.webapp.account.AccountSecurityService;
import myweddinginvitation.webapp.account.UserAccount;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class RoleSessionAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
	private static final String ADMIN_ROLE = "ROLE_ADMIN";
	private final AccountSecurityService accountSecurity;

	public RoleSessionAuthenticationSuccessHandler(AccountSecurityService accountSecurity) {
		this.accountSecurity = accountSecurity;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		boolean administrator = authentication.getAuthorities().stream()
				.anyMatch(authority -> ADMIN_ROLE.equals(authority.getAuthority()));
		UserAccount account = accountSecurity.loginSucceeded(authentication.getName());
		request.getSession().setMaxInactiveInterval(administrator ? 1800 : 43200);
		request.getSession().setAttribute(
				AccountSessionFilter.AUTHENTICATED_AT_MILLIS, System.currentTimeMillis());
		request.getSession().setAttribute(
				AccountSessionFilter.SESSION_VERSION, account.getSessionVersion());
		response.sendRedirect(account.isPasswordChangeRequired()
				? "/account/password"
				: administrator ? "/admin" : "/check-in");
	}
}
