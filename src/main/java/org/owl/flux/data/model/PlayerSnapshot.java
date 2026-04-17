package org.owl.flux.data.model;

public record PlayerSnapshot(
        String uuid,
        String username,
        String lastIp
) {
}
