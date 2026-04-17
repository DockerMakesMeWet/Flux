package org.owl.flux.data.model;

import java.time.Instant;
import java.util.Map;

public record PunishmentRecord(
        String id,
        PunishmentType type,
        String targetUuid,
        String targetIp,
        String targetUsername,
        String executorUuid,
        String reason,
        Instant startTime,
        Instant endTime,
        boolean active,
        boolean voided,
        boolean issuedOffline,
        boolean joinNoticeDelivered,
        Map<String, String> metadata
) {
    public PunishmentRecord(
            String id,
            PunishmentType type,
            String targetUuid,
            String targetIp,
            String executorUuid,
            String reason,
            Instant startTime,
            Instant endTime,
            boolean active,
            boolean voided,
            Map<String, String> metadata
    ) {
        this(id, type, targetUuid, targetIp, null, executorUuid, reason, startTime, endTime, active, voided, false, false, metadata);
    }
}
