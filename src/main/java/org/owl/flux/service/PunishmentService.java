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
        discordWebhookService.sendAction(
                actionKey(record.type()),
                request.target().username(),
                executorName(request.executor()),
                record.reason(),
                record.id(),
                record.type().name(),
                request.ipPunishment()
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
        discordWebhookService.sendAction(
                "unban",
                target,
                executorName(executor),
                "Ban lifted",
                "N/A",
                "UNBAN",
                ipPunishment
        );
    }

    public void sendUnmuteWebhook(String target, CommandSource executor) {
        discordWebhookService.sendAction(
                "unmute",
                target,
                executorName(executor),
                "Mute lifted",
                "N/A",
                "UNMUTE",
                false
        );
    }

    public void sendVoidWebhook(String targetActionId, CommandSource executor, boolean ipPunishment) {
        discordWebhookService.sendAction(
                "void",
                targetActionId,
                executorName(executor),
                "Action voided",
                targetActionId,
                "VOID",
                ipPunishment
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
