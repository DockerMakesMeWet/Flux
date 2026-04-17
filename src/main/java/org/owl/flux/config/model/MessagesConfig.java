package org.owl.flux.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class MessagesConfig {
    public String prefix = "<gradient:#00FBFF:#9D00FF><bold><server></bold></gradient> <gray>»</gray> ";
    @Setting("user-prefix")
    public String userPrefix = "<gradient:#f7d74b:#ffd86b><bold><server></bold></gradient> <gray>»</gray> ";
    public Branding branding = new Branding();
    @Setting("user-branding")
    public UserBranding userBranding = new UserBranding();
    public ErrorMessages errors = new ErrorMessages();
    public StaffMessages staff = new StaffMessages();
    public CommandMessages commands = new CommandMessages();
    public PlayerMessages player = new PlayerMessages();
    public ScreenMessages screens = new ScreenMessages();

    @ConfigSerializable
    public static final class Branding {
        @Setting("server-name")
        public String serverName = "Flux";

        @Setting("discord")
        public String discord = "discord.gg/flux";
    }

    @ConfigSerializable
    public static final class UserBranding {
        @Setting("server-name")
        public String serverName = "DuckyMC";

        @Setting("discord")
        public String discord = "dsc.gg/mcducky";
    }

    @ConfigSerializable
    public static final class ErrorMessages {
        @Setting("no-permission")
        public String noPermission = "<red>You don't have permission to use that command.</red>";

        @Setting("player-not-found")
        public String playerNotFound = "<red>Couldn't find player <white><player></white>.</red>";

        @Setting("target-protected")
        public String targetProtected = "<red>You can't take moderation action on that player.</red>";
    }

    @ConfigSerializable
    public static final class StaffMessages {
        @Setting("action-broadcast")
        public String actionBroadcast =
                "<gold><bold>STAFF</bold></gold> <gray>•</gray> <yellow><type></yellow> <white><target></white> <gray>by</gray> <white><executor></white> <dark_gray>[<id>]</dark_gray>";

        @Setting("action-broadcast-hover")
        public String actionBroadcastHover =
                "<yellow><bold>Action Details</bold></yellow><newline><gray>ID:</gray> <white><id></white><newline><gray>Type:</gray> <white><type></white><newline><gray>Target:</gray> <white><target></white><newline><gray>Executor:</gray> <white><executor></white><newline><gray>Reason:</gray> <white><reason></white><newline><gold>Click to suggest /check <id></gold>";

        @Setting("void-broadcast")
        public String voidBroadcast =
                "<gold><bold>STAFF</bold></gold> <gray>•</gray> <white><executor></white> <red>voided</red> <yellow><target_id></yellow> <dark_gray>[<id>]</dark_gray>";

        @Setting("void-broadcast-hover")
        public String voidBroadcastHover =
                "<yellow><bold>Void Details</bold></yellow><newline><gray>Void Action ID:</gray> <white><id></white><newline><gray>Target Action ID:</gray> <white><target_id></white><newline><gray>Executor:</gray> <white><executor></white><newline><gold>Click to suggest /check <target_id></gold>";
    }

    @ConfigSerializable
    public static final class CommandMessages {
        @Setting("action-created")
        public String actionCreated = "<yellow>Action logged: <white><type></white> on <white><target></white> <gray>(ID: <id>)</gray></yellow>";

        @Setting("action-updated")
        public String actionUpdated = "<yellow>Updated punishment state for <white><target></white>.</yellow>";

        @Setting("action-not-found")
        public String actionNotFound = "<red>No matching punishment was found.</red>";

        @Setting("history-header")
        public String historyHeader = "<yellow>History for <white><target></white>:</yellow>";

        @Setting("history-entry")
        public String historyEntry = "<gray>•</gray> <white><id></white> <yellow><type></yellow> <gray>-</gray> <white><reason></white> <dark_gray>(voided=<voided>)</dark_gray>";

        @Setting("history-entry-hover")
        public String historyEntryHover =
                "<yellow><bold>History Entry</bold></yellow><newline><gray>ID:</gray> <white><id></white><newline><gray>Type:</gray> <white><type></white><newline><gray>Reason:</gray> <white><reason></white><newline><gray>Voided:</gray> <white><voided></white><newline><gold>Click to suggest /check <id></gold>";

        @Setting("alts-header")
        public String altsHeader = "<yellow>Accounts sharing IP <white><ip></white>:</yellow>";

        @Setting("alts-entry")
        public String altsEntry = "<gray>•</gray> <white><username></white>";

        @Setting("iphistory-header")
        public String ipHistoryHeader = "<yellow>IP history for <white><target></white>:</yellow>";

        @Setting("iphistory-entry")
        public String ipHistoryEntry = "<gray>•</gray> <white><ip></white> <dark_gray>(<seen>)</dark_gray>";

        @Setting("reload-success")
        public String reloadSuccess = "<yellow><server></yellow><gray> configs reloaded and masters refreshed.</gray>";

        @Setting("reload-partial")
        public String reloadPartial = "<yellow><server></yellow><gray> configs reloaded, but masters refresh failed; previous cache retained.</gray>";

        @Setting("version")
        public String version = "<yellow><server></yellow> <gray>v<white><version></white></gray>";
    }

    @ConfigSerializable
    public static final class PlayerMessages {
        @Setting("punished-notice")
        public String punishedNotice =
                "<yellow>You were <white><type></white> by <white><executor></white>.</yellow><newline><gray>Reason:</gray> <white><reason></white><newline><gray>Expires At:</gray> <white><expires_at></white><newline><gray>Time Left:</gray> <white><time_left></white><newline><gray>Action ID:</gray> <white><id></white><newline><id_warning>";

        @Setting("unmuted-notice")
        public String unmutedNotice = "<yellow>Your mute has been lifted. You're free to chat again.</yellow>";

        @Setting("warn-removed-notice")
        public String warnRemovedNotice =
                "<yellow>A warning was removed from your record.</yellow> <gray>(ID: <white><id></white>)</gray><newline><id_warning>";

        @Setting("id-share-warning")
        public String idShareWarning = "<red>Warning:</red> <yellow>Sharing this ID may hurt your appeal chances.</yellow>";

        @Setting("offline-join-header")
        public String offlineJoinHeader =
                "<yellow>Welcome back to <white><server></white>! While you were away, <white><count></white> <white><label></white> <white><verb></white> applied to your account.</yellow>";

        @Setting("offline-join-entry")
        public String offlineJoinEntry =
                "<gray>•</gray> <yellow><type></yellow> <gray>(ID: <white><id></white>)</gray> <gray>-</gray> <white><reason></white><newline><id_warning>";
    }

    @ConfigSerializable
    public static final class ScreenMessages {
        @Setting("ban-screen")
        public String banScreen =
                "<gradient:#f7d74b:#ffd86b><bold><server></bold></gradient><newline><red>You are currently banned from this network.</red><newline><gray>Reason:</gray> <white><reason></white><newline><gray>Expires At:</gray> <white><expires_at></white><newline><gray>Time Left:</gray> <white><time_left></white><newline><gray>Action ID:</gray> <white><id></white><newline><id_warning><newline><gray>Appeal:</gray> <yellow><discord></yellow>";

        @Setting("kick-screen")
        public String kickScreen =
                "<gradient:#f7d74b:#ffd86b><bold><server></bold></gradient><newline><yellow>You were removed from this network.</yellow><newline><gray>Reason:</gray> <white><reason></white><newline><gray>Action ID:</gray> <white><id></white><newline><id_warning><newline><gray>Need help?</gray> <yellow><discord></yellow>";

        @Setting("muted-message")
        public String mutedMessage =
                "<yellow>You are currently muted and cannot chat right now.</yellow><newline><gray>Time left:</gray> <white><time_left></white><newline><gray>Expires at:</gray> <white><expires_at></white><newline><gray>If this seems wrong, visit <white><discord></white>.</gray>";
    }
}
