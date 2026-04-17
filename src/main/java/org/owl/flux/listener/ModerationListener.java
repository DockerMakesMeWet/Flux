package org.owl.flux.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
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

    public ModerationListener(
            PlayerRepository playerRepository,
            PunishmentService punishmentService,
            PermissionService permissionService,
            MastersService mastersService,
            MessageService messageService
    ) {
        this.playerRepository = playerRepository;
        this.punishmentService = punishmentService;
        this.permissionService = permissionService;
        this.mastersService = mastersService;
        this.messageService = messageService;
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
}
