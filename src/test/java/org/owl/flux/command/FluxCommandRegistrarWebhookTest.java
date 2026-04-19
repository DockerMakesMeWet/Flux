package org.owl.flux.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.model.ModerationActionType;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.service.TargetResolver;
import org.owl.flux.service.TemplateService;
import org.owl.flux.service.model.TargetProfile;

class FluxCommandRegistrarWebhookTest {

    @Test
    void runUnbanUsesStoredIpPunishmentContext() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"TargetUser", "appeal accepted"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);
        when(targetResolver.resolvePunishmentTarget("TargetUser")).thenReturn(Optional.of(target));

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord activeBan = punishment("BAN001", Map.of("ip_punishment", "true"));
        when(punishmentService.activeBan("uuid-1", "TargetUser", "203.0.113.10")).thenReturn(Optional.of(activeBan));
        when(punishmentService.unbanByTarget("uuid-1", "TargetUser")).thenReturn(true);
        when(punishmentService.isIpPunishment(activeBan)).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnban", invocation);

        verify(punishmentService).sendUnbanWebhook("TargetUser", source, true, "appeal accepted");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNBAN, "TargetUser", "BAN001", source, "appeal accepted");
    }

    @Test
    void runUnbanSupportsNeverSeenUsernameFallback() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"NeverSeen", "record review"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        TargetProfile target = new TargetProfile(null, "NeverSeen", null, null);
        when(targetResolver.resolvePunishmentTarget("NeverSeen")).thenReturn(Optional.of(target));

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord activeBan = punishment("BAN010", Map.of("ip_punishment", "false"));
        when(punishmentService.activeBan(null, "NeverSeen", null)).thenReturn(Optional.of(activeBan));
        when(punishmentService.unbanByTarget(null, "NeverSeen")).thenReturn(true);
        when(punishmentService.isIpPunishment(activeBan)).thenReturn(false);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnban", invocation);

        verify(punishmentService).unbanByTarget(null, "NeverSeen");
        verify(punishmentService).sendUnbanWebhook("NeverSeen", source, false, "record review");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNBAN, "NeverSeen", "BAN010", source, "record review");
    }

    @Test
    void runUnbanSupportsActionIdInputWhenPlayerLookupFails() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"ab1234", "manual correction"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        when(targetResolver.resolvePlayer("ab1234")).thenReturn(Optional.empty());

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord record = punishment("AB1234", PunishmentType.BAN, "TargetUser", Map.of("ip_punishment", "false"));
        when(punishmentService.findById("AB1234")).thenReturn(Optional.of(record));
        when(punishmentService.unbanById("AB1234")).thenReturn(true);
        when(punishmentService.isIpPunishment(record)).thenReturn(false);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnban", invocation);

        verify(punishmentService).unbanById("AB1234");
        verify(punishmentService).sendUnbanWebhook("TargetUser", source, false, "manual correction");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNBAN, "TargetUser", "AB1234", source, "manual correction");
    }

    @Test
    void runUnbanByIdFallsBackToIpWhenRecordedUsernameMissing() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"BA2001", "false positive"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        when(targetResolver.resolvePlayer("BA2001")).thenReturn(Optional.empty());

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord record = new PunishmentRecord(
                "BA2001",
                PunishmentType.BAN,
                null,
                "198.51.100.40",
                null,
                "executor-uuid",
                "reason",
                Instant.now(),
                null,
                true,
                false,
                true,
                false,
                Map.of()
        );
        when(punishmentService.findById("BA2001")).thenReturn(Optional.of(record));
        when(punishmentService.unbanById("BA2001")).thenReturn(true);
        when(punishmentService.isIpPunishment(record)).thenReturn(false);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnban", invocation);

        verify(punishmentService).sendUnbanWebhook("198.51.100.40", source, false, "false positive");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNBAN, "198.51.100.40", "BA2001", source, "false positive");
    }

    @Test
    void runUnmuteDispatchesWebhookOnSuccess() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"TargetUser", "appeal accepted"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);
        when(targetResolver.resolvePunishmentTarget("TargetUser")).thenReturn(Optional.of(target));

        PunishmentService punishmentService = mock(PunishmentService.class);
        when(punishmentService.unmuteByTarget("uuid-1", "TargetUser")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnmute", invocation);

        verify(punishmentService).sendUnmuteWebhook("TargetUser", source, "appeal accepted");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNMUTE, "TargetUser", null, source, "appeal accepted");
    }

    @Test
    void runUnmuteSupportsNeverSeenUsernameFallback() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"NeverSeen", "manual lift"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        TargetProfile target = new TargetProfile(null, "NeverSeen", null, null);
        when(targetResolver.resolvePunishmentTarget("NeverSeen")).thenReturn(Optional.of(target));

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord activeMute = punishment("MU9011", PunishmentType.MUTE, "NeverSeen", Map.of());
        when(punishmentService.activeMute(null, "NeverSeen")).thenReturn(Optional.of(activeMute));
        when(punishmentService.unmuteByTarget(null, "NeverSeen")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnmute", invocation);

        verify(punishmentService).unmuteByTarget(null, "NeverSeen");
        verify(punishmentService).sendUnmuteWebhook("NeverSeen", source, "manual lift");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNMUTE, "NeverSeen", "MU9011", source, "manual lift");
        verify(punishmentService).notifyPlayerUnmuted(activeMute);
    }

    @Test
    void runUnmuteSupportsActionIdInputAndNotifiesPlayer() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"MU1234", "appeal accepted"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        when(targetResolver.resolvePlayer("MU1234")).thenReturn(Optional.empty());

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord record = punishment("MU1234", PunishmentType.MUTE, "TargetUser", Map.of());
        when(punishmentService.findById("MU1234")).thenReturn(Optional.of(record));
        when(punishmentService.unmuteById("MU1234")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnmute", invocation);

        verify(punishmentService).sendUnmuteWebhook("TargetUser", source, "appeal accepted");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNMUTE, "TargetUser", "MU1234", source, "appeal accepted");
        verify(punishmentService).notifyPlayerUnmuted(record);
    }

    @Test
    void runUnmuteByIdFallsBackToUuidWhenUsernameAndIpMissing() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"MU2201", "expired already"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        when(targetResolver.resolvePlayer("MU2201")).thenReturn(Optional.empty());

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord record = new PunishmentRecord(
                "MU2201",
                PunishmentType.MUTE,
                "00000000-0000-0000-0000-000000000220",
                null,
                null,
                "executor-uuid",
                "reason",
                Instant.now(),
                null,
                true,
                false,
                true,
                false,
                Map.of()
        );
        when(punishmentService.findById("MU2201")).thenReturn(Optional.of(record));
        when(punishmentService.unmuteById("MU2201")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService);
        invoke(registrar, "runUnmute", invocation);

        verify(punishmentService).sendUnmuteWebhook("00000000-0000-0000-0000-000000000220", source, "expired already");
        verify(punishmentService).auditReversalAction(ModerationActionType.UNMUTE, "00000000-0000-0000-0000-000000000220", "MU2201", source, "expired already");
        verify(punishmentService).notifyPlayerUnmuted(record);
    }

    @Test
    void runVoidUsesStoredIpPunishmentContext() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"abc123", "invalid action"});

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord record = punishment("ABC123", Map.of("ip_punishment", "true"));
        when(punishmentService.findById("ABC123")).thenReturn(Optional.of(record));
        when(punishmentService.voidAction("ABC123", "invalid action")).thenReturn(true);
        when(punishmentService.isIpPunishment(record)).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), punishmentService);
        invoke(registrar, "runVoid", invocation);

        verify(punishmentService).voidAction("ABC123", "invalid action");
        verify(punishmentService).sendVoidWebhook("ABC123", source, true, "invalid action", record);
        verify(punishmentService).auditReversalAction(ModerationActionType.VOID, "uuid-1", "ABC123", source, "invalid action");
    }

    @Test
    void runVoidAllVoidsEveryActiveAccountPunishment() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"TargetUser", "bulk", "cleanup"});

        TargetResolver targetResolver = mock(TargetResolver.class);
        when(targetResolver.resolvePunishmentTarget("TargetUser"))
                .thenReturn(Optional.of(new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null)));

        PunishmentRecord first = punishment("BA1001", PunishmentType.BAN, "TargetUser", Map.of("ip_punishment", "false"));
        PunishmentRecord second = punishment("MU1001", PunishmentType.MUTE, "TargetUser", Map.of("ip_punishment", "false"));
        PunishmentRecord warn = punishment("WR1001", PunishmentType.WARN, "TargetUser", Map.of("ip_punishment", "false"));
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        when(punishmentRepository.voidAllCandidatesByTarget("uuid-1", "TargetUser"))
            .thenReturn(List.of(first, second, warn));

        PunishmentService punishmentService = mock(PunishmentService.class);
        when(punishmentService.voidAction("BA1001", "bulk cleanup")).thenReturn(true);
        when(punishmentService.voidAction("MU1001", "bulk cleanup")).thenReturn(true);
        when(punishmentService.voidAction("WR1001", "bulk cleanup")).thenReturn(true);
        when(punishmentService.isIpPunishment(first)).thenReturn(false);
        when(punishmentService.isIpPunishment(second)).thenReturn(false);
        when(punishmentService.isIpPunishment(warn)).thenReturn(false);

        FluxCommandRegistrar registrar = registrar(targetResolver, punishmentService, punishmentRepository);
        invoke(registrar, "runVoidAll", invocation);

        verify(punishmentRepository).voidAllCandidatesByTarget("uuid-1", "TargetUser");
        verify(punishmentService).voidAction("BA1001", "bulk cleanup");
        verify(punishmentService).voidAction("MU1001", "bulk cleanup");
        verify(punishmentService).voidAction("WR1001", "bulk cleanup");
        verify(punishmentService).auditReversalAction(ModerationActionType.VOID, "TargetUser", "BA1001", source, "bulk cleanup");
        verify(punishmentService).auditReversalAction(ModerationActionType.VOID, "TargetUser", "MU1001", source, "bulk cleanup");
        verify(punishmentService).auditReversalAction(ModerationActionType.VOID, "TargetUser", "WR1001", source, "bulk cleanup");
        verify(punishmentService).sendVoidWebhook("BA1001", source, false, "bulk cleanup", first);
        verify(punishmentService).sendVoidWebhook("MU1001", source, false, "bulk cleanup", second);
        verify(punishmentService).sendVoidWebhook("WR1001", source, false, "bulk cleanup", warn);
        verify(punishmentService, times(3)).notifyPlayerWarnRemoved(org.mockito.ArgumentMatchers.any(PunishmentRecord.class));
    }

    @Test
    void runVoidNotifiesWarnTargetWhenWarnIsVoided() throws Exception {
        CommandSource source = mock(CommandSource.class);
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(new String[]{"wa1234", "legacy warning"});

        PunishmentService punishmentService = mock(PunishmentService.class);
        PunishmentRecord record = punishment("WA1234", PunishmentType.WARN, "TargetUser", Map.of());
        when(punishmentService.findById("WA1234")).thenReturn(Optional.of(record));
        when(punishmentService.voidAction("WA1234", "legacy warning")).thenReturn(true);
        when(punishmentService.isIpPunishment(record)).thenReturn(false);

        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), punishmentService);
        invoke(registrar, "runVoid", invocation);

        verify(punishmentService).voidAction("WA1234", "legacy warning");
        verify(punishmentService).notifyPlayerWarnRemoved(record);
    }

    @Test
    void suggestVoidUsesPunishmentIdPrefixLookup() throws Exception {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"ab"});

        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        when(punishmentRepository.findRecentIdsByPrefix("ab", 25)).thenReturn(List.of("AB1000", "AB0999"));

        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), mock(PunishmentService.class), punishmentRepository);
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) invokeWithResult(registrar, "suggestVoid", invocation);

        assertEquals(List.of("AB1000", "AB0999"), suggestions);
        verify(punishmentRepository).findRecentIdsByPrefix("ab", 25);
    }

    @Test
    void suggestCheckUsesPunishmentIdPrefixLookup() throws Exception {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"ab"});

        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        when(punishmentRepository.findRecentIdsByPrefix("ab", 25)).thenReturn(List.of("AB1000", "AB0999"));

        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), mock(PunishmentService.class), punishmentRepository);
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) invokeWithResult(registrar, "suggestCheck", invocation);

        assertEquals(List.of("AB1000", "AB0999"), suggestions);
        verify(punishmentRepository).findRecentIdsByPrefix("ab", 25);
    }

    @Test
    void suggestVoidUsesEmptyPrefixWhenCurrentTokenIsMissing() throws Exception {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{});

        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        when(punishmentRepository.findRecentIdsByPrefix("", 25)).thenReturn(List.of("AB1000"));

        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), mock(PunishmentService.class), punishmentRepository);
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) invokeWithResult(registrar, "suggestVoid", invocation);

        assertEquals(List.of("AB1000"), suggestions);
        verify(punishmentRepository).findRecentIdsByPrefix("", 25);
    }

    @Test
    void suggestVoidTrimsPartialPrefixBeforeLookup() throws Exception {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"  ab1  "});

        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        when(punishmentRepository.findRecentIdsByPrefix("ab1", 25)).thenReturn(List.of("AB1000"));

        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), mock(PunishmentService.class), punishmentRepository);
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) invokeWithResult(registrar, "suggestVoid", invocation);

        assertEquals(List.of("AB1000"), suggestions);
        verify(punishmentRepository).findRecentIdsByPrefix("ab1", 25);
    }

    @Test
    void suggestVoidReturnsEmptyForExtraArguments() throws Exception {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"AB1000", "extra"});

        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(mock(TargetResolver.class), mock(PunishmentService.class), punishmentRepository);
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) invokeWithResult(registrar, "suggestVoid", invocation);

        assertEquals(List.of(), suggestions);
        verifyNoInteractions(punishmentRepository);
    }

    private static FluxCommandRegistrar registrar(TargetResolver targetResolver, PunishmentService punishmentService) {
        return registrar(targetResolver, punishmentService, mock(PunishmentRepository.class));
    }

    private static FluxCommandRegistrar registrar(
            TargetResolver targetResolver,
            PunishmentService punishmentService,
            PunishmentRepository punishmentRepository
    ) {
        return new FluxCommandRegistrar(
                new Object(),
                mock(ProxyServer.class),
                mock(MessageService.class),
                mock(PermissionService.class),
                null,
                targetResolver,
                mock(TemplateService.class),
                punishmentService,
                punishmentRepository,
                mock(PlayerRepository.class),
                mock(MastersService.class),
                () -> true,
                "1.0.0-test"
        );
    }

    private static void invoke(FluxCommandRegistrar registrar, String methodName, SimpleCommand.Invocation invocation)
            throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod(methodName, SimpleCommand.Invocation.class);
        method.setAccessible(true);
        method.invoke(registrar, invocation);
    }

    private static Object invokeWithResult(FluxCommandRegistrar registrar, String methodName, SimpleCommand.Invocation invocation)
            throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod(methodName, SimpleCommand.Invocation.class);
        method.setAccessible(true);
        return method.invoke(registrar, invocation);
    }

    private static PunishmentRecord punishment(String id, Map<String, String> metadata) {
        return punishment(id, PunishmentType.BAN, null, metadata);
    }

    private static PunishmentRecord punishment(String id, PunishmentType type, String targetUsername, Map<String, String> metadata) {
        return new PunishmentRecord(
                id,
                type,
                "uuid-1",
                "203.0.113.10",
                targetUsername,
                "executor-uuid",
                "reason",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                metadata
        );
    }
}
