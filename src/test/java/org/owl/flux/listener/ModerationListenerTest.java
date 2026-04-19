package org.owl.flux.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.owl.flux.config.model.MutedCommandsConfig;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;

class ModerationListenerTest {

    @Test
    void onChatDeniedForActiveMuteAndRefreshesMessageEachAttempt() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        PermissionService permissionService = mock(PermissionService.class);
        MastersService mastersService = mock(MastersService.class);
        MessageService messageService = mock(MessageService.class);
        ModerationListener listener = new ModerationListener(
                playerRepository,
                punishmentService,
                permissionService,
                mastersService,
                messageService,
                new MutedCommandsConfig()
        );

        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000777");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("MutedUser");
        when(mastersService.isMaster("MutedUser")).thenReturn(false);
        when(permissionService.has(player, "flux.bypass.mute")).thenReturn(false);

        PlayerChatEvent event = mock(PlayerChatEvent.class);
        PlayerChatEvent.ChatResult allowed = mock(PlayerChatEvent.ChatResult.class);
        when(allowed.isAllowed()).thenReturn(true);
        when(event.getResult()).thenReturn(allowed);
        when(event.getPlayer()).thenReturn(player);

        PunishmentRecord mute = new PunishmentRecord(
                "MU7001",
                PunishmentType.MUTE,
                uuid.toString(),
                "198.51.100.99",
                "MutedUser",
                "executor-uuid",
                "mute reason",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(600),
                true,
                false,
                false,
                false,
                Map.of()
        );
        when(punishmentService.activeMute(uuid.toString(), "MutedUser")).thenReturn(Optional.of(mute), Optional.of(mute));

        Component mutedMessage = Component.text("muted");
        when(messageService.mutedMessage(mute)).thenReturn(mutedMessage);

        listener.onChat(event);
        listener.onChat(event);

        verify(punishmentService, times(2)).activeMute(uuid.toString(), "MutedUser");
        verify(messageService, times(2)).mutedMessage(mute);
        verify(event, times(2)).setResult(any(PlayerChatEvent.ChatResult.class));
        verify(player, times(2)).sendMessage(mutedMessage);
    }

    @Test
    void onChatStaysAllowedWhenNoActiveMute() {
        PunishmentService punishmentService = mock(PunishmentService.class);
        PermissionService permissionService = mock(PermissionService.class);
        MastersService mastersService = mock(MastersService.class);
        MessageService messageService = mock(MessageService.class);
        ModerationListener listener = new ModerationListener(
                mock(PlayerRepository.class),
                punishmentService,
                permissionService,
                mastersService,
                messageService,
                new MutedCommandsConfig()
        );

        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000778");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("RegularUser");
        when(mastersService.isMaster("RegularUser")).thenReturn(false);
        when(permissionService.has(player, "flux.bypass.mute")).thenReturn(false);
        when(punishmentService.activeMute(uuid.toString(), "RegularUser")).thenReturn(Optional.empty());

        PlayerChatEvent event = mock(PlayerChatEvent.class);
        PlayerChatEvent.ChatResult allowed = mock(PlayerChatEvent.ChatResult.class);
        when(allowed.isAllowed()).thenReturn(true);
        when(event.getResult()).thenReturn(allowed);
        when(event.getPlayer()).thenReturn(player);

        listener.onChat(event);

        verify(event, never()).setResult(any(PlayerChatEvent.ChatResult.class));
        verify(messageService, never()).mutedMessage(any(PunishmentRecord.class));
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void onCommandExecuteBlocksConfiguredMutedCommand() {
        PunishmentService punishmentService = mock(PunishmentService.class);
        PermissionService permissionService = mock(PermissionService.class);
        MastersService mastersService = mock(MastersService.class);
        MessageService messageService = mock(MessageService.class);
        ModerationListener listener = new ModerationListener(
                mock(PlayerRepository.class),
                punishmentService,
                permissionService,
                mastersService,
                messageService,
                new MutedCommandsConfig()
        );

        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000779");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("MutedUser");
        when(mastersService.isMaster("MutedUser")).thenReturn(false);
        when(permissionService.has(player, "flux.bypass.mute")).thenReturn(false);

        PunishmentRecord mute = new PunishmentRecord(
                "MU7002",
                PunishmentType.MUTE,
                uuid.toString(),
                "198.51.100.100",
                "MutedUser",
                "executor-uuid",
                "mute reason",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(600),
                true,
                false,
                false,
                false,
                Map.of()
        );
        when(punishmentService.activeMute(uuid.toString(), "MutedUser")).thenReturn(Optional.of(mute));

        Component mutedMessage = Component.text("muted");
        when(messageService.mutedMessage(mute)).thenReturn(mutedMessage);

        CommandExecuteEvent event = mock(CommandExecuteEvent.class);
        when(event.getResult()).thenReturn(CommandExecuteEvent.CommandResult.allowed());
        when(event.getCommandSource()).thenReturn(player);
        when(event.getCommand()).thenReturn("/msg Staff hello");

        listener.onCommandExecute(event);

        verify(event).setResult(argThat(result -> !result.isAllowed()));
        verify(player).sendMessage(mutedMessage);
    }

        @Test
        void onChatSkipsMuteCheckForMaster() {
                PunishmentService punishmentService = mock(PunishmentService.class);
                PermissionService permissionService = mock(PermissionService.class);
                MastersService mastersService = mock(MastersService.class);
                MessageService messageService = mock(MessageService.class);
                ModerationListener listener = new ModerationListener(
                                mock(PlayerRepository.class),
                                punishmentService,
                                permissionService,
                                mastersService,
                                messageService,
                                new MutedCommandsConfig()
                );

                Player player = mock(Player.class);
                when(player.getUsername()).thenReturn("MasterUser");
                when(mastersService.isMaster("MasterUser")).thenReturn(true);

                PlayerChatEvent event = mock(PlayerChatEvent.class);
                PlayerChatEvent.ChatResult allowed = mock(PlayerChatEvent.ChatResult.class);
                when(allowed.isAllowed()).thenReturn(true);
                when(event.getResult()).thenReturn(allowed);
                when(event.getPlayer()).thenReturn(player);

                listener.onChat(event);

                verify(punishmentService, never()).activeMute(any(), any());
                verify(event, never()).setResult(any(PlayerChatEvent.ChatResult.class));
                verify(messageService, never()).mutedMessage(any(PunishmentRecord.class));
        }

        @Test
        void onCommandExecuteSkipsMuteCheckForMaster() {
                PunishmentService punishmentService = mock(PunishmentService.class);
                PermissionService permissionService = mock(PermissionService.class);
                MastersService mastersService = mock(MastersService.class);
                MessageService messageService = mock(MessageService.class);
                ModerationListener listener = new ModerationListener(
                                mock(PlayerRepository.class),
                                punishmentService,
                                permissionService,
                                mastersService,
                                messageService,
                                new MutedCommandsConfig()
                );

                Player player = mock(Player.class);
                when(player.getUsername()).thenReturn("MasterUser");
                when(mastersService.isMaster("MasterUser")).thenReturn(true);

                CommandExecuteEvent event = mock(CommandExecuteEvent.class);
                when(event.getResult()).thenReturn(CommandExecuteEvent.CommandResult.allowed());
                when(event.getCommandSource()).thenReturn(player);
                when(event.getCommand()).thenReturn("/msg Staff hello");

                listener.onCommandExecute(event);

                verify(punishmentService, never()).activeMute(any(), any());
                verify(event, never()).setResult(any(CommandExecuteEvent.CommandResult.class));
                verify(messageService, never()).mutedMessage(any(PunishmentRecord.class));
                verify(player, never()).sendMessage(any(Component.class));
        }

    @Test
    void onCommandExecuteAllowsUnlistedCommandForMutedPlayer() {
        PunishmentService punishmentService = mock(PunishmentService.class);
        PermissionService permissionService = mock(PermissionService.class);
        MastersService mastersService = mock(MastersService.class);
        MessageService messageService = mock(MessageService.class);
        ModerationListener listener = new ModerationListener(
                mock(PlayerRepository.class),
                punishmentService,
                permissionService,
                mastersService,
                messageService,
                new MutedCommandsConfig()
        );

        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000780");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("MutedUser");
        when(mastersService.isMaster("MutedUser")).thenReturn(false);
        when(permissionService.has(player, "flux.bypass.mute")).thenReturn(false);
        when(punishmentService.activeMute(uuid.toString(), "MutedUser")).thenReturn(Optional.of(mock(PunishmentRecord.class)));

        CommandExecuteEvent event = mock(CommandExecuteEvent.class);
        when(event.getResult()).thenReturn(CommandExecuteEvent.CommandResult.allowed());
        when(event.getCommandSource()).thenReturn(player);
        when(event.getCommand()).thenReturn("/spawn");

        listener.onCommandExecute(event);

        verify(event, never()).setResult(any(CommandExecuteEvent.CommandResult.class));
        verify(messageService, never()).mutedMessage(any(PunishmentRecord.class));
    }

        @Test
        void onLoginSkipsBanCheckForMaster() {
                PunishmentService punishmentService = mock(PunishmentService.class);
                PermissionService permissionService = mock(PermissionService.class);
                MastersService mastersService = mock(MastersService.class);
                MessageService messageService = mock(MessageService.class);
                ModerationListener listener = new ModerationListener(
                                mock(PlayerRepository.class),
                                punishmentService,
                                permissionService,
                                mastersService,
                                messageService,
                                new MutedCommandsConfig()
                );

                Player player = mock(Player.class);
                UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000781");
                when(player.getUniqueId()).thenReturn(uuid);
                when(player.getUsername()).thenReturn("MasterUser");
                when(player.getRemoteAddress()).thenReturn(new InetSocketAddress("198.51.100.111", 25565));
                when(mastersService.isMaster("MasterUser")).thenReturn(true);

                LoginEvent event = mock(LoginEvent.class);
                when(event.getPlayer()).thenReturn(player);

                listener.onLogin(event);

                verify(punishmentService, never()).activeBan(any(), any(), any());
                verify(event, never()).setResult(any());
                verify(messageService, never()).banScreen(any(), any(), any());
        }
}
