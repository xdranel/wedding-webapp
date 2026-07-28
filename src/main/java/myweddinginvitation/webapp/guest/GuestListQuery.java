package myweddinginvitation.webapp.guest;

public record GuestListQuery(String query, DeliveryState delivery, Boolean archived, Long categoryId) {
}
