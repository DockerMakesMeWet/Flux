package org.owl.flux.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                "executor-uuid",
                "Test reason",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                true,
                false,
                Map.of("template", "hacking")
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
        verify(messageService).sendCheckDetailMeta(source, "true", "hacking");
        verify(messageService).sendCheckFooter(source, "AB123");
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
        verify(messageService).sendHistoryEntry(source, "HI001", "BAN", "reason", "false");
        verify(messageService).sendPaginationFooter(source, 1, 1, "/history NeverSeen");
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
                "executor-uuid",
                "reason",
                Instant.now(),
                null,
                true,
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
