package myweddinginvitation.webapp.rsvp;

import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.INVALID;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.LOCKED;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.MALFORMED;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.SUCCESS;
import static myweddinginvitation.webapp.rsvp.PinVerificationResult.Status.UNAVAILABLE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import myweddinginvitation.webapp.guest.Guest;
import myweddinginvitation.webapp.guest.GuestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestPinService {
	private final GuestRepository guests;
	private final Clock clock;

	public GuestPinService(GuestRepository guests, Clock clock) {
		this.guests = guests;
		this.clock = clock;
	}

	@Transactional
	public PinVerificationResult verify(UUID publicId, long tokenVersion, String submittedPin) {
		if (submittedPin == null || !submittedPin.matches("[0-9]{4}")) {
			return new PinVerificationResult(MALFORMED, null);
		}
		if (publicId == null) return new PinVerificationResult(UNAVAILABLE, null);

		Guest guest = guests.findByPublicIdForUpdate(publicId).orElse(null);
		if (guest == null || guest.isArchived() || guest.getInvitationTokenVersion() != tokenVersion) {
			return new PinVerificationResult(UNAVAILABLE, null);
		}

		Instant now = clock.instant();
		if (guest.getPinLockedUntil() != null && now.isBefore(guest.getPinLockedUntil())) {
			return new PinVerificationResult(LOCKED, guest.getPinLockedUntil());
		}

		String number = guest.getNormalizedWhatsappNumber();
		if (number == null || number.length() < 4) return new PinVerificationResult(UNAVAILABLE, null);
		String expectedPin = number.substring(number.length() - 4);
		if (MessageDigest.isEqual(expectedPin.getBytes(StandardCharsets.UTF_8),
				submittedPin.getBytes(StandardCharsets.UTF_8))) {
			guest.pinSucceeded();
			guests.saveAndFlush(guest);
			return new PinVerificationResult(SUCCESS, null);
		}

		guest.pinFailed(now);
		guests.saveAndFlush(guest);
		if (guest.getPinLockedUntil() != null && now.isBefore(guest.getPinLockedUntil())) {
			return new PinVerificationResult(LOCKED, guest.getPinLockedUntil());
		}
		return new PinVerificationResult(INVALID, null);
	}

	@Transactional
	public void clear(long guestId) {
		Guest guest = guests.findById(guestId).orElseThrow(NoSuchElementException::new);
		guest.clearPinLock();
		guests.saveAndFlush(guest);
	}
}
