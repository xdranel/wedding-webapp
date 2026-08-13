package myweddinginvitation.webapp.wedding;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class CalendarService {
    private static final String TIME_ZONE = "Asia/Jakarta";
    private static final ZoneId JAKARTA = ZoneId.of(TIME_ZONE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_DATE_TIME = DATE_TIME.withZone(ZoneOffset.UTC);

    public Optional<CalendarFile> create(WeddingPreview preview, EventType type, String language, String invitationUrl, String timeZone) {
        if (!TIME_ZONE.equals(timeZone)) return Optional.empty();
        ZoneId zoneId = JAKARTA;
        WeddingPreview.EventView event = preview.events().stream().filter(candidate -> candidate.type() == type).findFirst().orElse(null);
        if (event == null || !complete(event)) return Optional.empty();

        LocalDateTime start = LocalDateTime.of(event.date(), event.startTime());
        LocalDateTime end = event.endTime() == null ? start.plusHours(type == EventType.CEREMONY ? 1 : 3) : LocalDateTime.of(event.date(), event.endTime());
        String name = "EN".equals(language) ? type == EventType.CEREMONY ? "Ceremony" : "Reception" : type == EventType.CEREMONY ? "Akad" : "Resepsi";
        List<String> properties = List.of(
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//myweddinginvitation.webapp//Wedding Invitation//EN", "CALSCALE:GREGORIAN",
                "BEGIN:VTIMEZONE", "TZID:" + zoneId.getId(), "BEGIN:STANDARD", "DTSTART:19700101T000000", "TZOFFSETFROM:+0700", "TZOFFSETTO:+0700",
                "TZNAME:WIB", "END:STANDARD", "END:VTIMEZONE",
                "BEGIN:VEVENT", "UID:" + uid(type, invitationUrl), "DTSTAMP:" + UTC_DATE_TIME.format(Instant.now()) + "Z", "SUMMARY:" + text(name + " - " + preview.coupleTitle()),
                dateTime("DTSTART", start, zoneId), dateTime("DTEND", end, zoneId),
                "LOCATION:" + text(event.venueName() + ", " + event.address()),
                "DESCRIPTION:" + text(description(event, invitationUrl)), "URL:" + invitationUrl, "END:VEVENT", "END:VCALENDAR");
        return Optional.of(new CalendarFile("wedding-" + type.name().toLowerCase() + ".ics", content(properties)));
    }

    public Set<EventType> availableEventTypes(WeddingPreview preview, String timeZone) {
        if (!TIME_ZONE.equals(timeZone)) return Set.of();
        return preview.events().stream().filter(this::complete).map(WeddingPreview.EventView::type)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean complete(WeddingPreview.EventView event) {
        return event.date() != null && event.startTime() != null && hasText(event.venueName()) && hasText(event.address());
    }

    private String dateTime(String name, LocalDateTime value, ZoneId zoneId) {
        return name + ";TZID=" + zoneId.getId() + ":" + DATE_TIME.format(value);
    }

    private String description(WeddingPreview.EventView event, String invitationUrl) {
        String map = hasText(event.mapUrl()) ? "\nMap: " + event.mapUrl() : "";
        return event.address() + map + "\nInvitation: " + invitationUrl;
    }

    private String uid(EventType type, String invitationUrl) {
        URI invitation = URI.create(invitationUrl);
        String scheme = invitation.getScheme().toLowerCase(Locale.ROOT);
        String host = invitation.getHost().toLowerCase(Locale.ROOT);
        int port = invitation.getPort() >= 0 ? invitation.getPort() : "https".equals(scheme) ? 443 : 80;
        return UUID.nameUUIDFromBytes((scheme + "://" + host + ":" + port + ":" + type).getBytes(UTF_8))
                + "@myweddinginvitation.webapp";
    }

    private String text(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    private byte[] content(List<String> properties) {
        return String.join("\r\n", properties.stream().flatMap(property -> fold(property).stream()).toList()).concat("\r\n").getBytes(UTF_8);
    }

    private List<String> fold(String property) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int bytes = 0;
        for (int index = 0; index < property.length();) {
            int codePoint = property.codePointAt(index);
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(UTF_8).length;
            if (bytes + size > 75) {
                lines.add(line.toString());
                line = new StringBuilder(" ");
                bytes = 1;
            }
            line.append(character);
            bytes += size;
            index += Character.charCount(codePoint);
        }
        lines.add(line.toString());
        return lines;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
