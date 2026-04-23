package org.owl.flux.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.service.HierarchyService;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.service.TargetResolver;
import org.owl.flux.service.TemplateService;
import org.owl.flux.service.model.PunishmentResult;
import org.owl.flux.service.model.TargetProfile;
import org.owl.flux.service.model.TemplateResolution;

class FluxCommandRegistrarTemplateTypeValidationTest {

    @Test
    void runWarnLikeRejectsNonWarnTemplate() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        HierarchyService hierarchyService = mock(HierarchyService.class);
        TemplateService templateService = mock(TemplateService.class);
        FluxCommandRegistrar registrar = registrar(messageService, punishmentService, targetResolver, hierarchyService, templateService);

        CommandSource source = mock(CommandSource.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);
        when(targetResolver.resolvePunishmentTarget("TargetUser")).thenReturn(Optional.of(target));
        when(hierarchyService.canTarget(eq(source), any(TargetProfile.class))).thenReturn(true);
        when(templateService.resolve(source, "#chatmute", "uuid-1")).thenReturn(
                new TemplateResolution(PunishmentType.MUTE, Duration.ofMinutes(30), "spam", "chatmute", 1, "1st offense", false)
        );

        invoke(registrar, "runWarnLike", invocation(source, "TargetUser", "#chatmute"));

        verify(messageService).sendTemplateTypeMismatch(source, "WARN", "MUTE");
        verify(punishmentService, never()).create(any());
    }

    @Test
    void runMuteLikeAcceptsMuteTemplate() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        HierarchyService hierarchyService = mock(HierarchyService.class);
        TemplateService templateService = mock(TemplateService.class);
        FluxCommandRegistrar registrar = registrar(messageService, punishmentService, targetResolver, hierarchyService, templateService);

        CommandSource source = mock(CommandSource.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);
        when(targetResolver.resolvePunishmentTarget("TargetUser")).thenReturn(Optional.of(target));
        when(hierarchyService.canTarget(eq(source), any(TargetProfile.class))).thenReturn(true);
        when(templateService.resolve(source, "#chatmute", "uuid-1")).thenReturn(
                new TemplateResolution(PunishmentType.MUTE, Duration.ofMinutes(30), "spam", "chatmute", 2, "2nd offense", false)
        );

        PunishmentRecord record = new PunishmentRecord(
                "MU1001",
                PunishmentType.MUTE,
                "uuid-1",
                "203.0.113.10",
                "TargetUser",
                "executor-uuid",
                "spam",
                Instant.now(),
                Instant.now().plusSeconds(1800),
                true,
                false,
                false,
                false,
                Map.of("template", "chatmute")
        );
        when(punishmentService.create(any())).thenReturn(new PunishmentResult(record, 0));

        invoke(registrar, "runMuteLike", invocation(source, "TargetUser", "#chatmute"));

        verify(punishmentService).create(argThat(request ->
                request.type() == PunishmentType.MUTE
                        && "chatmute".equals(request.templateName())
                        && Integer.valueOf(2).equals(request.templateOffenseStep())
                        && "2nd offense".equals(request.templateOffenseLabel())
        ));
        verify(messageService).sendActionCreated(source, "MUTE", "TargetUser", "MU1001");
    }

    @Test
    void runBanLikeCreatesAccountOnlyBanRequest() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        HierarchyService hierarchyService = mock(HierarchyService.class);
        TemplateService templateService = mock(TemplateService.class);
        FluxCommandRegistrar registrar = registrar(messageService, punishmentService, targetResolver, hierarchyService, templateService);

        CommandSource source = mock(CommandSource.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);
        when(targetResolver.resolvePunishmentTarget("TargetUser")).thenReturn(Optional.of(target));
        when(hierarchyService.canTarget(eq(source), any(TargetProfile.class))).thenReturn(true);

        PunishmentRecord record = new PunishmentRecord(
                "BA1001",
                PunishmentType.BAN,
                "uuid-1",
                "203.0.113.10",
                "TargetUser",
                "executor-uuid",
                "rule violation",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        );
        when(punishmentService.create(any())).thenReturn(new PunishmentResult(record, 0));

        invoke(registrar, "runBanLike", invocation(source, "TargetUser", "rule", "violation"));

        verify(punishmentService).create(argThat(request ->
                request.type() == PunishmentType.BAN
                        && !request.ipPunishment()
                        && request.ipOverride() == null
        ));
    }

    @Test
    void runIpBanCreatesIpPunishmentRequest() throws Exception {
        MessageService messageService = mock(MessageService.class);
        PunishmentService punishmentService = mock(PunishmentService.class);
        TargetResolver targetResolver = mock(TargetResolver.class);
        HierarchyService hierarchyService = mock(HierarchyService.class);
        TemplateService templateService = mock(TemplateService.class);
        FluxCommandRegistrar registrar = registrar(messageService, punishmentService, targetResolver, hierarchyService, templateService);

        CommandSource source = mock(CommandSource.class);
        TargetProfile target = new TargetProfile("uuid-1", "TargetUser", "203.0.113.10", null);
        when(targetResolver.resolvePlayer("TargetUser")).thenReturn(Optional.of(target));
        when(hierarchyService.canTarget(eq(source), any(TargetProfile.class))).thenReturn(true);

        PunishmentRecord record = new PunishmentRecord(
                "IB1001",
                PunishmentType.BAN,
                "uuid-1",
                "203.0.113.10",
                "TargetUser",
                "executor-uuid",
                "proxy evasion",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                Map.of("ip_punishment", "true")
        );
        when(punishmentService.create(any())).thenReturn(new PunishmentResult(record, 1));

        invoke(registrar, "runIpBan", invocation(source, "TargetUser", "proxy", "evasion"));

        verify(punishmentService).create(argThat(request ->
                request.type() == PunishmentType.BAN
                        && request.ipPunishment()
                        && "203.0.113.10".equals(request.ipOverride())
        ));
    }

    private static FluxCommandRegistrar registrar(
            MessageService messageService,
            PunishmentService punishmentService,
            TargetResolver targetResolver,
            HierarchyService hierarchyService,
            TemplateService templateService
    ) {
        return new FluxCommandRegistrar(
                new Object(),
                mock(ProxyServer.class),
                messageService,
                mock(PermissionService.class),
                hierarchyService,
                targetResolver,
                templateService,
                punishmentService,
                mock(PunishmentRepository.class),
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

    private static SimpleCommand.Invocation invocation(CommandSource source, String... arguments) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(arguments);
        return invocation;
    }
}
