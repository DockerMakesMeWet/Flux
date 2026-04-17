package org.owl.flux.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationParserTest {

    @Test
    void parsesDurationTokens() {
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30m").orElseThrow());
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d").orElseThrow());
        assertEquals(Duration.ofDays(14), DurationParser.parse("2w").orElseThrow());
    }

    @Test
    void treatsPermanentAsNoEndDate() {
        assertTrue(DurationParser.parse("permanent").isEmpty());
        assertTrue(DurationParser.parse("perm").isEmpty());
    }

    @Test
    void validatesDurationTokens() {
        assertTrue(DurationParser.isDurationToken("5h"));
        assertTrue(DurationParser.isDurationToken("perm"));
        assertFalse(DurationParser.isDurationToken("5x"));
    }
}
