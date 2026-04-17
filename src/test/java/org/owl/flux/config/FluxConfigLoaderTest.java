package org.owl.flux.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FluxConfigLoaderTest {

    @Test
    void rejectsDiscordConfigWithoutActions() throws IOException {
        Path dir = Files.createTempDirectory("flux-config-test-invalid");
        writeBaseFiles(dir);
        Files.writeString(dir.resolve("discord.yml"), """
                webhook:
                  enabled: false
                  url: ""
                  username: "Flux"
                  avatar-url: ""
                  timeout-ms: 8000
                actions: {}
                """);

        FluxConfigLoader loader = new FluxConfigLoader(dir);
        assertThrows(ConfigValidationException.class, loader::load);
    }

    @Test
    void rejectsDiscordConfigMissingRequiredActions() throws IOException {
        Path dir = Files.createTempDirectory("flux-config-test-valid");
        writeBaseFiles(dir);
        Files.writeString(dir.resolve("discord.yml"), """
                webhook:
                  enabled: false
                  url: ""
                  username: "Flux"
                  avatar-url: ""
                  timeout-ms: 8000
                actions:
                  ban:
                    enabled: true
                    title: "Ban Issued"
                    description: "<executor> banned <target>"
                """);

        FluxConfigLoader loader = new FluxConfigLoader(dir);
        assertThrows(ConfigValidationException.class, loader::load);
    }

    @Test
    void loadsValidActionBasedDiscordConfig() throws IOException {
        Path dir = Files.createTempDirectory("flux-config-test-valid-full");
        writeBaseFiles(dir);
        writeValidDiscordConfig(dir);

        FluxConfigLoader loader = new FluxConfigLoader(dir);
        assertDoesNotThrow(loader::load);
    }

    @Test
    void loadsDefaultValuesForNewMessageKeysWhenMissingFromLegacyFile() throws IOException {
        Path dir = Files.createTempDirectory("flux-config-test-message-defaults");
        writeBaseFiles(dir);
        writeValidDiscordConfig(dir);

        FluxConfigLoader loader = new FluxConfigLoader(dir);
        ConfigurationBundle bundle = loader.load();

        assertEquals("Flux", bundle.messages().branding.serverName);
        assertEquals("discord.gg/flux", bundle.messages().branding.discord);
        assertEquals("DuckyMC", bundle.messages().userBranding.serverName);
        assertEquals("dsc.gg/mcducky", bundle.messages().userBranding.discord);
        assertEquals("<gradient:#f7d74b:#ffd86b><bold><server></bold></gradient> <gray>»</gray> ", bundle.messages().userPrefix);
        assertEquals(
                "<yellow>You were <white><type></white> by <white><executor></white>.</yellow><newline><gray>Reason:</gray> <white><reason></white><newline><gray>Expires At:</gray> <white><expires_at></white><newline><gray>Time Left:</gray> <white><time_left></white><newline><gray>Action ID:</gray> <white><id></white><newline><id_warning>",
                bundle.messages().player.punishedNotice
        );
        assertEquals(
                "<yellow>Your mute has been lifted. You're free to chat again.</yellow>",
                bundle.messages().player.unmutedNotice
        );
        assertEquals(
                "<yellow>A warning was removed from your record.</yellow> <gray>(ID: <white><id></white>)</gray><newline><id_warning>",
                bundle.messages().player.warnRemovedNotice
        );
        assertEquals(
                "<red>Warning:</red> <yellow>Sharing this ID may hurt your appeal chances.</yellow>",
                bundle.messages().player.idShareWarning
        );
        assertEquals(
                "<yellow>Welcome back to <white><server></white>! While you were away, <white><count></white> <white><label></white> <white><verb></white> applied to your account.</yellow>",
                bundle.messages().player.offlineJoinHeader
        );
        assertEquals(
                "<gray>•</gray> <yellow><type></yellow> <gray>(ID: <white><id></white>)</gray> <gray>-</gray> <white><reason></white><newline><id_warning>",
                bundle.messages().player.offlineJoinEntry
        );
        assertEquals(
                "<yellow><bold>Action Details</bold></yellow><newline><gray>ID:</gray> <white><id></white><newline><gray>Type:</gray> <white><type></white><newline><gray>Target:</gray> <white><target></white><newline><gray>Executor:</gray> <white><executor></white><newline><gray>Reason:</gray> <white><reason></white><newline><gold>Click to suggest /check <id></gold>",
                bundle.messages().staff.actionBroadcastHover
        );
    }

    private static void writeValidDiscordConfig(Path dir) throws IOException {
        Files.writeString(dir.resolve("discord.yml"), """
                webhook:
                  enabled: false
                  url: ""
                  username: "Flux"
                  avatar-url: ""
                  timeout-ms: 8000
                actions:
                  ban:
                    enabled: true
                    title: "Ban Issued"
                    description: "<executor> banned <target>"
                  mute:
                    enabled: true
                    title: "Mute Issued"
                    description: "<executor> muted <target>"
                  kick:
                    enabled: true
                    title: "Kick Issued"
                    description: "<executor> kicked <target>"
                  warn:
                    enabled: true
                    title: "Warning Issued"
                    description: "<executor> warned <target>"
                  void:
                    enabled: true
                    title: "Action Voided"
                    description: "<executor> voided <target>"
                  unban:
                    enabled: true
                    title: "Ban Lifted"
                    description: "<executor> unbanned <target>"
                  unmute:
                    enabled: true
                    title: "Mute Lifted"
                    description: "<executor> unmuted <target>"
                """);
    }

    private static void writeBaseFiles(Path dir) throws IOException {
        Files.writeString(dir.resolve("config.yml"), """
                database:
                  provider: "postgresql"
                  postgresql:
                    host: "127.0.0.1"
                    port: 5432
                    database: "flux"
                    username: "flux"
                    password: "flux"
                    server-version: "17"
                    pool:
                      maximum-pool-size: 10
                      minimum-idle: 2
                      connection-timeout-ms: 30000
                  h2:
                    file: "flux-data"
                masters:
                  refresh-on-reload: true
                """);
        Files.writeString(dir.resolve("messages.yml"), """
                prefix: "<gray>Flux</gray>"
                errors:
                  no-permission: "a"
                  player-not-found: "b"
                  target-protected: "c"
                staff:
                  action-broadcast: "d"
                  void-broadcast: "e"
                commands:
                  action-created: "f"
                  action-updated: "g"
                  action-not-found: "h"
                  history-header: "i"
                  history-entry: "j"
                  alts-header: "k"
                  alts-entry: "l"
                  iphistory-header: "m"
                  iphistory-entry: "n"
                  reload-success: "o"
                  reload-partial: "p"
                  version: "q"
                screens:
                  ban-screen: "r"
                  kick-screen: "s"
                  muted-message: "t"
                """);
        Files.writeString(dir.resolve("templates.yml"), """
                templates:
                  sample:
                    permission: "flux.template.sample"
                    type: "BAN"
                    tiers:
                      - duration: "1d"
                        reason: "sample"
                """);
    }
}
