package org.owl.flux.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.owl.flux.config.model.DiscordConfig;
import org.owl.flux.config.model.MainConfig;
import org.owl.flux.config.model.MessagesConfig;
import org.owl.flux.config.model.MutedCommandsConfig;
import org.owl.flux.config.model.TemplatesConfig;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class FluxConfigLoader {
    private final Path dataDirectory;

    public FluxConfigLoader(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public ConfigurationBundle load() {
        MainConfig main = loadFile("config.yml", MainConfig.class);
        MessagesConfig messages = loadFile("messages.yml", MessagesConfig.class);
        TemplatesConfig templates = loadFile("templates.yml", TemplatesConfig.class);
        DiscordConfig discord = loadFile("discord.yml", DiscordConfig.class);
        MutedCommandsConfig mutedCommands = loadFile("mutedcmds.yml", MutedCommandsConfig.class);

        validateMain(main);
        validateMessages(messages);
        validateTemplates(templates);
        validateDiscord(discord);
        validateMutedCommands(mutedCommands);
        return new ConfigurationBundle(main, messages, templates, discord, mutedCommands);
    }

    private <T> T loadFile(String fileName, Class<T> type) {
        Path path = dataDirectory.resolve(fileName);
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(path)
                .build();

        try {
            ConfigurationNode root = loader.load();
            T value = root.get(type);
            if (value == null) {
                throw new ConfigValidationException("Configuration '" + fileName + "' is empty.");
            }
            return value;
        } catch (ConfigurateException exception) {
            throw new ConfigValidationException("Failed to read configuration '" + fileName + "'.", exception);
        }
    }

    private static void validateMain(MainConfig config) {
        String provider = normalize(config.database.provider);
        if (!provider.equals("postgresql") && !provider.equals("h2")) {
            throw new ConfigValidationException("config.yml database.provider must be either 'postgresql' or 'h2'.");
        }

        if (provider.equals("postgresql")) {
            if (config.database.postgresql.serverVersion == null
                    || !config.database.postgresql.serverVersion.trim().startsWith("17")) {
                throw new ConfigValidationException("config.yml postgresql.server-version must target PostgreSQL 17 (for example: '17' or '17.4').");
            }
        }

    }

    private static void validateMessages(MessagesConfig messages) {
        if (isBlank(messages.prefix)) {
            throw new ConfigValidationException("messages.yml prefix must not be blank.");
        }
        if (isBlank(messages.userPrefix)) {
            throw new ConfigValidationException("messages.yml user-prefix must not be blank.");
        }
    }

    private static void validateTemplates(TemplatesConfig templates) {
        if (templates.templates == null || templates.templates.isEmpty()) {
            throw new ConfigValidationException("templates.yml must contain at least one template.");
        }

        for (Map.Entry<String, TemplatesConfig.TemplateDefinition> entry : templates.templates.entrySet()) {
            String templateName = entry.getKey();
            TemplatesConfig.TemplateDefinition definition = entry.getValue();
            if (definition == null) {
                throw new ConfigValidationException("templates.yml template '" + templateName + "' is null.");
            }
            if (isBlank(definition.permission)) {
                throw new ConfigValidationException("templates.yml template '" + templateName + "' must define permission.");
            }
            if (isBlank(definition.type)) {
                throw new ConfigValidationException("templates.yml template '" + templateName + "' must define type.");
            }
            if (definition.tiers == null || definition.tiers.isEmpty()) {
                throw new ConfigValidationException("templates.yml template '" + templateName + "' must define at least one tier.");
            }

            for (int i = 0; i < definition.tiers.size(); i++) {
                TemplatesConfig.TemplateTier tier = definition.tiers.get(i);
                if (tier == null || isBlank(tier.reason)) {
                    throw new ConfigValidationException(
                            "templates.yml template '" + templateName + "' tier " + (i + 1) + " must define reason."
                    );
                }
                if (isBlank(tier.duration)) {
                    throw new ConfigValidationException(
                            "templates.yml template '" + templateName + "' tier " + (i + 1) + " must define duration."
                    );
                }
            }
        }
    }

    private static void validateDiscord(DiscordConfig discord) {
        List<String> requiredActions = List.of("ban", "mute", "kick", "warn", "void", "unban", "unmute");
        if (discord.webhook.enabled) {
            if (isBlank(discord.webhook.url)
                    || !(discord.webhook.url.startsWith("http://") || discord.webhook.url.startsWith("https://"))) {
                throw new ConfigValidationException("discord.yml webhook.url must be a valid URL when webhook.enabled is true.");
            }
        }

        if (discord.actions == null || discord.actions.isEmpty()) {
            throw new ConfigValidationException("discord.yml must define at least one action webhook configuration.");
        }
        if (!discord.actions.keySet().containsAll(requiredActions)) {
            throw new ConfigValidationException(
                    "discord.yml actions must define: " + String.join(", ", requiredActions) + "."
            );
        }

        for (Map.Entry<String, DiscordConfig.ActionWebhookConfig> entry : discord.actions.entrySet()) {
            String action = entry.getKey();
            DiscordConfig.ActionWebhookConfig actionConfig = entry.getValue();
            if (actionConfig == null) {
                throw new ConfigValidationException("discord.yml action '" + action + "' is null.");
            }
            if (isBlank(actionConfig.title)) {
                throw new ConfigValidationException("discord.yml action '" + action + "' title must not be blank.");
            }
            if (isBlank(actionConfig.description)) {
                throw new ConfigValidationException("discord.yml action '" + action + "' description must not be blank.");
            }
        }
    }

    private static void validateMutedCommands(MutedCommandsConfig mutedCommands) {
        if (mutedCommands.vanilla == null) {
            throw new ConfigValidationException("mutedcmds.yml vanilla section must not be null.");
        }
        if (mutedCommands.messageCommands == null) {
            throw new ConfigValidationException("mutedcmds.yml message-commands section must not be null.");
        }
        if (mutedCommands.essentialsxMessageCommands == null) {
            throw new ConfigValidationException("mutedcmds.yml essentialsx-message-commands section must not be null.");
        }

        int enabledCount = 0;
        enabledCount += validateMutedCommandGroup("vanilla", mutedCommands.vanilla);
        enabledCount += validateMutedCommandGroup("message-commands", mutedCommands.messageCommands);
        enabledCount += validateMutedCommandGroup("essentialsx-message-commands", mutedCommands.essentialsxMessageCommands);

        if (enabledCount <= 0) {
            throw new ConfigValidationException("mutedcmds.yml must enable at least one blocked command.");
        }
    }

    private static int validateMutedCommandGroup(String groupName, Map<String, MutedCommandsConfig.CommandRule> group) {
        int enabledCount = 0;
        for (Map.Entry<String, MutedCommandsConfig.CommandRule> entry : group.entrySet()) {
            String command = normalize(entry.getKey());
            if (command.isEmpty()) {
                throw new ConfigValidationException("mutedcmds.yml " + groupName + " contains a blank command key.");
            }
            MutedCommandsConfig.CommandRule rule = entry.getValue();
            if (rule == null) {
                throw new ConfigValidationException(
                        "mutedcmds.yml " + groupName + " command '" + command + "' must define a command rule."
                );
            }
            if (rule.enabled) {
                enabledCount++;
            }
        }
        return enabledCount;
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
