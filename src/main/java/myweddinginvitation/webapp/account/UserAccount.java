package myweddinginvitation.webapp.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class UserAccount {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountRole role;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "password_change_required", nullable = false)
	private boolean passwordChangeRequired = true;

	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	@Column(name = "session_version", nullable = false)
	private long sessionVersion;

	protected UserAccount() {
	}

	public UserAccount(String username, String passwordHash, AccountRole role) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public AccountRole getRole() {
		return role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
	}

	public int getFailedLoginCount() {
		return failedLoginCount;
	}

	public long getSessionVersion() {
		return sessionVersion;
	}
}
