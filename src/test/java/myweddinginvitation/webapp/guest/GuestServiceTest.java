package myweddinginvitation.webapp.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import myweddinginvitation.webapp.support.MySqlTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"app.bootstrap-admin.username=test-admin",
		"app.bootstrap-admin.password=Test-Only-Password-2026"
})
@Import(MySqlTestConfiguration.class)
class GuestServiceTest {
	@Autowired
	GuestService service;

	@Autowired
	GuestRepository guests;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clearGuests() {
		jdbc.update("delete from guest");
		jdbc.update("update wedding_settings set default_phone_country = 'ID' where id = 1");
	}

	@Test
	void selectedRegionOverridesWeddingDefaultForNationalInput() {
		Guest guest = service.create(form("Ada", "DE", "01512 3456789"), false);

		assertThat(guest.getNormalizedWhatsappNumber()).isEqualTo("+4915123456789");
	}

	@Test
	void explicitCallingCodeOverridesSelectedRegion() {
		Guest guest = service.create(form("Ada", "ID", "+49 1512 3456789"), false);

		assertThat(guest.getNormalizedWhatsappNumber()).isEqualTo("+4915123456789");
	}

	@Test
	void duplicateWarningMatchesNationalAndInternationalRepresentations() {
		service.create(form("Ada", "DE", "01512 3456789"), false);

		assertThatThrownBy(() -> service.create(form("Bela", "ID", "+49 1512 3456789"), false))
				.isInstanceOf(GuestService.DuplicateWhatsappNumberException.class);
	}

	@Test
	void sentGuestCanOnlyBeArchived() {
		Guest sent = savedGuest("Sari", "081234567890");
		sent.confirmSent(Instant.parse("2026-07-28T06:00:00Z"));
		sent = guests.saveAndFlush(sent);

		Guest current = sent;
		assertThatThrownBy(() -> service.deleteInactive(current.getId(), current.getVersion()))
				.isInstanceOf(IllegalStateException.class);

		service.archive(sent.getId(), sent.getVersion());
		assertThat(guests.findById(sent.getId())).get()
				.extracting(Guest::isArchived).isEqualTo(true);
	}

	@Test
	void duplicateNumberRequiresExplicitAcceptance() {
		savedGuest("Sari", "081234567890");

		assertThatThrownBy(() -> service.create(form("Rina", "0812 3456 7890"), false))
				.isInstanceOf(IllegalStateException.class);

		Guest duplicate = service.create(form("Rina", "0812 3456 7890"), true);
		assertThat(duplicate.getNormalizedWhatsappNumber()).isEqualTo("+6281234567890");
	}

	@Test
	void updatePreservesPublicIdAndDeliveryState() {
		Guest sent = savedGuest("Sari", "081234567890");
		sent.confirmSent(Instant.parse("2026-07-28T06:00:00Z"));
		sent = guests.saveAndFlush(sent);

		Guest updated = service.update(sent.getId(), sent.getVersion(), form("Sari Updated", "081234567890"), true);

		assertThat(updated).extracting(Guest::getDisplayName, Guest::getPublicId,
				Guest::getDeliveryState, Guest::getFirstSentAt)
				.containsExactly("Sari Updated", sent.getPublicId(), DeliveryState.SENT,
						Instant.parse("2026-07-28T06:00:00Z"));
	}

	@Test
	void updateResetsPinSecurityOnlyWhenNormalizedNumberChanges() {
		Guest guest = savedGuest("Sari", "081234567890");
		for (int attempt = 0; attempt < 5; attempt++) {
			guest.pinFailed(Instant.parse("2026-08-01T00:00:00Z"));
		}
		guest = guests.saveAndFlush(guest);

		Guest sameNumber = service.update(guest.getId(), guest.getVersion(),
				form("Sari", "+62 812-3456-7890"), false);

		assertThat(sameNumber.getFailedPinCount()).isEqualTo(5);
		assertThat(sameNumber.getPinLockedUntil()).isNotNull();

		Guest changedNumber = service.update(sameNumber.getId(), sameNumber.getVersion(),
				form("Sari", "+49 1512 3456789"), false);

		assertThat(changedNumber.getFailedPinCount()).isZero();
		assertThat(changedNumber.getPinLockedUntil()).isNull();
	}

	private Guest savedGuest(String name, String whatsappNumber) {
		return service.create(form(name, whatsappNumber), false);
	}

	private GuestForm form(String name, String whatsappNumber) {
		return form(name, "ID", whatsappNumber);
	}

	private GuestForm form(String name, String phoneRegion, String whatsappNumber) {
		return new GuestForm(name, "Ibu", phoneRegion, whatsappNumber, null, false, MessageLanguage.ID, null);
	}
}
