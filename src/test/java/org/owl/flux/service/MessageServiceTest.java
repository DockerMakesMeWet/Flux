package org.owl.flux.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.owl.flux.config.model.MessagesConfig;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;

class MessageServiceTest {

    @Test
    void sendVersionUsesStaffBrandingPrefix() {
        MessageService service = createService();
        CommandSource source = mock(CommandSource.class);

        service.sendVersion(source, "1.2.3");

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(source).sendMessage(captor.capture());
        String rendered = plain(captor.getValue());
        assertTrue(rendered.startsWith("Flux » "));
        assertTrue(rendered.contains("Flux v1.2.3"));
    }

    @Test
    void sendWarnRemovedNoticeUsesUserBrandingAndWarning() {
        MessageService service = createService();
        CommandSource source = mock(CommandSource.class);

        service.sendWarnRemovedNotice(source, "WR4001");

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(source).sendMessage(captor.capture());
        String rendered = plain(captor.getValue());
        assertTrue(rendered.startsWith("DuckyMC » "));
        assertTrue(rendered.contains("ID: WR4001"));
        assertTrue(rendered.contains("Sharing this ID may hurt your appeal chances."));
    }

    @Test
    void banScreenIncludesTimingForTemporaryBan() {
        MessageService service = createService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        String rendered = plain(service.banScreen("BA1001", "Rule breach", Instant.parse("2026-01-01T02:00:00Z")));

        assertTrue(rendered.contains("DuckyMC"));
        assertTrue(rendered.contains("Expires At: 2026-01-01 02:00:00 UTC"));
        assertTrue(rendered.contains("Time Left: 2h"));
        assertTrue(rendered.contains("Action ID: BA1001"));
        assertTrue(rendered.contains("Sharing this ID may hurt your appeal chances."));
        assertTrue(rendered.contains("dsc.gg/mcducky"));
    }

    @Test
    void banScreenShowsPermanentTimingWithoutExpiry() {
        MessageService service = createService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        String rendered = plain(service.banScreen("BA1002", "Rule breach"));

        assertTrue(rendered.contains("Expires At: Never"));
        assertTrue(rendered.contains("Time Left: Permanent"));
    }

    @Test
    void playerPunishedNoticeIncludesTimingValues() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PermissionService permissionService = mock(PermissionService.class);
        MessageService service = new MessageService(
                server,
                new MessagesConfig(),
                permissionService,
                mock(MastersService.class),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        Player targetPlayer = mock(Player.class);
        when(permissionService.canSeeSilentNotifications(targetPlayer)).thenReturn(false);
        PunishmentRecord record = new PunishmentRecord(
                "MU1234",
                PunishmentType.MUTE,
                "uuid-1",
                "198.51.100.20",
                "TargetUser",
                "executor-uuid",
                "mute reason",
                Instant.parse("2025-12-31T23:00:00Z"),
                Instant.parse("2026-01-01T01:30:00Z"),
                true,
                false,
                false,
                false,
                Map.of()
        );

        service.broadcastStaffAction(record, "ModUser", "TargetUser", targetPlayer);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(targetPlayer).sendMessage(captor.capture());
        String rendered = plain(captor.getValue());
        assertTrue(rendered.contains("You were Mute by ModUser."));
        assertTrue(rendered.contains("Expires At: 2026-01-01 01:30:00 UTC"));
        assertTrue(rendered.contains("Time Left: 1h 30m"));
        assertTrue(rendered.contains("Sharing this ID may hurt your appeal chances."));
    }

    @Test
    void playerPunishedNoticeShowsPermanentTimingValues() {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        PermissionService permissionService = mock(PermissionService.class);
        MessageService service = new MessageService(
                server,
                new MessagesConfig(),
                permissionService,
                mock(MastersService.class),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        Player targetPlayer = mock(Player.class);
        when(permissionService.canSeeSilentNotifications(targetPlayer)).thenReturn(false);
        PunishmentRecord record = new PunishmentRecord(
                "BA9999",
                PunishmentType.BAN,
                "uuid-1",
                "198.51.100.20",
                "TargetUser",
                "executor-uuid",
                "ban reason",
                Instant.parse("2025-12-31T23:00:00Z"),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        );

        service.broadcastStaffAction(record, "ModUser", "TargetUser", targetPlayer);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(targetPlayer).sendMessage(captor.capture());
        String rendered = plain(captor.getValue());
        assertTrue(rendered.contains("Expires At: Never"));
        assertTrue(rendered.contains("Time Left: Permanent"));
    }

    @Test
    void offlineJoinEntriesIncludeIdWarning() {
        MessageService service = createService();
        Player player = mock(Player.class);
        List<PunishmentRecord> punishments = List.of(new PunishmentRecord(
                "JN1001",
                PunishmentType.WARN,
                "uuid-1",
                "198.51.100.20",
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

        service.sendOfflineJoinNotice(player, punishments);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, org.mockito.Mockito.times(2)).sendMessage(captor.capture());
        List<String> rendered = captor.getAllValues().stream().map(MessageServiceTest::plain).toList();
        assertEquals(2, rendered.size());
        assertTrue(rendered.get(0).startsWith("DuckyMC » "));
        assertTrue(rendered.get(1).contains("ID: JN1001"));
        assertTrue(rendered.get(1).contains("Sharing this ID may hurt your appeal chances."));
    }

    @Test
    void mutedMessageUsesLiveRemainingTimeAndExpiryForTemporaryMutes() {
        MessageService service = createService();
        Instant end = Instant.parse("2026-01-03T04:10:30Z");
        PunishmentRecord mute = new PunishmentRecord(
                "MU1001",
                PunishmentType.MUTE,
                "uuid-1",
                "198.51.100.30",
                "TargetUser",
                "executor-uuid",
                "mute reason",
                Instant.parse("2026-01-01T00:00:00Z"),
                end,
                true,
                false,
                false,
                false,
                Map.of()
        );

        String firstRendered = plain(service.mutedMessage(mute, Instant.parse("2026-01-01T00:00:00Z")));
        String secondRendered = plain(service.mutedMessage(mute, Instant.parse("2026-01-02T00:00:00Z")));

        assertTrue(firstRendered.startsWith("DuckyMC » "));
        assertTrue(firstRendered.contains("Time left: 2d 4h 10m 30s"));
        assertTrue(firstRendered.contains("Expires at: 2026-01-03 04:10:30 UTC"));
        assertTrue(secondRendered.contains("Time left: 1d 4h 10m 30s"));
    }

    @Test
    void mutedMessageShowsPermanentLabelsWhenMuteHasNoExpiry() {
        MessageService service = createService();
        PunishmentRecord mute = new PunishmentRecord(
                "MU2001",
                PunishmentType.MUTE,
                "uuid-1",
                "198.51.100.31",
                "TargetUser",
                "executor-uuid",
                "mute reason",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        );

        String rendered = plain(service.mutedMessage(mute, Instant.parse("2026-01-01T01:00:00Z")));

        assertTrue(rendered.contains("Time left: Permanent"));
        assertTrue(rendered.contains("Expires at: Never"));
    }

    private static MessageService createService() {
        return createService(Clock.systemUTC());
    }

    private static MessageService createService(Clock clock) {
        ProxyServer server = mock(ProxyServer.class);
        when(server.getAllPlayers()).thenReturn(List.of());
        return new MessageService(
                server,
                new MessagesConfig(),
                mock(PermissionService.class),
                mock(MastersService.class),
                clock
        );
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
