package org.owl.flux.data.model;

import java.time.Instant;

public record IpVisit(
        String ip,
        Instant lastSeen
) {
}
