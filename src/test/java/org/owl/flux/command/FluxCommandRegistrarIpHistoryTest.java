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
import org.owl.flux.data.model.AccountVisit;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.service.TargetResolver;
import org.owl.flux.service.TemplateService;

class FluxCommandRegistrarIpHistoryTest {

    @Test
    void runIpHistoryForIpShowsDistinctAccountsWithLastSeen() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findAccountsByIp("203.0.113.10")).thenReturn(List.of(
                new AccountVisit("Alpha", Instant.parse("2026-01-03T00:00:00Z")),
                new AccountVisit("Bravo", Instant.parse("2026-01-02T00:00:00Z"))
        ));

        FluxCommandRegistrar registrar = registrar(messageService, playerRepository);
        CommandSource source = mock(CommandSource.class);

        invoke(registrar, "runIpHistory", invocation(source, "203.0.113.10"));

        verify(messageService).sendIpHistoryHeader(source, "203.0.113.10");
        verify(messageService).send(
                source,
                "<gray>-</gray> <white><account></white> <dark_gray>(<seen>)</dark_gray>",
                Map.of("account", "Alpha", "seen", "2026-01-03T00:00:00Z")
        );
        verify(messageService).send(
                source,
                "<gray>-</gray> <white><account></white> <dark_gray>(<seen>)</dark_gray>",
                Map.of("account", "Bravo", "seen", "2026-01-02T00:00:00Z")
        );
    }

    @Test
    void runIpHistoryForIpShowsActionNotFoundWhenNoAccountsExist() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findAccountsByIp("203.0.113.99")).thenReturn(List.of());

        FluxCommandRegistrar registrar = registrar(messageService, playerRepository);
        CommandSource source = mock(CommandSource.class);

        invoke(registrar, "runIpHistory", invocation(source, "203.0.113.99"));

        verify(messageService).sendIpHistoryHeader(source, "203.0.113.99");
        verify(messageService).sendActionNotFound(source);
    }

    private static FluxCommandRegistrar registrar(MessageService messageService, PlayerRepository playerRepository) {
        return new FluxCommandRegistrar(
                new Object(),
                mock(ProxyServer.class),
                messageService,
                mock(PermissionService.class),
                null,
                mock(TargetResolver.class),
                mock(TemplateService.class),
                mock(PunishmentService.class),
                mock(PunishmentRepository.class),
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

    private static SimpleCommand.Invocation invocation(CommandSource source, String... arguments) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(arguments);
        return invocation;
    }
}
