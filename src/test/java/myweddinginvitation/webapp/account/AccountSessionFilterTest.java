package myweddinginvitation.webapp.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;

import myweddinginvitation.webapp.config.AccountSessionFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountSessionFilterTest {
	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void staffSessionExpiresTwelveHoursAfterAuthentication() throws Exception {
		UserAccount staff = readyAccount("staff", AccountRole.STAFF);
		AccountSessionFilter filter = new AccountSessionFilter(repositoryReturning(staff));
		authenticate("staff", "ROLE_STAFF");
		MockHttpServletRequest request = authenticatedRequest("/check-in", staff);
		request.getSession().setAttribute(AccountSessionFilter.AUTHENTICATED_AT_MILLIS,
				System.currentTimeMillis() - Duration.ofHours(12).toMillis());
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void administratorUsesServletInactivityTimeoutWithoutStaffAbsoluteLimit() throws Exception {
		UserAccount admin = readyAccount("admin", AccountRole.ADMIN);
		AccountSessionFilter filter = new AccountSessionFilter(repositoryReturning(admin));
		authenticate("admin", "ROLE_ADMIN");
		MockHttpServletRequest request = authenticatedRequest("/admin", admin);
		request.getSession().setAttribute(AccountSessionFilter.AUTHENTICATED_AT_MILLIS,
				System.currentTimeMillis() - Duration.ofHours(13).toMillis());
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getRedirectedUrl()).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void sessionVersionMismatchRevokesAuthentication() throws Exception {
		UserAccount staff = readyAccount("staff", AccountRole.STAFF);
		AccountSessionFilter filter = new AccountSessionFilter(repositoryReturning(staff));
		authenticate("staff", "ROLE_STAFF");
		MockHttpServletRequest request = authenticatedRequest("/check-in", staff);
		request.getSession().setAttribute(AccountSessionFilter.SESSION_VERSION,
				staff.getSessionVersion() - 1);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void disabledAccountLosesAccess() throws Exception {
		UserAccount staff = readyAccount("staff", AccountRole.STAFF);
		staff.disable();
		AccountSessionFilter filter = new AccountSessionFilter(repositoryReturning(staff));
		authenticate("staff", "ROLE_STAFF");
		MockHttpServletRequest request = authenticatedRequest("/check-in", staff);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void firstLoginCanOnlyOpenPasswordChangeOrLogout() throws Exception {
		UserAccount admin = new UserAccount("admin", "hash", AccountRole.ADMIN);
		AccountSessionFilter filter = new AccountSessionFilter(repositoryReturning(admin));
		authenticate("admin", "ROLE_ADMIN");
		MockHttpServletRequest request = authenticatedRequest("/admin", admin);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getRedirectedUrl()).isEqualTo("/account/password");
		assertThat(chain.getRequest()).isNull();
	}

	private UserAccount readyAccount(String username, AccountRole role) {
		UserAccount account = new UserAccount(username, "hash", role);
		account.changePassword("hash");
		return account;
	}

	private void authenticate(String username, String role) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(
						username, "", AuthorityUtils.createAuthorityList(role)));
	}

	private MockHttpServletRequest authenticatedRequest(String path, UserAccount account) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AccountSessionFilter.SESSION_VERSION, account.getSessionVersion());
		session.setAttribute(AccountSessionFilter.AUTHENTICATED_AT_MILLIS,
				System.currentTimeMillis());
		request.setSession(session);
		return request;
	}

	private UserAccountRepository repositoryReturning(UserAccount account) {
		return (UserAccountRepository) Proxy.newProxyInstance(
				UserAccountRepository.class.getClassLoader(),
				new Class<?>[] {UserAccountRepository.class},
				(proxy, method, arguments) -> {
					if (method.getName().equals("findByUsernameIgnoreCase")) {
						return Optional.of(account);
					}
					throw new UnsupportedOperationException(method.getName());
				});
	}
}
