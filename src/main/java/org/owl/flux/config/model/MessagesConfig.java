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
                public String discord = "dsc.gg/mcducky";
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
                "<gold><bold>STAFF</bold></gold> <gray>•</gray> <white><executor></white> <gray>issued</gray> <yellow><type></yellow> <gray>to</gray> <white><target></white> <dark_gray>[<id>]</dark_gray>";

        @Setting("action-broadcast-hover")
        public String actionBroadcastHover =
                "<yellow><bold>Moderation Action</bold></yellow><newline><gray>ID:</gray> <white><id></white><newline><gray>Type:</gray> <white><type></white><newline><gray>Target:</gray> <white><target></white><newline><gray>Executor:</gray> <white><executor></white><newline><gray>Reason:</gray> <white><reason></white><newline><gold>Click to suggest /check <id></gold>";

        @Setting("action-broadcast-console")
        public String actionBroadcastConsole =
                "[Flux Moderation] <type> <target> by <executor> [<id>] reason=<reason>";

        @Setting("void-broadcast")
        public String voidBroadcast =
                "<gold><bold>STAFF</bold></gold> <gray>•</gray> <white><executor></white> <gray>voided</gray> <yellow><target_id></yellow> <dark_gray>[<id>]</dark_gray>";

        @Setting("void-broadcast-hover")
        public String voidBroadcastHover =
                "<yellow><bold>Void Action</bold></yellow><newline><gray>Void Action ID:</gray> <white><id></white><newline><gray>Target Action ID:</gray> <white><target_id></white><newline><gray>Executor:</gray> <white><executor></white><newline><gray>Reason:</gray> <white><reason></white><newline><gold>Click to suggest /check <target_id></gold>";

        @Setting("void-broadcast-console")
        public String voidBroadcastConsole =
                "[Flux Moderation] VOID <target_id> by <executor> [<id>] reason=<reason>";
    }

    @ConfigSerializable
    public static final class CommandMessages {
        @Setting("usage-format")
        public String usageFormat = "<red>Usage: <white><usage></white></red>";

        @Setting("usage-ban")
        public String usageBan = "/ban <user> [duration] <reason/#template>";

        @Setting("usage-mute")
        public String usageMute = "/mute <user> [duration] <reason/#template>";

        @Setting("usage-warn")
        public String usageWarn = "/warn <user> <reason/#template>";

        @Setting("usage-kick")
        public String usageKick = "/kick <user> <reason/#template>";

        @Setting("usage-ipban")
        public String usageIpBan = "/ipban <user/ip> [duration] <reason/#template>";

        @Setting("usage-unban")
        public String usageUnban = "/unban <user/ip/id> <reason>";

        @Setting("usage-unmute")
        public String usageUnmute = "/unmute <user/id> <reason>";

        @Setting("usage-void")
        public String usageVoid = "/void <id> <reason>";

        @Setting("usage-history")
        public String usageHistory = "/history <user> [page]";

        @Setting("usage-alts")
        public String usageAlts = "/alts <user> [page]";

        @Setting("usage-iphistory")
        public String usageIpHistory = "/iphistory <ip/user> [page]";

        @Setting("usage-check")
        public String usageCheck = "/check <id>";

        @Setting("usage-checkplayer")
        public String usageCheckPlayer = "/checkplayer <user> [page]";

        @Setting("usage-checkip")
        public String usageCheckIp = "/checkip <ip/user> [page]";

        @Setting("usage-flux")
        public String usageFlux = "/flux [reload|ver|info|version]";

        @Setting("invalid-duration")
        public String invalidDuration = "<red>Invalid duration format.</red>";

        @Setting("template-not-found")
        public String templateNotFound = "<red>Template not found.</red>";

        @Setting("invalid-page")
        public String invalidPage = "<red>Invalid page <white><page></white>. Use a positive number.</red>";

        @Setting("action-created")
        public String actionCreated = "<yellow>Action logged: <white><type></white> on <white><target></white> <gray>(ID: <id>)</gray></yellow>";

        @Setting("punishment-summary-header")
        public String punishmentSummaryHeader =
                "<gray>===</gray> <yellow>Punishment summary for <white><id></white></yellow> <gray>===</gray>";

        @Setting("punishment-summary-footer")
        public String punishmentSummaryFooter =
                "<gray>===</gray> <yellow>End punishment summary for <white><id></white></yellow> <gray>===</gray>";

        @Setting("action-updated")
        public String actionUpdated = "<yellow>Updated punishment state for <white><target></white>.</yellow>";

        @Setting("void-updated")
        public String voidUpdated = "<yellow>Voided punishment/action <white><id></white>.</yellow>";

        @Setting("action-not-found")
        public String actionNotFound = "<red>No matching punishment was found.</red>";

        @Setting("check-header")
        public String checkHeader =
                "<gray>===</gray> <yellow>Checking action <white><id></white></yellow> <gray>===</gray>";

        @Setting("check-footer")
        public String checkFooter =
                "<gray>===</gray> <yellow>End check for <white><id></white></yellow> <gray>===</gray>";

        @Setting("check-detail-type")
        public String checkDetailType = "<gray>Type:</gray> <white><type></white>";

        @Setting("check-detail-target")
        public String checkDetailTarget = "<gray>Target:</gray> <white><target></white> <dark_gray>(ip=<ip>)</dark_gray>";

        @Setting("check-detail-issuer")
        public String checkDetailIssuer = "<gray>Issuer:</gray> <white><issuer></white>";

        @Setting("check-detail-reason")
        public String checkDetailReason = "<gray>Reason:</gray> <white><reason></white>";

        @Setting("check-detail-started")
        public String checkDetailStarted = "<gray>Started:</gray> <white><started></white>";

        @Setting("check-detail-duration")
        public String checkDetailDuration = "<gray>Duration:</gray> <white><duration></white>";

        @Setting("check-detail-expires")
        public String checkDetailExpires = "<gray>Expires:</gray> <white><expires></white>";

        @Setting("check-detail-status")
        public String checkDetailStatus = "<gray>Active:</gray> <white><active></white> <gray>| Voided:</gray> <white><voided></white> <gray>| Void Reason:</gray> <white><void_reason></white>";

        @Setting("check-detail-meta")
        public String checkDetailMeta = "<gray>IP Punishment:</gray> <white><ip_punishment></white> <gray>| Template:</gray> <white><template></white>";

        @Setting("check-summary-entry")
        public String checkSummaryEntry = "<gray>-</gray> <white><id></white> <yellow><type></yellow> <gray><reason></gray> <dark_gray>(expires=<expires><void_note>)</dark_gray>";

        @Setting("check-summary-entry-with-target")
        public String checkSummaryEntryWithTarget = "<gray>-</gray> <white><id></white> <yellow><type></yellow> <gray><reason></gray> <dark_gray>(target=<target>, expires=<expires><void_note>)</dark_gray>";

        @Setting("checkplayer-header")
        public String checkPlayerHeader =
                "<gray>===</gray> <yellow>Checking active punishments for <white><target></white></yellow> <gray>===</gray>";

        @Setting("checkplayer-footer")
        public String checkPlayerFooter =
                "<gray>===</gray> <yellow>Found <white><count></white> active entries for <white><target></white></yellow> <gray>===</gray>";

        @Setting("checkip-header")
        public String checkIpHeader =
                "<gray>===</gray> <yellow>Checking active punishments for IP <white><target></white></yellow> <gray>===</gray>";

        @Setting("checkip-footer")
        public String checkIpFooter =
                "<gray>===</gray> <yellow>Found <white><count></white> active entries for IP <white><target></white></yellow> <gray>===</gray>";

        @Setting("history-header")
        public String historyHeader =
                "<gray>===</gray> <yellow>History of <white><target></white></yellow> <gray>===</gray>";

        @Setting("history-footer")
        public String historyFooter =
                "<gray>===</gray> <yellow>End history of <white><target></white> (<count>)</yellow> <gray>===</gray>";

        @Setting("history-entry")
        public String historyEntry = "<gray>•</gray> <white><id></white> <yellow><type></yellow> <gray>-</gray> <white><reason></white> <dark_gray>(voided=<voided><void_note>)</dark_gray>";

        @Setting("history-entry-hover")
        public String historyEntryHover =
                "<yellow><bold>History Entry</bold></yellow><newline><gray>ID:</gray> <white><id></white><newline><gray>Type:</gray> <white><type></white><newline><gray>Reason:</gray> <white><reason></white><newline><gray>Voided:</gray> <white><voided></white><newline><gray>Void Reason:</gray> <white><void_reason></white><newline><gold>Click to suggest /check <id></gold>";

        @Setting("alts-header")
        public String altsHeader = "<yellow>Accounts sharing IP <white><ip></white>:</yellow>";

        @Setting("alts-entry")
        public String altsEntry = "<gray>•</gray> <white><username></white>";

        @Setting("iphistory-account-entry")
        public String ipHistoryAccountEntry = "<gray>-</gray> <white><account></white> <dark_gray>(<seen>)</dark_gray>";

        @Setting("iphistory-header")
        public String ipHistoryHeader =
                "<gray>===</gray> <yellow>IP history for <white><target></white></yellow> <gray>===</gray>";

        @Setting("iphistory-footer")
        public String ipHistoryFooter =
                "<gray>===</gray> <yellow>End IP history of <white><target></white> (<count>)</yellow> <gray>===</gray>";

        @Setting("iphistory-entry")
        public String ipHistoryEntry = "<gray>•</gray> <white><ip></white> <dark_gray>(<seen>)</dark_gray>";

        @Setting("pagination-prev")
        public String paginationPrev = "<yellow><bold><<</bold></yellow>";

        @Setting("pagination-prev-disabled")
        public String paginationPrevDisabled = "<dark_gray><bold><<</bold></dark_gray>";

        @Setting("pagination-viewing")
        public String paginationViewing = "<yellow>Viewing page <white><page></white> out of <white><pages></white></yellow>";

        @Setting("pagination-next")
        public String paginationNext = "<yellow><bold>>></bold></yellow>";

        @Setting("pagination-next-disabled")
        public String paginationNextDisabled = "<dark_gray><bold>>></bold></dark_gray>";

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
