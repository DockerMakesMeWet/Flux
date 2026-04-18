package org.owl.flux.data.model;

import java.time.Instant;

public record ModerationActionRecord(
        long id,
        ModerationActionType actionType,
        String targetReference,
        String punishmentId,
        String executorUuid,
        String reason,
        Instant createdAt
) {
}
