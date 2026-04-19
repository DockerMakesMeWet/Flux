package org.owl.flux.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.owl.flux.data.model.ModerationActionRecord;
import org.owl.flux.data.model.ModerationActionType;
import org.owl.flux.data.model.PlayerSnapshot;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.service.TargetResolver;
import org.owl.flux.service.TemplateService;
import org.owl.flux.service.model.TargetProfile;

class FluxCommandRegistrarCheckCommandTest {

    @Test
    void runCheckShowsUsageForMissingId() throws Exception {
        MessageService messageService = mock(MessageService.class);
        FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), mock(PunishmentService.class),
                mock(PunishmentRepository.class), mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);
        when(messageService.usageCheck()).thenReturn("/check <id>");

        invoke(registrar, "runCheck", invocation(source));

        verify(messageService).sendUsage(source, "/check <id>");
    }

    @Test
    void runCheckShowsPunishmentDetails() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), punishmentService,
                mock(PunishmentRepository.class), playerRepository);
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord record = new PunishmentRecord(
                "AB123",
                PunishmentType.BAN,
                "target-uuid",
                "203.0.113.10",
                null,
                "executor-uuid",
                "Test reason",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                true,
                false,
                false,
                false,
                Map.of(
                        "template", "hacking",
                        "template_step", "2nd offense"
                )
        );
        when(punishmentService.findById("AB123")).thenReturn(Optional.of(record));
        when(punishmentService.isIpPunishment(record)).thenReturn(true);
        when(playerRepository.findByUuid("target-uuid"))
                .thenReturn(Optional.of(new PlayerSnapshot("target-uuid", "TargetUser", "203.0.113.10")));
        when(playerRepository.findByUuid("executor-uuid"))
                .thenReturn(Optional.of(new PlayerSnapshot("executor-uuid", "Moderator", "203.0.113.9")));

        invoke(registrar, "runCheck", invocation(source, "ab123"));

        verify(messageService).sendCheckHeader(source, "AB123");
        verify(messageService).sendCheckDetailType(source, "BAN");
        verify(messageService).sendCheckDetailIssuer(source, "Moderator");
        verify(messageService).sendCheckDetailMeta(source, "true", "hacking", "2nd offense");
        verify(messageService).sendCheckFooter(source, "AB123");
    }

    @Test
    void runCheckIncludesVoidReasonInStatusLineWhenVoided() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), punishmentService,
                mock(PunishmentRepository.class), playerRepository);
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord record = new PunishmentRecord(
                "AB125",
                PunishmentType.BAN,
                "target-uuid",
                "203.0.113.12",
                "TargetUser",
                "executor-uuid",
                "Test reason",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                false,
                true,
                "manual correction",
                false,
                false,
                Map.of()
        );
        when(punishmentService.findById("AB125")).thenReturn(Optional.of(record));
        when(punishmentService.isIpPunishment(record)).thenReturn(false);
        when(playerRepository.findByUuid("executor-uuid"))
                .thenReturn(Optional.of(new PlayerSnapshot("executor-uuid", "Moderator", "203.0.113.9")));

        invoke(registrar, "runCheck", invocation(source, "ab125"));

        verify(messageService).sendCheckDetailStatus(source, "false", "true", "manual correction");
    }

    @Test
    void runCheckUsesRecordedTargetUsernameWhenTargetUuidMissing() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), punishmentService,
                mock(PunishmentRepository.class), playerRepository);
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord record = new PunishmentRecord(
                "AB124",
                PunishmentType.BAN,
                null,
                "203.0.113.11",
                "NeverSeenUser",
                "executor-uuid",
                "Test reason",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                true,
                false,
                true,
                false,
                Map.of()
        );
        when(punishmentService.findById("AB124")).thenReturn(Optional.of(record));
        when(punishmentService.isIpPunishment(record)).thenReturn(false);
        when(playerRepository.findByUuid("executor-uuid"))
                .thenReturn(Optional.of(new PlayerSnapshot("executor-uuid", "Moderator", "203.0.113.9")));

        invoke(registrar, "runCheck", invocation(source, "ab124"));

        verify(messageService).sendCheckDetailTarget(source, "NeverSeenUser", "203.0.113.11");
    }

    @Test
    void runCheckPlayerQueriesOnlyActivePunishments() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        when(targetResolver.resolvePunishmentTarget("TargetUser"))
                .thenReturn(Optional.of(new TargetProfile("target-uuid", "TargetUser", "203.0.113.10", null)));
        when(punishmentRepository.activeByTarget("target-uuid", "TargetUser"))
                .thenReturn(List.of(punishment("AB001", "target-uuid", "203.0.113.10")));

        invoke(registrar, "runCheckPlayer", invocation(source, "TargetUser"));

        verify(punishmentRepository).activeByTarget("target-uuid", "TargetUser");
        verify(messageService).sendCheckPlayerHeader(source, "TargetUser");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/checkplayer TargetUser");
    }

    @Test
    void runCheckPlayerSupportsNeverSeenUsernameFallback() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        when(targetResolver.resolvePunishmentTarget("NeverSeen"))
                .thenReturn(Optional.of(new TargetProfile(null, "NeverSeen", null, null)));
        when(punishmentRepository.activeByTarget(null, "NeverSeen"))
                .thenReturn(List.of(punishment("AB002", null, null)));

        invoke(registrar, "runCheckPlayer", invocation(source, "NeverSeen"));

        verify(punishmentRepository).activeByTarget(null, "NeverSeen");
        verify(messageService).sendCheckPlayerHeader(source, "NeverSeen");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/checkplayer NeverSeen");
    }

    @Test
    void runCheckIpResolvesPlayerInputToIp() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        when(targetResolver.resolvePlayer("TargetUser"))
                .thenReturn(Optional.of(new TargetProfile("target-uuid", "TargetUser", "203.0.113.10", null)));
        when(punishmentRepository.activeByIp("203.0.113.10")).thenReturn(List.of());

        invoke(registrar, "runCheckIp", invocation(source, "TargetUser"));

        verify(punishmentRepository).activeByIp("203.0.113.10");
        verify(messageService).sendCheckIpHeader(source, "203.0.113.10");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/checkip TargetUser");
        verify(messageService).sendActionNotFound(source);
    }

    @Test
    void runCheckWarnsSupportsNeverSeenUsernameAndExcludesVoidedWarns() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord visibleWarn = new PunishmentRecord(
                "WR001",
                PunishmentType.WARN,
                null,
                null,
                "NeverSeen",
                "executor-uuid",
                "warn reason",
                Instant.now(),
                null,
                false,
                false,
                false,
                false,
                Map.of()
        );
        PunishmentRecord voidedWarn = new PunishmentRecord(
                "WR002",
                PunishmentType.WARN,
                null,
                null,
                "NeverSeen",
                "executor-uuid",
                "voided warn",
                Instant.now(),
                null,
                false,
                true,
                "appeal",
                false,
                false,
                Map.of()
        );
        PunishmentRecord ban = punishment("BA001", null, null);

        when(targetResolver.resolvePunishmentTarget("NeverSeen"))
                .thenReturn(Optional.of(new TargetProfile(null, "NeverSeen", null, null)));
        when(punishmentRepository.historyByTarget(null, "NeverSeen"))
                .thenReturn(List.of(visibleWarn, voidedWarn, ban));

        invoke(registrar, "runCheckWarns", invocation(source, "NeverSeen"));

        verify(messageService).sendCheckWarnsHeader(source, "NeverSeen");
        verify(messageService).sendCheckSummaryEntry(source, "WR001", "WARN", "warn reason");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/checkwarns NeverSeen");
    }

    @Test
    void runCheckWarnsSupportsIpInput() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord warn = new PunishmentRecord(
                "WR010",
                PunishmentType.WARN,
                "target-uuid",
                "203.0.113.10",
                "TargetUser",
                "executor-uuid",
                "warn reason",
                Instant.now(),
                null,
                false,
                false,
                false,
                false,
                Map.of()
        );
        when(punishmentRepository.historyByIp("203.0.113.10")).thenReturn(List.of(warn));

        invoke(registrar, "runCheckWarns", invocation(source, "203.0.113.10"));

        verify(punishmentRepository).historyByIp("203.0.113.10");
        verify(messageService).sendCheckWarnsHeader(source, "203.0.113.10");
        verify(messageService).sendCheckSummaryEntryWithTarget(source, "WR010", "WARN", "warn reason", "TargetUser");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/checkwarns 203.0.113.10");
    }

    @Test
    void runHistorySupportsNeverSeenUsernameFallback() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        when(targetResolver.resolvePunishmentTarget("NeverSeen"))
                .thenReturn(Optional.of(new TargetProfile(null, "NeverSeen", null, null)));
        when(punishmentRepository.historyByTarget(null, "NeverSeen"))
                .thenReturn(List.of(punishment("HI001", null, null)));

        invoke(registrar, "runHistory", invocation(source, "NeverSeen"));

        verify(punishmentRepository).historyByTarget(null, "NeverSeen");
        verify(messageService).sendHistoryHeader(source, "NeverSeen");
                verify(messageService).sendHistoryEntry(source, "HI001", "BAN", "reason");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/history NeverSeen");
    }

        @Test
        void runHistorySupportsIpInput() throws Exception {
                MessageService messageService = mock(MessageService.class);
                PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
                PunishmentService punishmentService = mock(PunishmentService.class);
                FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), punishmentService,
                                punishmentRepository, mock(PlayerRepository.class));
                CommandSource source = mock(CommandSource.class);

                PunishmentRecord history = punishment("HI100", null, "203.0.113.10");
                when(punishmentRepository.historyByIp("203.0.113.10")).thenReturn(List.of(history));
                when(punishmentService.historyActionsByTarget(null, "203.0.113.10")).thenReturn(List.of());

                invoke(registrar, "runHistory", invocation(source, "203.0.113.10"));

                verify(punishmentRepository).historyByIp("203.0.113.10");
                verify(punishmentService).historyActionsByTarget(null, "203.0.113.10");
                verify(messageService).sendHistoryHeader(source, "203.0.113.10");
                verify(messageService).sendHistoryEntry(source, "HI100", "BAN", "reason");
                verify(messageService).sendPaginationFooter(source, 1, 1, "/history 203.0.113.10");
        }

    @Test
    void runHistoryIncludesVoidReasonWhenPresent() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, mock(PunishmentService.class),
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord voided = new PunishmentRecord(
                "HI777",
                PunishmentType.BAN,
                null,
                null,
                "NeverSeen",
                "executor-uuid",
                "initial reason",
                Instant.now(),
                null,
                false,
                true,
                "appeal accepted",
                false,
                false,
                Map.of()
        );

        when(targetResolver.resolvePunishmentTarget("NeverSeen"))
                .thenReturn(Optional.of(new TargetProfile(null, "NeverSeen", null, null)));
        when(punishmentRepository.historyByTarget(null, "NeverSeen"))
                .thenReturn(List.of(voided));

        invoke(registrar, "runHistory", invocation(source, "NeverSeen"));

        verify(messageService).sendHistoryEntry(source, "HI777", "BAN", "initial reason");
    }

    @Test
    void runHistoryMergesModerationActionsInChronologicalOrder() throws Exception {
        MessageService messageService = mock(MessageService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        FluxCommandRegistrar registrar = registrar(messageService, targetResolver, punishmentService,
                punishmentRepository, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        PunishmentRecord punishment = new PunishmentRecord(
                "HI010",
                PunishmentType.BAN,
                null,
                null,
                "NeverSeen",
                "executor-uuid",
                "initial ban",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                false,
                false,
                false,
                false,
                Map.of()
        );
        ModerationActionRecord reversal = new ModerationActionRecord(
                42L,
                ModerationActionType.UNBAN,
                "NeverSeen",
                "HI010",
                "executor-uuid",
                "appeal accepted",
                Instant.parse("2026-01-02T00:00:00Z")
        );

        when(targetResolver.resolvePunishmentTarget("NeverSeen"))
                .thenReturn(Optional.of(new TargetProfile(null, "NeverSeen", null, null)));
        when(punishmentRepository.historyByTarget(null, "NeverSeen")).thenReturn(List.of(punishment));
        when(punishmentService.historyActionsByTarget(null, "NeverSeen")).thenReturn(List.of(reversal));

        invoke(registrar, "runHistory", invocation(source, "NeverSeen"));

        InOrder order = inOrder(messageService);
        order.verify(messageService).sendHistoryEntry(source, "HI010", "UNBAN", "appeal accepted");
        order.verify(messageService).sendHistoryEntry(source, "HI010", "BAN", "initial ban");
    }

    @Test
    void runFluxRootRejectsExtraVersionArgumentsAndUsesFixedVersionTemplate() throws Exception {
        MessageService messageService = mock(MessageService.class);
        FluxCommandRegistrar registrar = registrar(messageService, mock(TargetResolver.class), mock(PunishmentService.class),
                mock(PunishmentRepository.class), mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);
        when(messageService.usageFlux()).thenReturn("/flux [reload|ver|info|version]");

        invoke(registrar, "runFluxRoot", invocation(source, "version", "extra"));
        verify(messageService).sendUsage(source, "/flux [reload|ver|info|version]");

        invoke(registrar, "runFluxRoot", invocation(source));
        verify(messageService).sendVersion(source, "1.0.0-test");
    }

    private static FluxCommandRegistrar registrar(
            MessageService messageService,
            TargetResolver targetResolver,
            PunishmentService punishmentService,
            PunishmentRepository punishmentRepository,
            PlayerRepository playerRepository
    ) {
        return new FluxCommandRegistrar(
                new Object(),
                mock(ProxyServer.class),
                messageService,
                mock(PermissionService.class),
                null,
                targetResolver,
                mock(TemplateService.class),
                punishmentService,
                punishmentRepository,
                playerRepository,
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

    private static PunishmentRecord punishment(String id, String targetUuid, String targetIp) {
        return new PunishmentRecord(
                id,
                PunishmentType.BAN,
                targetUuid,
                targetIp,
                null,
                "executor-uuid",
                "reason",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        );
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... arguments) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(arguments);
        return invocation;
    }
}
