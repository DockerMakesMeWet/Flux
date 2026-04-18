package org.owl.flux.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PunishmentTimeFormatterTest {

    @Test
    void formatsRemainingDurationFromNowToEnd() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = now.plusSeconds(2 * 86400L + 4 * 3600L + 10 * 60L);

        assertEquals("2d 4h 10m", PunishmentTimeFormatter.formatRemaining(now, end));
    }

    @Test
    void formatsRemainingAsPermanentWhenNoEndIsPresent() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertTrue(PunishmentTimeFormatter.isPermanent(null));
        assertEquals("Permanent", PunishmentTimeFormatter.formatRemaining(now, null));
        assertEquals("Never", PunishmentTimeFormatter.formatExpiry(null));
    }

    @Test
    void formatsAbsoluteExpiryTimestamp() {
        Instant expiry = Instant.parse("2026-01-03T04:10:00Z");

        assertEquals("2026-01-03 04:10:00 UTC", PunishmentTimeFormatter.formatExpiry(expiry));
        assertEquals("2026-01-03 04:10:00 UTC", PunishmentTimeFormatter.formatTimestamp(expiry));
    }

    @Test
    void clampsExpiredRemainingDurationsToZero() {
        Instant now = Instant.parse("2026-01-01T00:00:30Z");
        Instant end = Instant.parse("2026-01-01T00:00:00Z");

        assertEquals("0s", PunishmentTimeFormatter.formatRemaining(now, end));
    }
}
