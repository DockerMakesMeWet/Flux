package org.owl.flux.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.owl.flux.config.model.TemplatesConfig;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.service.TargetResolver;
import org.owl.flux.service.TemplateService;

class FluxCommandRegistrarCompletionSourceTest {

    @Test
    void suggestOnlineUsernamesFiltersByPrefixUsingOnlinePlayersOnly() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"), player("Beta"), player("Ally"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);

        PlayerRepository playerRepository = mock(PlayerRepository.class);
        FluxCommandRegistrar registrar = registrar(server, playerRepository);

        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"al"});

        List<String> suggestions = invokeSuggestOnlineUsernames(registrar, invocation, 0);
        assertEquals(List.of("Alpha", "Ally"), suggestions);
        verifyNoInteractions(playerRepository);
    }

    @Test
    void suggestOnlineUsernamesCanUseDifferentArgumentIndex() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"), player("Beta"), player("Ally"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class));

        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.arguments()).thenReturn(new String[]{"sub", "be"});

        List<String> suggestions = invokeSuggestOnlineUsernames(registrar, invocation, 1);
        assertEquals(List.of("Beta"), suggestions);
    }

    @Test
    void suggestTemplatesByPrefixIncludesOnlyPermittedTemplates() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        PermissionService permissionService = mock(PermissionService.class);
        TemplateService templateService = mock(TemplateService.class);
        CommandSource source = mock(CommandSource.class);

        Map<String, TemplatesConfig.TemplateDefinition> templates = new LinkedHashMap<>();
        templates.put("hacking", template("flux.template.hacking"));
        templates.put("spam", template("flux.template.spam"));
        when(templateService.allTemplates()).thenReturn(templates);
        when(permissionService.has(source, "flux.template.*")).thenReturn(false);
        when(permissionService.has(source, "flux.template.hacking")).thenReturn(true);
        when(permissionService.has(source, "flux.template.spam")).thenReturn(false);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class), permissionService, templateService);
        List<String> suggestions = invokeSuggestTemplatesByPrefix(registrar, source, "#h");

        assertEquals(List.of("#hacking"), suggestions);
    }

    @Test
    void suggestTemplatesByPrefixSupportsWildcardPermissionAndCaseInsensitivePrefix() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        PermissionService permissionService = mock(PermissionService.class);
        TemplateService templateService = mock(TemplateService.class);
        CommandSource source = mock(CommandSource.class);

        Map<String, TemplatesConfig.TemplateDefinition> templates = new LinkedHashMap<>();
        templates.put("hacking", template("flux.template.hacking"));
        templates.put("spam", template("flux.template.spam"));
        when(templateService.allTemplates()).thenReturn(templates);
        when(permissionService.has(source, "flux.template.*")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class), permissionService, templateService);
        List<String> suggestions = invokeSuggestTemplatesByPrefix(registrar, source, "#SP");

        assertEquals(List.of("#spam"), suggestions);
    }

    @Test
    void suggestTemplatesByPrefixSkipsTemplatesWithoutUsableDefinitionOrPermission() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        PermissionService permissionService = mock(PermissionService.class);
        TemplateService templateService = mock(TemplateService.class);
        CommandSource source = mock(CommandSource.class);

        Map<String, TemplatesConfig.TemplateDefinition> templates = new LinkedHashMap<>();
        templates.put("hacking", template("flux.template.hacking"));
        templates.put("blankperm", template("   "));
        templates.put("missing", null);
        when(templateService.allTemplates()).thenReturn(templates);
        when(permissionService.has(source, "flux.template.*")).thenReturn(false);
        when(permissionService.has(source, "flux.template.hacking")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class), permissionService, templateService);
        List<String> suggestions = invokeSuggestTemplatesByPrefix(registrar, source, "#");

        assertEquals(List.of("#hacking"), suggestions);
    }

    @Test
    void suggestBanLikeProvidesPositionalTargetDurationAndTemplateSuggestions() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"), player("Beta"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);
        PermissionService permissionService = mock(PermissionService.class);
        TemplateService templateService = mock(TemplateService.class);
        CommandSource source = mock(CommandSource.class);

        Map<String, TemplatesConfig.TemplateDefinition> templates = new LinkedHashMap<>();
        templates.put("hacking", template("flux.template.hacking"));
        when(templateService.allTemplates()).thenReturn(templates);
        when(permissionService.has(source, "flux.template.*")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class), permissionService, templateService);

        List<String> targetSuggestions = invokeSuggestionMethod(registrar, "suggestBanLike", invocation(source, "al"));
        assertEquals(List.of("Alpha"), targetSuggestions);

        List<String> payloadStartSuggestions = invokeSuggestionMethod(registrar, "suggestBanLike", invocation(source, "Alpha", ""));
        assertTrue(payloadStartSuggestions.contains("perm"));
        assertTrue(payloadStartSuggestions.contains("#hacking"));

        List<String> templateAfterDuration =
                invokeSuggestionMethod(registrar, "suggestBanLike", invocation(source, "Alpha", "1d", "#h"));
        assertEquals(List.of("#hacking"), templateAfterDuration);
    }

    @Test
    void suggestPunishmentCommandsFilterTemplatesByPunishmentType() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);
        PermissionService permissionService = mock(PermissionService.class);
        TemplateService templateService = mock(TemplateService.class);
        CommandSource source = mock(CommandSource.class);

        Map<String, TemplatesConfig.TemplateDefinition> templates = new LinkedHashMap<>();
        templates.put("griefing", template("flux.template.griefing", "BAN"));
        templates.put("chat-spam", template("flux.template.chat-spam", "MUTE"));
        templates.put("abuse", template("flux.template.abuse", "WARN"));
        when(templateService.allTemplates()).thenReturn(templates);
        when(permissionService.has(source, "flux.template.*")).thenReturn(true);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class), permissionService, templateService);

        assertEquals(List.of("#griefing"), invokeSuggestionMethod(registrar, "suggestBanLike", invocation(source, "Alpha", "1d", "#")));
        assertEquals(List.of("#chat-spam"), invokeSuggestionMethod(registrar, "suggestMuteLike", invocation(source, "Alpha", "1d", "#")));
        assertEquals(List.of("#abuse"), invokeSuggestionMethod(registrar, "suggestWarnLike", invocation(source, "Alpha", "#")));
        assertEquals(List.of("#griefing"), invokeSuggestionMethod(registrar, "suggestIpBan", invocation(source, "Alpha", "1d", "#")));
    }

    @Test
    void suggestIpBanAllowsIpLikeTargetInputWithoutForcingUsernameSuggestions() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"), player("Beta"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);

        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        List<String> usernameSuggestions = invokeSuggestionMethod(registrar, "suggestIpBan", invocation(source, "al"));
        assertEquals(List.of("Alpha"), usernameSuggestions);

        List<String> ipSuggestions = invokeSuggestionMethod(registrar, "suggestIpBan", invocation(source, "203.0"));
        assertEquals(List.of(), ipSuggestions);

        verify(server).getAllPlayers();
    }

    @Test
    void suggestSingleArgumentUsernameCommandsUseExpectedTargetSources() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"), player("Beta"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);
        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestUnmute", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestHistory", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestCheckPlayer", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestCheckIp", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestCheckWarns", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestAlts", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestUnban", invocation(source, "al")));
        assertEquals(List.of("Alpha"), invokeSuggestionMethod(registrar, "suggestIpHistory", invocation(source, "al")));

        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestHistory", invocation(source, "203.0")));
        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestCheckIp", invocation(source, "203.0")));
        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestCheckWarns", invocation(source, "203.0")));
        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestUnban", invocation(source, "203.0")));
        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestIpHistory", invocation(source, "203.0")));
    }

    @Test
    void paginatedListCommandsSuggestPageNumbersOnSecondArgument() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        Player alpha = player("Alpha");
        when(server.getAllPlayers()).thenReturn(List.of(alpha));
        FluxCommandRegistrar registrar = registrar(server, mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        assertEquals(List.of("2"), invokeSuggestionMethod(registrar, "suggestHistory", invocation(source, "Alpha", "2")));
        assertEquals(List.of("3"), invokeSuggestionMethod(registrar, "suggestCheckPlayer", invocation(source, "Alpha", "3")));
        assertEquals(List.of("4"), invokeSuggestionMethod(registrar, "suggestCheckIp", invocation(source, "Alpha", "4")));
        assertEquals(List.of("5"), invokeSuggestionMethod(registrar, "suggestCheckWarns", invocation(source, "Alpha", "5")));
        assertEquals(List.of("5"), invokeSuggestionMethod(registrar, "suggestAlts", invocation(source, "Alpha", "5")));
        assertEquals(List.of("10"), invokeSuggestionMethod(registrar, "suggestIpHistory", invocation(source, "Alpha", "10")));
    }

    @Test
    void suggestUnbanAndUnmuteIncludeActivePunishmentIdsByPrefix() throws Exception {
        ProxyServer server = mock(ProxyServer.class);
        List<Player> onlinePlayers = List.of(player("Alpha"));
        when(server.getAllPlayers()).thenReturn(onlinePlayers);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PunishmentRepository punishmentRepository = mock(PunishmentRepository.class);
        when(punishmentRepository.findRecentActiveIdsByTypePrefix(PunishmentType.BAN, "ab", 25))
                .thenReturn(List.of("AB1234"));
        when(punishmentRepository.findRecentActiveIdsByTypePrefix(PunishmentType.MUTE, "mu", 25))
                .thenReturn(List.of("MU7777"));

        FluxCommandRegistrar registrar = registrar(
                server,
                playerRepository,
                mock(PermissionService.class),
                mock(TemplateService.class),
                punishmentRepository
        );
        CommandSource source = mock(CommandSource.class);

        assertEquals(List.of("AB1234"), invokeSuggestionMethod(registrar, "suggestUnban", invocation(source, "ab")));
        assertEquals(List.of("MU7777"), invokeSuggestionMethod(registrar, "suggestUnmute", invocation(source, "mu")));
    }

    @Test
    void suggestFluxRootSubcommandsIncludeReloadVerInfoAndVersion() throws Exception {
        FluxCommandRegistrar registrar = registrar(mock(ProxyServer.class), mock(PlayerRepository.class));

        List<String> suggestions = invokeSuggestionMethod(registrar, "suggestFluxRoot", invocation(mock(CommandSource.class), ""));
        assertEquals(List.of("reload", "ver", "info", "version"), suggestions);

        List<String> prefixSuggestions =
                invokeSuggestionMethod(registrar, "suggestFluxRoot", invocation(mock(CommandSource.class), "v"));
        assertEquals(List.of("ver", "version"), prefixSuggestions);
    }

    @Test
    void suggestFluxRootReturnsNoSuggestionsAfterSubcommandIsEntered() throws Exception {
        FluxCommandRegistrar registrar = registrar(mock(ProxyServer.class), mock(PlayerRepository.class));
        CommandSource source = mock(CommandSource.class);

        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestFluxRoot", invocation(source, "reload", "")));
        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestFluxRoot", invocation(source, "ReLoAd", "extra")));
        assertEquals(List.of(), invokeSuggestionMethod(registrar, "suggestFluxRoot", invocation(source, "version", "extra")));
    }

    @Test
    void completeByPrefixIsCaseInsensitiveAndCapsSanitizedResults() throws Exception {
        List<String> suggestions = invokeCompleteByPrefix(
                Arrays.asList("  Alpha  ", "", null, "Ally", "Alfred"),
                " aL ",
                2
        );

        assertEquals(List.of("Alpha", "Ally"), suggestions);
    }

    @Test
    void sanitizeSuggestionsTrimsDeduplicatesAndCapsResults() throws Exception {
        List<String> suggestions = invokeSanitizeSuggestions(
                Arrays.asList("  one  ", "one", "", null, "two", "three"),
                2
        );

        assertEquals(List.of("one", "two"), suggestions);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSuggestOnlineUsernames(
            FluxCommandRegistrar registrar,
            SimpleCommand.Invocation invocation,
            int argumentIndex
    ) throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod(
                "suggestOnlineUsernames",
                SimpleCommand.Invocation.class,
                int.class
        );
        method.setAccessible(true);
        return (List<String>) method.invoke(registrar, invocation, argumentIndex);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSuggestTemplatesByPrefix(
            FluxCommandRegistrar registrar,
            CommandSource source,
            String prefix
    ) throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod(
                "suggestTemplatesByPrefix",
                CommandSource.class,
                String.class
        );
        method.setAccessible(true);
        return (List<String>) method.invoke(registrar, source, prefix);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSuggestionMethod(
            FluxCommandRegistrar registrar,
            String methodName,
            SimpleCommand.Invocation invocation
    ) throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod(methodName, SimpleCommand.Invocation.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(registrar, invocation);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeCompleteByPrefix(Iterable<String> candidates, String prefix, int limit)
            throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod(
                "completeByPrefix",
                Iterable.class,
                String.class,
                int.class
        );
        method.setAccessible(true);
        return (List<String>) method.invoke(null, candidates, prefix, limit);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSanitizeSuggestions(Iterable<String> candidates, int limit) throws Exception {
        Method method = FluxCommandRegistrar.class.getDeclaredMethod("sanitizeSuggestions", Iterable.class, int.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, candidates, limit);
    }

    private static FluxCommandRegistrar registrar(ProxyServer server, PlayerRepository playerRepository) {
        return registrar(server, playerRepository, mock(PermissionService.class), mock(TemplateService.class), mock(PunishmentRepository.class));
    }

    private static FluxCommandRegistrar registrar(
            ProxyServer server,
            PlayerRepository playerRepository,
            PermissionService permissionService,
            TemplateService templateService
    ) {
        return registrar(server, playerRepository, permissionService, templateService, mock(PunishmentRepository.class));
    }

    private static FluxCommandRegistrar registrar(
            ProxyServer server,
            PlayerRepository playerRepository,
            PermissionService permissionService,
            TemplateService templateService,
            PunishmentRepository punishmentRepository
    ) {
        return new FluxCommandRegistrar(
                new Object(),
                server,
                mock(MessageService.class),
                permissionService,
                null,
                mock(TargetResolver.class),
                templateService,
                mock(PunishmentService.class),
                punishmentRepository,
                playerRepository,
                mock(MastersService.class),
                () -> true,
                "1.0.0-test"
        );
    }

    private static TemplatesConfig.TemplateDefinition template(String permission) {
        return template(permission, "BAN");
    }

    private static TemplatesConfig.TemplateDefinition template(String permission, String type) {
        TemplatesConfig.TemplateDefinition definition = new TemplatesConfig.TemplateDefinition();
        definition.permission = permission;
        definition.type = type;
        return definition;
    }

    private static Player player(String username) {
        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn(username);
        return player;
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... arguments) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(arguments);
        return invocation;
    }
}
