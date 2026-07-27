package myweddinginvitation.webapp.config;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import myweddinginvitation.webapp.account.AccountRole;
import myweddinginvitation.webapp.account.UserAccount;
import myweddinginvitation.webapp.account.UserAccountRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountSessionFilter extends OncePerRequestFilter {
	public static final String AUTHENTICATED_AT_MILLIS =
			AccountSessionFilter.class.getName() + ".authenticatedAt";
	public static final String SESSION_VERSION =
			AccountSessionFilter.class.getName() + ".sessionVersion";
	private static final long STAFF_LIFETIME_MILLIS = Duration.ofHours(12).toMillis();

	private final UserAccountRepository accounts;

	public AccountSessionFilter(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		UserAccount account = accounts.findByUsernameIgnoreCase(authentication.getName())
				.orElse(null);
		if (!valid(session, account)) {
			expire(session, response);
			return;
		}
		if (account.isPasswordChangeRequired() && !passwordChangeOrLogout(request)) {
			response.sendRedirect("/account/password");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean valid(HttpSession session, UserAccount account) {
		if (session == null || account == null || !account.isEnabled()) {
			return false;
		}
		Object sessionVersion = session.getAttribute(SESSION_VERSION);
		Object authenticatedAt = session.getAttribute(AUTHENTICATED_AT_MILLIS);
		if (!(sessionVersion instanceof Number version)
				|| version.longValue() != account.getSessionVersion()
				|| !(authenticatedAt instanceof Number loginTime)) {
			return false;
		}
		return account.getRole() != AccountRole.STAFF
				|| System.currentTimeMillis() - loginTime.longValue() < STAFF_LIFETIME_MILLIS;
	}

	private boolean passwordChangeOrLogout(HttpServletRequest request) {
		return request.getRequestURI().equals("/account/password")
				|| request.getRequestURI().equals("/logout");
	}

	private void expire(HttpSession session, HttpServletResponse response) throws IOException {
		SecurityContextHolder.clearContext();
		if (session != null) {
			session.invalidate();
		}
		response.sendRedirect("/login?expired");
	}
}
