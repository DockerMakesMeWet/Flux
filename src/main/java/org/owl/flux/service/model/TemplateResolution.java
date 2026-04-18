package org.owl.flux.service.model;

import java.time.Duration;
import java.util.Optional;
import org.owl.flux.data.model.PunishmentType;

public record TemplateResolution(
        PunishmentType type,
        Duration duration,
        String reason,
        String templateName,
        int offenseStep,
        String offenseLabel
) {
    public Optional<Duration> optionalDuration() {
        return Optional.ofNullable(duration);
    }
}
