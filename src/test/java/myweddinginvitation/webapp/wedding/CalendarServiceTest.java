package myweddinginvitation.webapp.wedding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarServiceTest {
    private final CalendarService service = new CalendarService();

    @Test
    void createsSeparateLocalizedEventFilesWithStableGuestIndependentUid() {
        WeddingPreview preview = preview(
                event(EventType.CEREMONY, LocalTime.of(9, 0), LocalTime.of(10, 30), "Gereja", "Jl. Mawar", "https://maps.example/ceremony"),
                event(EventType.RECEPTION, LocalTime.of(18, 0), null, "Ballroom", "Rose Street", "https://maps.example/reception"));

        String ceremony = content(service.create(preview, EventType.CEREMONY, "ID", "https://invite.example/a", "Asia/Jakarta").orElseThrow());
        String ceremonyForAnotherGuest = content(service.create(preview, EventType.CEREMONY, "ID", "https://invite.example/b", "Asia/Jakarta").orElseThrow());
        String reception = content(service.create(preview, EventType.RECEPTION, "EN", "https://invite.example/a", "Asia/Jakarta").orElseThrow());

        assertThat(ceremony).contains("SUMMARY:Akad - A & B", "LOCATION:Gereja\\, Jl. Mawar", "DTSTART;TZID=Asia/Jakarta:20270502T090000",
                "DTEND;TZID=Asia/Jakarta:20270502T103000", "https://maps.example/ceremony", "https://invite.example/a")
                .doesNotContain("BEGIN:VALARM");
        assertThat(ceremony).containsPattern("DTSTAMP:\\d{8}T\\d{6}Z");
        assertThat(ceremony).contains("BEGIN:VTIMEZONE\r\nTZID:Asia/Jakarta\r\nBEGIN:STANDARD\r\nDTSTART:19700101T000000\r\n"
                + "TZOFFSETFROM:+0700\r\nTZOFFSETTO:+0700\r\nTZNAME:WIB\r\nEND:STANDARD\r\nEND:VTIMEZONE");
        assertThat(ceremony.indexOf("BEGIN:VCALENDAR")).isLessThan(ceremony.indexOf("BEGIN:VTIMEZONE"));
        assertThat(ceremony.indexOf("END:VTIMEZONE")).isLessThan(ceremony.indexOf("BEGIN:VEVENT"));
        assertThat(ceremony.indexOf("END:VEVENT")).isLessThan(ceremony.indexOf("END:VCALENDAR"));
        assertThat(reception).contains("SUMMARY:Reception - A & B", "LOCATION:Ballroom\\, Rose Street",
                "DTEND;TZID=Asia/Jakarta:20270502T210000", "https://maps.example/reception");
        assertThat(uid(ceremony)).isEqualTo(uid(ceremonyForAnotherGuest)).isNotEqualTo(uid(reception));
        assertThat(service.create(preview, EventType.CEREMONY, "ID", "https://invite.example/a", "Asia/Jakarta").orElseThrow().filename())
                .isEqualTo("wedding-ceremony.ics");
        assertThat(content(service.create(preview(event(EventType.CEREMONY, LocalTime.of(9, 0), null, "Gereja", "Jl. Mawar", null)),
                EventType.CEREMONY, "ID", "https://invite.example/a", "Asia/Jakarta").orElseThrow()))
                .contains("DTEND;TZID=Asia/Jakarta:20270502T100000");
    }

    @Test
    void returnsEmptyForAbsentOrIncompleteEvents() {
        WeddingPreview absent = preview();
        WeddingPreview incomplete = preview(event(EventType.CEREMONY, LocalTime.of(9, 0), null, "", "Jl. Mawar", null));

        assertThat(service.create(absent, EventType.CEREMONY, "ID", "https://invite.example/a", "Asia/Jakarta")).isEmpty();
        assertThat(service.create(incomplete, EventType.CEREMONY, "ID", "https://invite.example/a", "Asia/Jakarta")).isEmpty();
    }

    @Test
    void escapesTextAndUsesOnlyCrLfWithUtf8SafeFolding() {
        String address = "Jalan, Semicolon; Slash\\\n" + "é".repeat(40);
        CalendarFile file = service.create(preview(event(EventType.CEREMONY, LocalTime.of(9, 0), null, "Gedung, A; \\", address, "https://maps.example/a")),
                EventType.CEREMONY, "ID", "https://invite.example/a", "Asia/Jakarta").orElseThrow();
        String calendar = content(file);

        String unfolded = calendar.replace("\r\n ", "");
        assertThat(unfolded).contains("LOCATION:Gedung\\, A\\; " + "\\\\" + "\\, Jalan\\, Semicolon\\; Slash" + "\\\\" + "\\n");
        assertThat(unfolded).contains("é".repeat(40));
        assertThat(unfolded).contains("Map: https://maps.example/a\\nInvitation: https://invite.example/a");
        assertThat(calendar).matches("(?s)(?:[^\\r\\n]|\\r\\n)*").endsWith("\r\n");
        for (String line : calendar.split("\\r\\n", -1)) {
            assertThat(line.getBytes(UTF_8).length).isLessThanOrEqualTo(75);
        }
        assertThat(calendar).contains("\r\n ");
    }

    private WeddingPreview preview(WeddingPreview.EventView... events) {
        return new WeddingPreview(null, "A & B", null, null, null, null, null, null, null, List.of(), List.of(events), List.of());
    }

    private WeddingPreview.EventView event(EventType type, LocalTime start, LocalTime end, String venue, String address, String mapUrl) {
        return new WeddingPreview.EventView(type, LocalDate.of(2027, 5, 2), start, end, venue, address, mapUrl);
    }

    private String content(CalendarFile file) {
        return new String(file.content(), UTF_8);
    }

    private String uid(String calendar) {
        return calendar.lines().filter(line -> line.startsWith("UID:")).findFirst().orElseThrow();
    }
}
