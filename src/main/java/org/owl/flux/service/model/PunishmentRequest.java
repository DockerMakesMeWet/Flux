package org.owl.flux.service.model;

import com.velocitypowered.api.command.CommandSource;
import java.time.Duration;
import org.owl.flux.data.model.PunishmentType;

public record PunishmentRequest(
        CommandSource executor,
        TargetProfile target,
        PunishmentType type,
        Duration duration,
        String reason,
        String templateName,
        String ipOverride,
        boolean ipPunishment
) {
}
