package org.owl.flux.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.DuplicatePunishmentIdException;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.integration.DiscordWebhookService;
import org.owl.flux.service.model.PunishmentRequest;
import org.owl.flux.service.model.PunishmentResult;
import org.owl.flux.util.NetworkUtil;
import org.owl.flux.util.PunishmentTimeFormatter;

public final class PunishmentService {
    private static final String CONSOLE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String IP_PUNISHMENT_METADATA_KEY = "ip_punishment";
    private static final int MAX_SAVE_ID_COLLISION_RETRIES = 8;

    private final ProxyServer server;
    private final PunishmentRepository punishmentRepository;
    private final ActionIdService actionIdService;
    private final MessageService messageService;
    private final DiscordWebhookService discordWebhookService;

    public PunishmentService(
            ProxyServer server,
            PunishmentRepository punishmentRepository,
            ActionIdService actionIdService,
            MessageService messageService,
            DiscordWebhookService discordWebhookService
    ) {
        this.server = server;
        this.punishmentRepository = punishmentRepository;
        this.actionIdService = actionIdService;
        this.messageService = messageService;
        this.discordWebhookService = discordWebhookService;
    }

    public PunishmentResult create(PunishmentRequest request) {
        Instant now = Instant.now();
        Instant end = request.duration() == null ? null : now.plus(request.duration());
        boolean active = request.type() == PunishmentType.BAN || request.type() == PunishmentType.MUTE;
        String targetIp = request.ipOverride() != null ? request.ipOverride() : request.target().ip();
        boolean issuedOffline = request.target().optionalOnlinePlayer().isEmpty();

        Map<String, String> metadata = new HashMap<>();
        if (request.templateName() != null && !request.templateName().isBlank()) {
            metadata.put("template", request.templateName());
        }
        metadata.put(IP_PUNISHMENT_METADATA_KEY, Boolean.toString(request.ipPunishment()));

        PunishmentRecord record = persistWithRetryOnIdCollision(request, now, end, active, targetIp, issuedOffline, metadata);

        int disconnected = applyImmediateEnforcement(record, request.target());
        String actionKey = actionKey(record.type());
        discordWebhookService.sendAction(
            actionKey,
                request.target().username(),
                executorName(request.executor()),
                record.reason(),
                record.id(),
                record.type().name(),
            request.ipPunishment(),
            buildActionContext(request, record, actionKey)
        );
        return new PunishmentResult(record, disconnected);
    }

    private PunishmentRecord persistWithRetryOnIdCollision(
            PunishmentRequest request,
            Instant now,
            Instant end,
            boolean active,
            String targetIp,
            boolean issuedOffline,
            Map<String, String> metadata
    ) {
        DuplicatePunishmentIdException lastCollision = null;
        for (int attempt = 0; attempt < MAX_SAVE_ID_COLLISION_RETRIES; attempt++) {
            PunishmentRecord candidate = new PunishmentRecord(
                    actionIdService.nextUniqueId(),
                    request.type(),
                    request.target().uuid(),
                    targetIp,
                    request.target().username(),
                    executorUuid(request.executor()),
                    request.reason(),
                    now,
                    end,
                    active,
                    false,
                    issuedOffline,
                    false,
                    metadata
            );
            try {
                punishmentRepository.save(candidate);
                return candidate;
            } catch (DuplicatePunishmentIdException collision) {
                lastCollision = collision;
            }
        }
        throw new IllegalStateException("Unable to persist punishment with a unique Flux action ID.", lastCollision);
    }

    public Optional<PunishmentRecord> activeMute(String targetUuid, String targetUsername) {
        return punishmentRepository.findActivePunishment(targetUuid, targetUsername, null, PunishmentType.MUTE);
    }

    public Optional<PunishmentRecord> activeBan(String targetUuid, String targetUsername, String ip) {
        return punishmentRepository.findActivePunishment(targetUuid, targetUsername, ip, PunishmentType.BAN);
    }

    public boolean unbanByTarget(String targetUuid, String targetUsername) {
        return punishmentRepository.deactivateActiveByTarget(targetUuid, targetUsername, PunishmentType.BAN);
    }

    public boolean unbanByIp(String ip) {
        return punishmentRepository.deactivateActiveBanByIp(ip);
    }

    public boolean unbanById(String id) {
        return punishmentRepository.deactivateActiveById(id, PunishmentType.BAN);
    }

    public boolean unmuteByTarget(String targetUuid, String targetUsername) {
        return punishmentRepository.deactivateActiveByTarget(targetUuid, targetUsername, PunishmentType.MUTE);
    }

    public boolean unmuteById(String id) {
        return punishmentRepository.deactivateActiveById(id, PunishmentType.MUTE);
    }

    public boolean voidAction(String id) {
        return punishmentRepository.voidById(id);
    }

    public Optional<PunishmentRecord> findById(String id) {
        return punishmentRepository.findById(id);
    }

    public List<PunishmentRecord> pendingJoinNotices(String targetUuid, String targetUsername) {
        return punishmentRepository.findPendingJoinNotices(targetUuid, targetUsername);
    }

    public int markJoinNoticesDelivered(List<String> punishmentIds) {
        return punishmentRepository.markJoinNoticesDelivered(punishmentIds);
    }

    public List<PunishmentRecord> pendingMuteExpiryNotices(String targetUuid, String targetUsername) {
        return punishmentRepository.findPendingMuteExpiryNotices(targetUuid, targetUsername);
    }

    public int markMuteExpiryNoticesDelivered(List<String> punishmentIds) {
        return punishmentRepository.markMuteExpiryNoticesDelivered(punishmentIds);
    }

    public void deliverPendingJoinNotice(Player player) {
        List<PunishmentRecord> pending = pendingJoinNotices(player.getUniqueId().toString(), player.getUsername());
        if (pending.isEmpty()) {
            return;
        }

        List<String> pendingIds = pending.stream().map(PunishmentRecord::id).toList();
        int updated = markJoinNoticesDelivered(pendingIds);
        if (updated <= 0) {
            return;
        }
        messageService.sendOfflineJoinNotice(player, pending);
    }

    public void processMuteExpiryNotificationsForOnlinePlayers() {
        punishmentRepository.expireEndedPunishments();
        for (Player player : server.getAllPlayers()) {
            deliverPendingMuteExpiryNoticeInternal(player);
        }
    }

    public void deliverPendingMuteExpiryNotice(Player player) {
        punishmentRepository.expireEndedPunishments();
        deliverPendingMuteExpiryNoticeInternal(player);
    }

    public boolean isIpPunishment(PunishmentRecord record) {
        if (record == null || record.metadata() == null) {
            return false;
        }
        return Boolean.parseBoolean(record.metadata().getOrDefault(IP_PUNISHMENT_METADATA_KEY, "false"));
    }

    public void sendUnbanWebhook(String target, CommandSource executor, boolean ipPunishment) {
        Map<String, String> context = new HashMap<>();
        context.put("related_action_id", "N/A");
        context.put("duration", "N/A");
        context.put("template", "N/A");
        context.put("executor_type", executor instanceof Player ? "PLAYER" : "CONSOLE");
        discordWebhookService.sendAction(
                "unban",
                target,
                executorName(executor),
                "Ban lifted",
                "N/A",
                "UNBAN",
            ipPunishment,
            context
        );
    }

    public void sendUnmuteWebhook(String target, CommandSource executor) {
        Map<String, String> context = new HashMap<>();
        context.put("related_action_id", "N/A");
        context.put("duration", "N/A");
        context.put("template", "N/A");
        context.put("executor_type", executor instanceof Player ? "PLAYER" : "CONSOLE");
        discordWebhookService.sendAction(
                "unmute",
                target,
                executorName(executor),
                "Mute lifted",
                "N/A",
                "UNMUTE",
            false,
            context
        );
    }

    public void sendVoidWebhook(String targetActionId, CommandSource executor, boolean ipPunishment) {
        Map<String, String> context = new HashMap<>();
        context.put("related_action_id", targetActionId);
        context.put("duration", "N/A");
        context.put("template", "N/A");
        context.put("executor_type", executor instanceof Player ? "PLAYER" : "CONSOLE");
        discordWebhookService.sendAction(
                "void",
                targetActionId,
                executorName(executor),
                "Action voided",
                targetActionId,
                "VOID",
            ipPunishment,
            context
        );
    }

    public void notifyPlayerUnmuted(PunishmentRecord record) {
        if (record == null || record.type() != PunishmentType.MUTE) {
            return;
        }
        withTargetOnline(record.targetUuid(), messageService::sendUnmutedNotice);
    }

    public void notifyPlayerWarnRemoved(PunishmentRecord record) {
        if (record == null || record.type() != PunishmentType.WARN) {
            return;
        }
        withTargetOnline(record.targetUuid(), player -> messageService.sendWarnRemovedNotice(player, record.id()));
    }

    private int applyImmediateEnforcement(PunishmentRecord record, org.owl.flux.service.model.TargetProfile target) {
        if (record.type() == PunishmentType.KICK) {
            target.optionalOnlinePlayer().ifPresent(player -> player.disconnect(messageService.kickScreen(record.id(), record.reason())));
            return target.optionalOnlinePlayer().isPresent() ? 1 : 0;
        }

        if (record.type() == PunishmentType.BAN) {
            if (target.optionalOnlinePlayer().isPresent()) {
                Player player = target.optionalOnlinePlayer().get();
                player.disconnect(messageService.banScreen(record.id(), record.reason(), record.endTime()));
                return 1;
            }

            int disconnected = 0;
            if (record.targetIp() != null) {
                for (Player player : server.getAllPlayers()) {
                    String ip = NetworkUtil.extractIp(player);
                    if (record.targetIp().equals(ip)) {
                        player.disconnect(messageService.banScreen(record.id(), record.reason(), record.endTime()));
                        disconnected++;
                    }
                }
            }
            return disconnected;
        }

        return 0;
    }

    private static String executorUuid(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUniqueId().toString();
        }
        return CONSOLE_UUID;
    }

    private static String executorName(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername();
        }
        return "Console";
    }

    private static String actionKey(PunishmentType type) {
        return switch (type) {
            case BAN -> "ban";
            case MUTE -> "mute";
            case WARN -> "warn";
            case KICK -> "kick";
        };
    }

    private Map<String, String> buildActionContext(PunishmentRequest request, PunishmentRecord record, String actionKey) {
        Map<String, String> context = new HashMap<>();
        context.put("target_uuid", safeNullable(request.target().uuid()));
        context.put("target_ip", safeNullable(record.targetIp()));
        context.put("template", safeNullable(request.templateName()));
        context.put("duration", PunishmentTimeFormatter.formatRemaining(record.startTime(), record.endTime()));
        context.put("start_time", PunishmentTimeFormatter.formatTimestampHumanUtc(record.startTime()));
        context.put(
            "end_time",
            record.endTime() == null
                ? PunishmentTimeFormatter.NEVER_LABEL
                : PunishmentTimeFormatter.formatTimestampHumanUtc(record.endTime())
        );
        context.put("issued_offline", Boolean.toString(record.issuedOffline()));
        context.put("related_action_id", record.id());
        context.put("executor_type", request.executor() instanceof Player ? "PLAYER" : "CONSOLE");
        context.put("action_label", actionKey.toUpperCase());
        return context;
    }

    private static String safeNullable(String value) {
        return (value == null || value.isBlank()) ? "N/A" : value;
    }

    private void withTargetOnline(String targetUuid, Consumer<Player> callback) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return;
        }
        try {
            UUID uuid = UUID.fromString(targetUuid);
            server.getPlayer(uuid).ifPresent(callback);
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed UUIDs from historical records.
        }
    }

    private void deliverPendingMuteExpiryNoticeInternal(Player player) {
        List<PunishmentRecord> pending = pendingMuteExpiryNotices(player.getUniqueId().toString(), player.getUsername());
        if (pending.isEmpty()) {
            return;
        }
        List<String> pendingIds = pending.stream().map(PunishmentRecord::id).toList();
        int updated = markMuteExpiryNoticesDelivered(pendingIds);
        if (updated <= 0) {
            return;
        }
        messageService.sendUnmutedNotice(player);
    }
}
