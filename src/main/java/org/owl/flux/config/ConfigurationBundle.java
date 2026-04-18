package org.owl.flux.config;

import org.owl.flux.config.model.DiscordConfig;
import org.owl.flux.config.model.MainConfig;
import org.owl.flux.config.model.MessagesConfig;
import org.owl.flux.config.model.MutedCommandsConfig;
import org.owl.flux.config.model.TemplatesConfig;

public record ConfigurationBundle(
        MainConfig main,
        MessagesConfig messages,
        TemplatesConfig templates,
        DiscordConfig discord,
        MutedCommandsConfig mutedCommands
) {
}
