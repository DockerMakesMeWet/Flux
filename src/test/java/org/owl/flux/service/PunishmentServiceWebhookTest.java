package org.owl.flux.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.DuplicatePunishmentIdException;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.integration.DiscordWebhookService;
import org.owl.flux.service.model.PunishmentRequest;
import org.owl.flux.service.model.TargetProfile;

class PunishmentServiceWebhookTest {

    @Test
    void createStoresIpPunishmentMetadataAndDispatchesBanWebhook() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        ActionIdService actionIdService = mock(ActionIdService.class);
        when(actionIdService.nextUniqueId()).thenReturn("ABC123");
        MessageService messageService = mock(MessageService.class);
        DiscordWebhookService discordWebhookService = mock(DiscordWebhookService.class);
        CommandSource executor = mock(CommandSource.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);

        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                actionIdService,
                messageService,
                discordWebhookService
        );

        service.create(new PunishmentRequest(
                executor,
                target,
                PunishmentType.BAN,
                null,
                "Rule violation",
                null,
                "203.0.113.10",
                true
        ));

        ArgumentCaptor<PunishmentRecord> captor = ArgumentCaptor.forClass(PunishmentRecord.class);
        verify(punishmentRepository).save(captor.capture());
        assertEquals("true", captor.getValue().metadata().get("ip_punishment"));
        assertEquals("TargetUser", captor.getValue().targetUsername());
        assertTrue(captor.getValue().issuedOffline());
        verify(discordWebhookService).sendAction(
                "ban",
                "TargetUser",
                "Console",
                "Rule violation",
                "ABC123",
                "BAN",
                true
        );
    }

    @Test
    void createTimedBanUsesPersistedEndTimeForBanScreen() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        ActionIdService actionIdService = mock(ActionIdService.class);
        when(actionIdService.nextUniqueId()).thenReturn("ABC123");
        MessageService messageService = mock(MessageService.class);
        Component banComponent = Component.text("ban-screen");
        when(messageService.banScreen(eq("ABC123"), eq("Rule violation"), any(Instant.class))).thenReturn(banComponent);
        DiscordWebhookService discordWebhookService = mock(DiscordWebhookService.class);
        CommandSource executor = mock(CommandSource.class);
        Player onlineTarget = mock(Player.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", onlineTarget);

        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                actionIdService,
                messageService,
                discordWebhookService
        );

        service.create(new PunishmentRequest(
                executor,
                target,
                PunishmentType.BAN,
                Duration.ofHours(2),
                "Rule violation",
                null,
                null,
                false
        ));

        ArgumentCaptor<PunishmentRecord> captor = ArgumentCaptor.forClass(PunishmentRecord.class);
        verify(punishmentRepository).save(captor.capture());
        Instant persistedEndTime = captor.getValue().endTime();
        assertTrue(persistedEndTime != null);
        verify(messageService).banScreen("ABC123", "Rule violation", persistedEndTime);
        verify(onlineTarget).disconnect(banComponent);
    }

    @Test
    void isIpPunishmentReturnsFalseWhenMetadataMissing() {
        PunishmentService service = new PunishmentService(
                mock(ProxyServer.class),
                mock(PunishmentRepository.class),
                mock(ActionIdService.class),
                mock(MessageService.class),
                mock(DiscordWebhookService.class)
        );
        PunishmentRecord record = new PunishmentRecord(
                "ABC123",
                PunishmentType.BAN,
                "uuid-1",
                "203.0.113.10",
                "executor-uuid",
                "reason",
                Instant.now(),
                null,
                true,
                false,
                Map.of()
        );

        assertFalse(service.isIpPunishment(record));
    }

    @Test
    void createRetriesWithNewActionIdAfterDuplicateCollision() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        doThrow(new DuplicatePunishmentIdException("duplicate", null))
                .doNothing()
                .when(punishmentRepository)
                .save(any(PunishmentRecord.class));
        ActionIdService actionIdService = mock(ActionIdService.class);
        when(actionIdService.nextUniqueId()).thenReturn("ABC123", "DEF456");
        MessageService messageService = mock(MessageService.class);
        DiscordWebhookService discordWebhookService = mock(DiscordWebhookService.class);
        CommandSource executor = mock(CommandSource.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);

        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                actionIdService,
                messageService,
                discordWebhookService
        );

        service.create(new PunishmentRequest(
                executor,
                target,
                PunishmentType.BAN,
                null,
                "Rule violation",
                null,
                null,
                false
        ));

        ArgumentCaptor<PunishmentRecord> captor = ArgumentCaptor.forClass(PunishmentRecord.class);
        verify(punishmentRepository, times(2)).save(captor.capture());
        assertEquals(List.of("ABC123", "DEF456"), captor.getAllValues().stream().map(PunishmentRecord::id).toList());
        verify(discordWebhookService).sendAction(
                "ban",
                "TargetUser",
                "Console",
                "Rule violation",
                "DEF456",
                "BAN",
                false
        );
    }

    @Test
    void createFailsAfterExhaustingDuplicateCollisionRetries() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        doThrow(new DuplicatePunishmentIdException("duplicate", null))
                .when(punishmentRepository)
                .save(any(PunishmentRecord.class));
        ActionIdService actionIdService = mock(ActionIdService.class);
        when(actionIdService.nextUniqueId()).thenReturn(
                "AA0001",
                "AA0002",
                "AA0003",
                "AA0004",
                "AA0005",
                "AA0006",
                "AA0007",
                "AA0008"
        );

        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                actionIdService,
                mock(MessageService.class),
                mock(DiscordWebhookService.class)
        );

        assertThrows(IllegalStateException.class, () -> service.create(new PunishmentRequest(
                mock(CommandSource.class),
                new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null),
                PunishmentType.BAN,
                null,
                "Rule violation",
                null,
                null,
                false
        )));
        verify(punishmentRepository, times(8)).save(any(PunishmentRecord.class));
    }

    @Test
    void createPersistsNeverSeenOfflineTargetIdentityFields() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        ActionIdService actionIdService = mock(ActionIdService.class);
        when(actionIdService.nextUniqueId()).thenReturn("ABC123");

        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                actionIdService,
                mock(MessageService.class),
                mock(DiscordWebhookService.class)
        );

        service.create(new PunishmentRequest(
                mock(CommandSource.class),
                new TargetProfile(null, "NeverSeen", null, null),
                PunishmentType.WARN,
                null,
                "Never joined before",
                null,
                null,
                false
        ));

        ArgumentCaptor<PunishmentRecord> captor = ArgumentCaptor.forClass(PunishmentRecord.class);
        verify(punishmentRepository).save(captor.capture());
        assertNull(captor.getValue().targetUuid());
        assertEquals("NeverSeen", captor.getValue().targetUsername());
        assertTrue(captor.getValue().issuedOffline());
        assertFalse(captor.getValue().joinNoticeDelivered());
    }

    @Test
    void deliverPendingJoinNoticeConsolidatesAndMarksSingleDelivery() {
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                mock(ProxyServer.class),
                punishmentRepository,
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000101");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("TargetUser");
        List<PunishmentRecord> pending = List.of(
                new PunishmentRecord(
                        "JN1001",
                        PunishmentType.WARN,
                        uuid.toString(),
                        "198.51.100.60",
                        "TargetUser",
                        "executor-uuid",
                        "warn reason",
                        Instant.now().minusSeconds(20),
                        null,
                        false,
                        false,
                        true,
                        false,
                        Map.of()
                ),
                new PunishmentRecord(
                        "JN1002",
                        PunishmentType.MUTE,
                        uuid.toString(),
                        "198.51.100.60",
                        "TargetUser",
                        "executor-uuid",
                        "mute reason",
                        Instant.now().minusSeconds(10),
                        null,
                        true,
                        false,
                        true,
                        false,
                        Map.of()
                )
        );
        when(punishmentRepository.findPendingJoinNotices(uuid.toString(), "TargetUser")).thenReturn(pending);
        when(punishmentRepository.markJoinNoticesDelivered(List.of("JN1001", "JN1002"))).thenReturn(2);

        service.deliverPendingJoinNotice(player);

        verify(punishmentRepository).markJoinNoticesDelivered(List.of("JN1001", "JN1002"));
        verify(messageService).sendOfflineJoinNotice(player, pending);
    }

    @Test
    void deliverPendingJoinNoticeDoesNotSendWhenRowsWereNotMarked() {
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                mock(ProxyServer.class),
                punishmentRepository,
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000202");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("TargetUser");
        List<PunishmentRecord> pending = List.of(new PunishmentRecord(
                "JN2001",
                PunishmentType.WARN,
                uuid.toString(),
                "198.51.100.70",
                "TargetUser",
                "executor-uuid",
                "warn reason",
                Instant.now(),
                null,
                false,
                false,
                true,
                false,
                Map.of()
        ));
        when(punishmentRepository.findPendingJoinNotices(uuid.toString(), "TargetUser")).thenReturn(pending);
        when(punishmentRepository.markJoinNoticesDelivered(List.of("JN2001"))).thenReturn(0);

        service.deliverPendingJoinNotice(player);

        verify(messageService, never()).sendOfflineJoinNotice(player, pending);
    }

    @Test
    void deliverPendingJoinNoticeSendsOnlyOnFirstSuccessfulDelivery() {
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                mock(ProxyServer.class),
                punishmentRepository,
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000212");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("TargetUser");
        List<PunishmentRecord> pending = List.of(new PunishmentRecord(
                "JN2010",
                PunishmentType.WARN,
                uuid.toString(),
                "198.51.100.71",
                "TargetUser",
                "executor-uuid",
                "warn reason",
                Instant.now(),
                null,
                false,
                false,
                true,
                false,
                Map.of()
        ));
        when(punishmentRepository.findPendingJoinNotices(uuid.toString(), "TargetUser"))
                .thenReturn(pending)
                .thenReturn(List.of());
        when(punishmentRepository.markJoinNoticesDelivered(List.of("JN2010"))).thenReturn(1);

        service.deliverPendingJoinNotice(player);
        service.deliverPendingJoinNotice(player);

        verify(punishmentRepository, times(1)).markJoinNoticesDelivered(List.of("JN2010"));
        verify(messageService, times(1)).sendOfflineJoinNotice(player, pending);
    }

    @Test
    void processMuteExpiryNotificationsForOnlinePlayersDeliversImmediateNotice() {
        ProxyServer server = mock(ProxyServer.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000515");
        when(server.getAllPlayers()).thenReturn(List.of(player));
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("TargetUser");
        List<PunishmentRecord> pending = List.of(new PunishmentRecord(
                "MU5151",
                PunishmentType.MUTE,
                uuid.toString(),
                "198.51.100.81",
                "TargetUser",
                "executor-uuid",
                "mute reason",
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(1),
                false,
                false,
                false,
                false,
                Map.of()
        ));
        when(punishmentRepository.findPendingMuteExpiryNotices(uuid.toString(), "TargetUser")).thenReturn(pending);
        when(punishmentRepository.markMuteExpiryNoticesDelivered(List.of("MU5151"))).thenReturn(1);

        service.processMuteExpiryNotificationsForOnlinePlayers();

        verify(punishmentRepository).expireEndedPunishments();
        verify(punishmentRepository).markMuteExpiryNoticesDelivered(List.of("MU5151"));
        verify(messageService).sendUnmutedNotice(player);
    }

    @Test
    void deliverPendingMuteExpiryNoticeSendsOnlyOnNextJoinForOfflineExpiry() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                server,
                punishmentRepository,
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000616");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn("TargetUser");
        List<PunishmentRecord> pending = List.of(new PunishmentRecord(
                "MU6161",
                PunishmentType.MUTE,
                uuid.toString(),
                "198.51.100.82",
                "TargetUser",
                "executor-uuid",
                "mute reason",
                Instant.now().minusSeconds(140),
                Instant.now().minusSeconds(5),
                false,
                false,
                false,
                false,
                Map.of()
        ));
        when(punishmentRepository.findPendingMuteExpiryNotices(uuid.toString(), "TargetUser"))
                .thenReturn(pending)
                .thenReturn(pending);
        when(punishmentRepository.markMuteExpiryNoticesDelivered(List.of("MU6161")))
                .thenReturn(1)
                .thenReturn(0);

        service.processMuteExpiryNotificationsForOnlinePlayers();
        verify(messageService, never()).sendUnmutedNotice(any());

        service.deliverPendingMuteExpiryNotice(player);
        service.deliverPendingMuteExpiryNotice(player);

        verify(messageService, times(1)).sendUnmutedNotice(player);
    }

    @Test
    void notifyPlayerUnmutedSendsNoticeToOnlineTarget() {
        ProxyServer server = mock(ProxyServer.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                server,
                mock(PunishmentRepository.class),
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000303");
        when(server.getPlayer(uuid)).thenReturn(Optional.of(player));
        PunishmentRecord record = new PunishmentRecord(
                "MU3001",
                PunishmentType.MUTE,
                uuid.toString(),
                "203.0.113.10",
                "TargetUser",
                "executor-uuid",
                "mute reason",
                Instant.now(),
                null,
                false,
                false,
                false,
                false,
                Map.of()
        );

        service.notifyPlayerUnmuted(record);

        verify(messageService).sendUnmutedNotice(player);
    }

    @Test
    void notifyPlayerWarnRemovedSendsNoticeToOnlineTarget() {
        ProxyServer server = mock(ProxyServer.class);
        MessageService messageService = mock(MessageService.class);
        PunishmentService service = new PunishmentService(
                server,
                mock(PunishmentRepository.class),
                mock(ActionIdService.class),
                messageService,
                mock(DiscordWebhookService.class)
        );
        Player player = mock(Player.class);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000404");
        when(server.getPlayer(uuid)).thenReturn(Optional.of(player));
        PunishmentRecord record = new PunishmentRecord(
                "WR4001",
                PunishmentType.WARN,
                uuid.toString(),
                "203.0.113.10",
                "TargetUser",
                "executor-uuid",
                "warn reason",
                Instant.now(),
                null,
                false,
                true,
                false,
                false,
                Map.of()
        );

        service.notifyPlayerWarnRemoved(record);

        verify(messageService).sendWarnRemovedNotice(player, "WR4001");
    }
}
