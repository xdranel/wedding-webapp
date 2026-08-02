package myweddinginvitation.webapp.rsvp;

import java.util.UUID;

public record QrReference(UUID publicId, long tokenVersion) {
}
