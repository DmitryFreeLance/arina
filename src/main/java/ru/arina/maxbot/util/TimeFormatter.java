package ru.arina.maxbot.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class TimeFormatter {

    private final ZoneId zoneId;
    private final DateTimeFormatter dateTimeFormatter;

    public TimeFormatter(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zoneId);
    }

    public String format(Instant instant) {
        return instant == null ? "—" : dateTimeFormatter.format(instant);
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
