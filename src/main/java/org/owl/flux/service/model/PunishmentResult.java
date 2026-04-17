package org.owl.flux.service.model;

import org.owl.flux.data.model.PunishmentRecord;

public record PunishmentResult(
        PunishmentRecord punishment,
        int disconnectedCount
) {
}
