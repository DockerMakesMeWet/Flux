package org.owl.flux.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.owl.flux.config.model.MessagesConfig;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.util.PunishmentTimeFormatter;

public final class MessageService {
    private final ProxyServer server;
    private volatile MessagesConfig messages;
    private final PermissionService permissionService;
    private final MastersService mastersService;
    private final Clock clock;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageService(
            ProxyServer server,
            MessagesConfig messages,
            PermissionService permissionService,
            MastersService mastersService
    ) {
        this(server, messages, permissionService, mastersService, Clock.systemUTC());
    }

    MessageService(
            ProxyServer server,
            MessagesConfig messages,
            PermissionService permissionService,
            MastersService mastersService,
            Clock clock
    ) {
        this.server = server;
        this.messages = messages == null ? new MessagesConfig() : messages;
        this.permissionService = permissionService;
        this.mastersService = mastersService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void updateMessages(MessagesConfig messages) {
        this.messages = messages == null ? new MessagesConfig() : messages;
    }

    public void send(CommandSource source, String template, Map<String, String> placeholders) {
        source.sendMessage(renderWithPrefix(template, placeholders));
    }

    public void sendUser(CommandSource source, String template, Map<String, String> placeholders) {
        source.sendMessage(renderWithUserPrefix(template, placeholders));
    }

    public void sendNoPermission(CommandSource source) {
        source.sendMessage(renderWithPrefix(messages.errors.noPermission, Map.of()));
    }

    public void sendPlayerNotFound(CommandSource source, String player) {
        source.sendMessage(renderWithPrefix(messages.errors.playerNotFound, Map.of("player", safeText(player))));
    }

    public void sendTargetProtected(CommandSource source) {
        source.sendMessage(renderWithPrefix(messages.errors.targetProtected, Map.of()));
    }

    public void sendRawError(CommandSource source, String message) {
        source.sendMessage(renderWithPrefix("<red>" + safeText(message) + "</red>", Map.of()));
    }

    public void sendUsage(CommandSource source, String usage) {
        send(source, messages.commands.usageFormat, Map.of("usage", safeText(usage)));
    }

    public String usageBan() {
        return safeText(messages.commands.usageBan);
    }

    public String usageMute() {
        return safeText(messages.commands.usageMute);
    }

    public String usageWarn() {
        return safeText(messages.commands.usageWarn);
    }

    public String usageKick() {
        return safeText(messages.commands.usageKick);
    }

    public String usageIpBan() {
        return safeText(messages.commands.usageIpBan);
    }

    public String usageIpMute() {
        return safeText(messages.commands.usageIpMute);
    }

    public String usageUnban() {
        return safeText(messages.commands.usageUnban);
    }

    public String usageUnmute() {
        return safeText(messages.commands.usageUnmute);
    }

    public String usageVoid() {
        return safeText(messages.commands.usageVoid);
    }

    public String usageVoidAll() {
        return safeText(messages.commands.usageVoidAll);
    }

    public String usageHistory() {
        return safeText(messages.commands.usageHistory);
    }

    public String usageAlts() {
        return safeText(messages.commands.usageAlts);
    }

    public String usageIpHistory() {
        return safeText(messages.commands.usageIpHistory);
    }

    public String usageCheck() {
        return safeText(messages.commands.usageCheck);
    }

    public String usageCheckPlayer() {
        return safeText(messages.commands.usageCheckPlayer);
    }

    public String usageCheckIp() {
        return safeText(messages.commands.usageCheckIp);
    }

    public String usageCheckWarns() {
        return safeText(messages.commands.usageCheckWarns);
    }

    public String usageFlux() {
        return safeText(messages.commands.usageFlux);
    }

    public void sendInvalidDuration(CommandSource source) {
        send(source, messages.commands.invalidDuration, Map.of());
    }

    public void sendTemplateNotFound(CommandSource source) {
        send(source, messages.commands.templateNotFound, Map.of());
    }

    public void sendTemplateTypeMismatch(CommandSource source, String commandType, String templateType) {
        send(source, messages.commands.templateTypeMismatch, Map.of(
                "command_type", safeText(commandType),
                "template_type", safeText(templateType)
        ));
    }

    public void sendInvalidPage(CommandSource source, String page) {
        send(source, messages.commands.invalidPage, Map.of("page", safeText(page)));
    }

    public void sendActionCreated(CommandSource source, String type, String target, String id) {
        send(source, messages.commands.actionCreated, Map.of(
                "type", safeText(type),
                "target", safeText(target),
                "id", safeText(id)
        ));
    }

    public void sendPunishmentSummaryHeader(CommandSource source, String id) {
        send(source, messages.commands.punishmentSummaryHeader, Map.of("id", safeText(id)));
    }

    public void sendPunishmentSummaryFooter(CommandSource source, String id) {
        send(source, messages.commands.punishmentSummaryFooter, Map.of("id", safeText(id)));
    }

    public void sendActionUpdated(CommandSource source, String target) {
        send(source, messages.commands.actionUpdated, Map.of("target", safeText(target)));
    }

    public void sendVoidUpdated(CommandSource source, String id) {
        send(source, messages.commands.voidUpdated, Map.of("id", safeText(id)));
    }

    public void sendVoidAllUpdated(CommandSource source, String target, int count) {
        send(source, messages.commands.voidAllUpdated, Map.of(
                "target", safeText(target),
                "count", Integer.toString(Math.max(0, count))
        ));
    }

    public void sendActionNotFound(CommandSource source) {
        send(source, messages.commands.actionNotFound, Map.of());
    }

    public void sendCheckHeader(CommandSource source, String id) {
        send(source, messages.commands.checkHeader, Map.of("id", safeText(id)));
    }

    public void sendCheckFooter(CommandSource source, String id) {
        send(source, messages.commands.checkFooter, Map.of("id", safeText(id)));
    }

    public void sendCheckDetailType(CommandSource source, String type) {
        send(source, messages.commands.checkDetailType, Map.of("type", safeText(type)));
    }

    public void sendCheckDetailTarget(CommandSource source, String target, String ip) {
        send(source, messages.commands.checkDetailTarget, Map.of(
                "target", safeText(target),
                "ip", safeText(ip)
        ));
    }

    public void sendCheckDetailIssuer(CommandSource source, String issuer) {
        send(source, messages.commands.checkDetailIssuer, Map.of("issuer", safeText(issuer)));
    }

    public void sendCheckDetailReason(CommandSource source, String reason) {
        send(source, messages.commands.checkDetailReason, Map.of("reason", safeText(reason)));
    }

    public void sendCheckDetailStarted(CommandSource source, String started) {
        send(source, messages.commands.checkDetailStarted, Map.of("started", safeText(started)));
    }

    public void sendCheckDetailDuration(CommandSource source, String duration) {
        send(source, messages.commands.checkDetailDuration, Map.of("duration", safeText(duration)));
    }

    public void sendCheckDetailExpires(CommandSource source, String expires) {
        send(source, messages.commands.checkDetailExpires, Map.of("expires", safeText(expires)));
    }

    public void sendCheckDetailStatus(CommandSource source, String active, String voided, String voidReason) {
        send(source, messages.commands.checkDetailStatus, Map.of(
                "active", safeText(active),
                "voided", safeText(voided),
                "void_reason", safeText(voidReason)
        ));
    }

    public void sendCheckDetailMeta(CommandSource source, String ipPunishment, String template, String templateStep) {
        send(source, messages.commands.checkDetailMeta, Map.of(
                "ip_punishment", safeText(ipPunishment),
                "template", safeText(template),
                "template_step", safeText(templateStep)
        ));
    }

    public void sendCheckPlayerHeader(CommandSource source, String target) {
        send(source, messages.commands.checkPlayerHeader, Map.of("target", safeText(target)));
    }

    public void sendCheckPlayerFooter(CommandSource source, String target, int count) {
        send(source, messages.commands.checkPlayerFooter, Map.of(
                "target", safeText(target),
                "count", Integer.toString(Math.max(0, count))
        ));
    }

    public void sendCheckIpHeader(CommandSource source, String target) {
        send(source, messages.commands.checkIpHeader, Map.of("target", safeText(target)));
    }

    public void sendCheckWarnsHeader(CommandSource source, String target) {
        send(source, messages.commands.checkWarnsHeader, Map.of("target", safeText(target)));
    }

    public void sendCheckIpFooter(CommandSource source, String target, int count) {
        send(source, messages.commands.checkIpFooter, Map.of(
                "target", safeText(target),
                "count", Integer.toString(Math.max(0, count))
        ));
    }

    public void sendCheckSummaryEntry(
            CommandSource source,
            String id,
            String type,
            String reason
    ) {
        send(source, messages.commands.checkSummaryEntry, Map.of(
                "id", safeText(id),
                "type", safeText(type),
            "reason", safeText(reason)
        ));
    }

    public void sendCheckSummaryEntryWithTarget(
            CommandSource source,
            String id,
            String type,
            String reason,
            String target
    ) {
        send(source, messages.commands.checkSummaryEntryWithTarget, Map.of(
                "id", safeText(id),
                "type", safeText(type),
                "reason", safeText(reason),
                "target", safeText(target)
        ));
    }

    public void sendHistoryHeader(CommandSource source, String target) {
        send(source, messages.commands.historyHeader, Map.of("target", safeText(target)));
    }

    public void sendHistoryFooter(CommandSource source, String target, int count) {
        send(source, messages.commands.historyFooter, Map.of(
                "target", safeText(target),
                "count", Integer.toString(Math.max(0, count))
        ));
    }

    public void sendHistoryEntry(
            CommandSource source,
            String id,
            String type,
            String reason
    ) {
        Map<String, String> placeholders = Map.of(
                "id", safeText(id),
                "type", safeText(type),
            "reason", safeText(reason)
        );
        Component component = renderWithPrefix(messages.commands.historyEntry, placeholders)
                .hoverEvent(HoverEvent.showText(component(messages.commands.historyEntryHover, placeholders)))
                .clickEvent(ClickEvent.suggestCommand("/check " + safeText(id)));
        source.sendMessage(component);
    }

    public void sendAltsHeader(CommandSource source, String ip) {
        send(source, messages.commands.altsHeader, Map.of("ip", safeText(ip)));
    }

    public void sendAltsEntry(CommandSource source, String username, String status) {
        String template = switch (status) {
            case "banned" -> messages.commands.altsEntryBanned;
            case "muted"  -> messages.commands.altsEntryMuted;
            default       -> messages.commands.altsEntry;
        };
        send(source, template, Map.of("username", safeText(username)));
    }

    public void sendAltsLegend(CommandSource source) {
        send(source, messages.commands.altsLegend, Map.of());
    }

    public void sendIpHistoryHeader(CommandSource source, String target) {
        send(source, messages.commands.ipHistoryHeader, Map.of("target", safeText(target)));
    }

    public void sendIpHistoryFooter(CommandSource source, String target, int count) {
        send(source, messages.commands.ipHistoryFooter, Map.of(
                "target", safeText(target),
                "count", Integer.toString(Math.max(0, count))
        ));
    }

    public void sendIpHistoryEntry(CommandSource source, String ip, String seen) {
        send(source, messages.commands.ipHistoryEntry, Map.of("ip", safeText(ip), "seen", safeText(seen)));
    }

    public void sendIpHistoryAccountEntry(CommandSource source, String account, String seen) {
        send(source, messages.commands.ipHistoryAccountEntry, Map.of(
                "account", safeText(account),
                "seen", safeText(seen)
        ));
    }

    public void sendPaginationFooter(CommandSource source, int page, int totalPages, String commandBase) {
        int normalizedPage = Math.max(1, page);
        int normalizedTotalPages = Math.max(1, totalPages);
        Map<String, String> placeholders = Map.of(
                "page", Integer.toString(normalizedPage),
                "pages", Integer.toString(normalizedTotalPages)
        );

        Component prefix = component(messages.prefix, Map.of());
        Component previous = component(
                normalizedPage > 1 ? messages.commands.paginationPrev : messages.commands.paginationPrevDisabled,
                placeholders
        );
        if (normalizedPage > 1 && commandBase != null && !commandBase.isBlank()) {
            previous = previous.clickEvent(ClickEvent.runCommand(commandBase + " " + (normalizedPage - 1)));
        }

        Component center = component(messages.commands.paginationViewing, placeholders);

        Component next = component(
                normalizedPage < normalizedTotalPages ? messages.commands.paginationNext : messages.commands.paginationNextDisabled,
                placeholders
        );
        if (normalizedPage < normalizedTotalPages && commandBase != null && !commandBase.isBlank()) {
            next = next.clickEvent(ClickEvent.runCommand(commandBase + " " + (normalizedPage + 1)));
        }

        source.sendMessage(prefix
                .append(previous)
                .append(Component.space())
                .append(center)
                .append(Component.space())
                .append(next));
    }

    public void sendUnmutedNotice(CommandSource source) {
        sendUser(source, messages.player.unmutedNotice, Map.of());
    }

    public void sendWarnRemovedNotice(CommandSource source, String id) {
        sendUser(source, messages.player.warnRemovedNotice, withIdWarning(Map.of("id", safeText(id))));
    }

    public void sendReloadResult(CommandSource source, boolean mastersRefreshed) {
        if (mastersRefreshed) {
            send(source, messages.commands.reloadSuccess, Map.of());
        } else {
            send(source, messages.commands.reloadPartial, Map.of());
        }
    }

    public void sendVersion(CommandSource source, String version) {
        send(source, messages.commands.version, Map.of("version", safeText(version)));
    }

    public void sendOfflineJoinNotice(Player player, List<PunishmentRecord> punishments) {
        if (punishments == null || punishments.isEmpty()) {
            return;
        }
        String countLabel = punishments.size() == 1 ? "punishment" : "punishments";
        String verb = punishments.size() == 1 ? "was" : "were";
        player.sendMessage(renderWithUserPrefix(
                messages.player.offlineJoinHeader,
                Map.of(
                        "count", Integer.toString(punishments.size()),
                        "label", countLabel,
                        "verb", verb
                )
        ));
        for (PunishmentRecord punishment : punishments) {
            player.sendMessage(renderWithUserPrefix(
                    messages.player.offlineJoinEntry,
                    withIdWarning(Map.of(
                            "type", friendlyPunishmentType(punishment),
                            "id", safeText(punishment.id()),
                            "reason", safeText(punishment.reason())
                    ))
            ));
        }
    }

    public void broadcastStaffAction(PunishmentRecord record, String executor, String target, Player targetPlayer) {
        Map<String, String> placeholders = new HashMap<>(punishmentTimingPlaceholders(record.endTime()));
        placeholders.put("id", safeText(record.id()));
        placeholders.put("executor", safeText(executor));
        placeholders.put("target", safeText(target));
        placeholders.put("type", safeText(record.type().name()));
        placeholders.put("reason", safeText(record.reason()));
        Component component = renderWithPrefix(messages.staff.actionBroadcast, placeholders)
                .clickEvent(ClickEvent.suggestCommand("/check " + safeText(record.id())))
                .hoverEvent(HoverEvent.showText(component(messages.staff.actionBroadcastHover, placeholders)));

        for (Player player : server.getAllPlayers()) {
            if (permissionService.canSeeSilentNotifications(player)) {
                player.sendMessage(component);
            }
        }
        CommandSource console = server.getConsoleCommandSource();
        if (console != null) {
            console.sendMessage(component(messages.staff.actionBroadcastConsole, placeholders));
        }
        if (targetPlayer != null && !permissionService.canSeeSilentNotifications(targetPlayer)) {
            Map<String, String> playerPlaceholders = new HashMap<>(placeholders);
            playerPlaceholders.put("type", friendlyPunishmentType(record));
            targetPlayer.sendMessage(renderWithUserPrefix(messages.player.punishedNotice, withIdWarning(playerPlaceholders)));
        }
    }

    public void broadcastVoid(String voidActionId, String targetActionId, String executor, String reason) {
        Map<String, String> placeholders = Map.of(
                "id", safeText(voidActionId),
                "target_id", safeText(targetActionId),
                "executor", safeText(executor),
                "reason", safeText(reason)
        );
        Component component = renderWithPrefix(messages.staff.voidBroadcast, placeholders)
                .clickEvent(ClickEvent.suggestCommand("/check " + safeText(targetActionId)))
                .hoverEvent(HoverEvent.showText(component(messages.staff.voidBroadcastHover, placeholders)));
        for (Player player : server.getAllPlayers()) {
            if (permissionService.canSeeSilentNotifications(player)) {
                player.sendMessage(component);
            }
        }
        CommandSource console = server.getConsoleCommandSource();
        if (console != null) {
            console.sendMessage(component(messages.staff.voidBroadcastConsole, placeholders));
        }
    }

    public Component banScreen(String id, String reason) {
        return banScreen(id, reason, null);
    }

    public Component banScreen(String id, String reason, Instant endTime) {
        Map<String, String> placeholders = new HashMap<>(punishmentTimingPlaceholders(endTime));
        placeholders.put("id", safeText(id));
        placeholders.put("reason", safeText(reason));
        return userComponent(messages.screens.banScreen, withIdWarning(placeholders));
    }

    public Component kickScreen(String id, String reason) {
        return userComponent(messages.screens.kickScreen, withIdWarning(Map.of("id", safeText(id), "reason", safeText(reason))));
    }

    public Component mutedMessage() {
        return mutedMessage(null);
    }

    public Component mutedMessage(PunishmentRecord mute) {
        return mutedMessage(mute, Instant.now(clock));
    }

    Component mutedMessage(PunishmentRecord mute, Instant now) {
        Instant endTime = mute == null ? null : mute.endTime();
        return renderWithUserPrefix(messages.screens.mutedMessage, Map.of(
                "id", safeText(mute == null ? null : mute.id()),
                "time_left", safeText(PunishmentTimeFormatter.formatRemaining(now, endTime)),
                "expires_at", safeText(PunishmentTimeFormatter.formatExpiry(endTime))
        ));
    }

    public Component component(String template, Map<String, String> placeholders) {
        return miniMessage.deserialize(template, resolvers(withBranding(placeholders, false)));
    }

    public Component userComponent(String template, Map<String, String> placeholders) {
        return miniMessage.deserialize(template, resolvers(withBranding(placeholders, true)));
    }

    private Component renderWithPrefix(String template, Map<String, String> placeholders) {
        Component prefix = component(messages.prefix, Map.of());
        return prefix.append(component(template, placeholders));
    }

    private Component renderWithUserPrefix(String template, Map<String, String> placeholders) {
        Component prefix = userComponent(messages.userPrefix, Map.of());
        return prefix.append(userComponent(template, placeholders));
    }

    private static TagResolver[] resolvers(Map<String, String> placeholders) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvers.add(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        return resolvers.toArray(new TagResolver[0]);
    }

    private Map<String, String> withBranding(Map<String, String> placeholders, boolean userFacing) {
        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        String serverName = userFacing
                ? (messages.userBranding == null ? "DuckyMC" : safeText(messages.userBranding.serverName))
                : (messages.branding == null ? "Flux" : safeText(messages.branding.serverName));
        String discordInvite = userFacing
                ? (messages.userBranding == null ? "dsc.gg/mcducky" : safeText(messages.userBranding.discord))
            : (messages.branding == null ? "dsc.gg/mcducky" : safeText(messages.branding.discord));
        merged.putIfAbsent("server", serverName);
        merged.putIfAbsent("discord", discordInvite);
        return merged;
    }

    private Map<String, String> withIdWarning(Map<String, String> placeholders) {
        Map<String, String> merged = new HashMap<>();
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        String warning = "";
        if (messages.player != null && messages.player.idShareWarning != null) {
            warning = messages.player.idShareWarning;
        }
        merged.putIfAbsent("id_warning", warning);
        return merged;
    }

    private Map<String, String> punishmentTimingPlaceholders(Instant endTime) {
        return Map.of(
                "time_left", safeText(PunishmentTimeFormatter.formatRemaining(Instant.now(clock), endTime)),
                "expires_at", safeText(PunishmentTimeFormatter.formatExpiry(endTime))
        );
    }

    private static String friendlyPunishmentType(PunishmentRecord punishment) {
        String value = punishment.type().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value;
    }

}
