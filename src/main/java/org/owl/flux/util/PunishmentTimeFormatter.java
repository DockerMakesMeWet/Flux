package org.owl.flux.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class PunishmentTimeFormatter {
    public static final String PERMANENT_LABEL = "Permanent";
    public static final String NEVER_LABEL = "Never";
    private static final DateTimeFormatter HUMAN_UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private PunishmentTimeFormatter() {
    }

    public static String formatRemaining(Instant endTime) {
        return formatRemaining(Instant.now(), endTime);
    }

    public static String formatRemaining(Instant now, Instant endTime) {
        if (endTime == null) {
            return PERMANENT_LABEL;
        }
        return formatDuration(now, endTime);
    }

    public static String formatExpiry(Instant endTime) {
        if (endTime == null) {
            return NEVER_LABEL;
        }
        return formatTimestamp(endTime);
    }

    public static String formatTimestamp(Instant instant) {
        return HUMAN_UTC_FORMATTER.format(instant);
    }

    public static String formatTimestampHumanUtc(Instant instant) {
        return formatTimestamp(instant);
    }

    public static boolean isPermanent(Instant endTime) {
        return endTime == null;
    }

    public static String formatDuration(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        long seconds = Math.max(0, duration.getSeconds());
        if (seconds == 0) {
            return "0s";
        }

        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder builder = new StringBuilder();
        appendDurationPart(builder, days, "d");
        appendDurationPart(builder, hours, "h");
        appendDurationPart(builder, minutes, "m");
        appendDurationPart(builder, seconds, "s");
        return builder.toString().trim();
    }

    private static void appendDurationPart(StringBuilder builder, long value, String unit) {
        if (value <= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value).append(unit);
    }
}
