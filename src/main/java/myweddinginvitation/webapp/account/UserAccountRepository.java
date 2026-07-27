package myweddinginvitation.webapp.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	Optional<UserAccount> findByUsernameIgnoreCase(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from UserAccount account "
			+ "where lower(account.username) = lower(:username)")
	Optional<UserAccount> findByUsernameForUpdate(@Param("username") String username);

	long countByRole(AccountRole role);
}
