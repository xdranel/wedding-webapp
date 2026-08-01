package myweddinginvitation.webapp.rsvp;

import java.time.LocalDate;

public record PublicGreetingView(String displayName, String greeting, LocalDate date) {
}
