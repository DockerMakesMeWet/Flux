package org.owl.flux.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.owl.flux.config.model.MutedCommandsConfig;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.util.NetworkUtil;

public final class ModerationListener {
    private final PlayerRepository playerRepository;
    private final PunishmentService punishmentService;
    private final PermissionService permissionService;
    private final MastersService mastersService;
    private final MessageService messageService;
    private volatile Set<String> blockedMutedCommandRoots;

    public ModerationListener(
            PlayerRepository playerRepository,
            PunishmentService punishmentService,
            PermissionService permissionService,
            MastersService mastersService,
            MessageService messageService,
            MutedCommandsConfig mutedCommandsConfig
    ) {
        this.playerRepository = playerRepository;
        this.punishmentService = punishmentService;
        this.permissionService = permissionService;
        this.mastersService = mastersService;
        this.messageService = messageService;
        this.blockedMutedCommandRoots = blockedCommandRoots(mutedCommandsConfig);
    }

    public void updateMutedCommands(MutedCommandsConfig mutedCommandsConfig) {
        this.blockedMutedCommandRoots = blockedCommandRoots(mutedCommandsConfig);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }

        Player player = event.getPlayer();
        if (mastersService.isMaster(player.getUsername()) || permissionService.has(player, "flux.bypass.mute")) {
            return;
        }

        punishmentService.activeMute(player.getUniqueId().toString(), player.getUsername()).ifPresent(mute -> {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(messageService.mutedMessage(mute));
        });
    }

    @Subscribe(order = PostOrder.LAST)
    public void onCommandExecute(CommandExecuteEvent event) {
        // Flux does not bypass denied command events from auth plugins.
        if (!event.getResult().isAllowed()) {
            return;
        }

        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        if (mastersService.isMaster(player.getUsername()) || permissionService.has(player, "flux.bypass.mute")) {
            return;
        }

        Set<String> blockedRoots = this.blockedMutedCommandRoots;
        if (blockedRoots == null || blockedRoots.isEmpty()) {
            return;
        }

        String root = commandRoot(event.getCommand());
        if (root.isEmpty() || !blockedRoots.contains(root)) {
            return;
        }

        punishmentService.activeMute(player.getUniqueId().toString(), player.getUsername()).ifPresent(mute -> {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(messageService.mutedMessage(mute));
        });
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (mastersService.isMaster(player.getUsername()) || permissionService.has(player, "flux.bypass.ban")) {
            return;
        }

        String ip = NetworkUtil.extractIp(player);
        punishmentService.activeBan(player.getUniqueId().toString(), player.getUsername(), ip).ifPresent(ban ->
                event.setResult(com.velocitypowered.api.event.ResultedEvent.ComponentResult.denied(
                        messageService.banScreen(ban.id(), ban.reason(), ban.endTime())
                ))
        );
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        playerRepository.upsertPlayer(
                player.getUniqueId().toString(),
                player.getUsername(),
                NetworkUtil.extractIp(player)
        );
        if (event.getPreviousServer().isEmpty()) {
            punishmentService.deliverPendingJoinNotice(player);
            punishmentService.deliverPendingMuteExpiryNotice(player);
        }
    }

    private static Set<String> blockedCommandRoots(MutedCommandsConfig config) {
        if (config == null) {
            return Set.of();
        }

        Set<String> roots = new LinkedHashSet<>();
        collectEnabledCommands(roots, config.vanilla);
        collectEnabledCommands(roots, config.messageCommands);
        collectEnabledCommands(roots, config.essentialsxMessageCommands);
        return Set.copyOf(roots);
    }

    private static void collectEnabledCommands(Set<String> output, Map<String, MutedCommandsConfig.CommandRule> group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        for (Map.Entry<String, MutedCommandsConfig.CommandRule> entry : group.entrySet()) {
            MutedCommandsConfig.CommandRule rule = entry.getValue();
            if (rule == null || !rule.enabled) {
                continue;
            }

            String root = commandRoot(entry.getKey());
            if (!root.isEmpty()) {
                output.add(root);
            }
        }
    }

    private static String commandRoot(String commandInput) {
        if (commandInput == null) {
            return "";
        }

        String normalized = commandInput.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return "";
        }

        int spaceIndex = normalized.indexOf(' ');
        String root = spaceIndex >= 0 ? normalized.substring(0, spaceIndex) : normalized;

        int namespaceIndex = root.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < root.length() - 1) {
            root = root.substring(namespaceIndex + 1);
        }

        return root.trim().toLowerCase(Locale.ROOT);
    }
}
