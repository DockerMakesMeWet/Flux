package org.owl.flux.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhdw])$");

    private DurationParser() {
    }

    public static boolean isDurationToken(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("permanent") || value.equals("perm") || DURATION_PATTERN.matcher(value).matches();
    }

    public static Optional<Duration> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank() || value.equals("permanent") || value.equals("perm")) {
            return Optional.empty();
        }

        Matcher matcher = DURATION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        return Optional.of(switch (unit) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(amount * 7L);
            default -> throw new IllegalStateException("Unhandled unit: " + unit);
        });
    }
}
