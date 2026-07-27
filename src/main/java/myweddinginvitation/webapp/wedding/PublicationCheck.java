package myweddinginvitation.webapp.wedding;

import java.util.List;

public record PublicationCheck(boolean published, List<String> errors) {
	public PublicationCheck {
		errors = List.copyOf(errors);
	}
}
