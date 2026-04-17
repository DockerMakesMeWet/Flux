package org.owl.flux.data.model;

import java.time.Instant;

public record AccountVisit(
        String account,
        Instant lastSeen
) {
}
