package org.owl.flux.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.owl.flux.config.model.TemplatesConfig;
import org.owl.flux.data.model.AccountVisit;
import org.owl.flux.data.model.IpVisit;
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
import org.owl.flux.service.model.PunishmentRequest;
import org.owl.flux.service.model.PunishmentResult;
import org.owl.flux.service.model.TargetProfile;
import org.owl.flux.service.model.TemplateResolution;
import org.owl.flux.util.DurationParser;
import org.owl.flux.util.NetworkUtil;
import org.owl.flux.util.PunishmentTimeFormatter;

public final class FluxCommandRegistrar {
    private static final int DEFAULT_COMPLETION_LIMIT = 25;
    private static final String CONSOLE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String USAGE_BAN = "/ban <user> [duration] <reason/#template>";
    private static final String USAGE_MUTE = "/mute <user> [duration] <reason/#template>";
    private static final String USAGE_WARN = "/warn <user> <reason/#template>";
    private static final String USAGE_KICK = "/kick <user> <reason/#template>";
    private static final String USAGE_IPBAN = "/ipban <user/ip> [duration] <reason/#template>";
    private static final String USAGE_UNBAN = "/unban <user/ip/id>";
    private static final String USAGE_UNMUTE = "/unmute <user/id>";
    private static final String USAGE_VOID = "/void <id>";
    private static final String USAGE_HISTORY = "/history <user>";
    private static final String USAGE_ALTS = "/alts <user>";
    private static final String USAGE_IPHISTORY = "/iphistory <ip/user>";
    private static final String USAGE_CHECK = "/check <id>";
    private static final String USAGE_CHECKPLAYER = "/checkplayer <user>";
    private static final String USAGE_CHECKIP = "/checkip <ip/user>";
    private static final String USAGE_FLUX = "/flux [reload|ver|info|version]";
    private static final List<String> DURATION_TOKEN_SUGGESTIONS = List.of(
            "15m",
            "30m",
            "1h",
            "6h",
            "12h",
            "1d",
            "3d",
            "7d",
            "30d",
            "perm",
            "permanent"
    );

    private final Object plugin;
    private final ProxyServer server;
    private final MessageService messageService;
    private final PermissionService permissionService;
    private final HierarchyService hierarchyService;
    private final TargetResolver targetResolver;
    private final TemplateService templateService;
    private final PunishmentService punishmentService;
    private final PunishmentRepository punishmentRepository;
    private final PlayerRepository playerRepository;
    private final MastersService mastersService;
    private final ReloadHandler reloadHandler;
    private final String pluginVersion;
    private final List<CommandMeta> registrations = new ArrayList<>();

    public FluxCommandRegistrar(
            Object plugin,
            ProxyServer server,
            MessageService messageService,
            PermissionService permissionService,
            HierarchyService hierarchyService,
            TargetResolver targetResolver,
            TemplateService templateService,
            PunishmentService punishmentService,
            PunishmentRepository punishmentRepository,
            PlayerRepository playerRepository,
            MastersService mastersService,
            ReloadHandler reloadHandler,
            String pluginVersion
    ) {
        this.plugin = plugin;
        this.server = server;
        this.messageService = messageService;
        this.permissionService = permissionService;
        this.hierarchyService = hierarchyService;
        this.targetResolver = targetResolver;
        this.templateService = templateService;
        this.punishmentService = punishmentService;
        this.punishmentRepository = punishmentRepository;
        this.playerRepository = playerRepository;
        this.mastersService = mastersService;
        this.reloadHandler = reloadHandler;
        this.pluginVersion = pluginVersion;
    }

    public void registerAll() {
        register("ban", "flux.command.ban", this::runBanLike, this::suggestBanLike, "tempban");
        register("mute", "flux.command.mute", this::runMuteLike, this::suggestMuteLike, "tempmute");
        register("warn", "flux.command.warn", this::runWarnLike, this::suggestWarnLike);
        register("kick", "flux.command.kick", this::runKickLike, this::suggestKickLike);
        register("ipban", "flux.command.ipban", this::runIpBan, this::suggestIpBan, "banip");
        register("unban", "flux.command.unban", this::runUnban, this::suggestUnban, "pardon");
        register("unmute", "flux.command.unmute", this::runUnmute, this::suggestUnmute);
        register("void", "flux.command.void", this::runVoid, this::suggestVoid);
        register("check", "flux.command.check", this::runCheck, this::suggestCheck);
        register("checkplayer", "flux.command.checkplayer", this::runCheckPlayer, this::suggestCheckPlayer);
        register("checkip", "flux.command.checkip", this::runCheckIp, this::suggestCheckIp);
        register("history", "flux.command.history", this::runHistory, this::suggestHistory);
        register("alts", "flux.command.alts", this::runAlts, this::suggestAlts, "dupeip");
        register("iphistory", "flux.command.iphistory", this::runIpHistory, this::suggestIpHistory);
        register("flux", "flux.command.version", this::runFluxRoot, this::suggestFluxRoot);
    }

    public void unregisterAll() {
        CommandManager commandManager = server.getCommandManager();
        for (CommandMeta registration : registrations) {
            commandManager.unregister(registration);
        }
        registrations.clear();
    }

    private void register(String alias, String permission, CommandExecutor executor, String... aliases) {
        register(alias, permission, executor, invocation -> List.of(), aliases);
    }

    private void register(
            String alias,
            String permission,
            CommandExecutor executor,
            CommandSuggester suggester,
            String... aliases
    ) {
        SimpleCommand command = new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                if (!permissionService.has(invocation.source(), permission)) {
                    messageService.sendNoPermission(invocation.source());
                    return;
                }
                executor.execute(invocation);
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return permissionService.has(invocation.source(), permission);
            }

            @Override
            public List<String> suggest(Invocation invocation) {
                if (!permissionService.has(invocation.source(), permission)) {
                    return List.of();
                }
                return sanitizeSuggestions(suggester.suggest(invocation), DEFAULT_COMPLETION_LIMIT);
            }
        };

        CommandMeta meta = server.getCommandManager()
                .metaBuilder(alias)
                .aliases(aliases)
                .plugin(plugin)
                .build();
        server.getCommandManager().register(meta, command);
        registrations.add(meta);
    }

    private void runBanLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.BAN, true, USAGE_BAN);
    }

    private void runMuteLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.MUTE, true, USAGE_MUTE);
    }

    private void runWarnLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.WARN, false, USAGE_WARN);
    }

    private void runKickLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.KICK, false, USAGE_KICK);
    }

    private void runPlayerPunishment(
            SimpleCommand.Invocation invocation,
            PunishmentType baseType,
            boolean allowDuration,
            String usage
    ) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendRawError(invocation.source(), "Usage: " + usage);
            return;
        }

        String targetInput = args[0];
        Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(targetInput);
        if (targetMaybe.isEmpty()) {
            messageService.sendPlayerNotFound(invocation.source(), targetInput);
            return;
        }
        TargetProfile target = targetMaybe.get();

        if (!hierarchyService.canTarget(invocation.source(), target)) {
            messageService.sendTargetProtected(invocation.source());
            return;
        }

        String[] payload = Arrays.copyOfRange(args, 1, args.length);
        ParseResult parseResult = parsePunishmentPayload(payload, allowDuration);
        if (parseResult == null) {
            messageService.sendRawError(invocation.source(), "Invalid duration format.");
            return;
        }
        if (parseResult.reason().isBlank()) {
            messageService.sendRawError(invocation.source(), "Usage: " + usage);
            return;
        }

        PunishmentType appliedType = baseType;
        Duration duration = parseResult.duration();
        String reason = parseResult.reason();
        String templateName = null;

        if (reason.startsWith("#")) {
            try {
                TemplateResolution resolution = templateService.resolve(invocation.source(), reason, target.uuid());
                appliedType = resolution.type();
                duration = resolution.optionalDuration().orElse(null);
                reason = resolution.reason();
                templateName = resolution.templateName();
            } catch (IllegalArgumentException exception) {
                if ("template:no-permission".equals(exception.getMessage())) {
                    messageService.sendNoPermission(invocation.source());
                } else {
                    messageService.sendRawError(invocation.source(), "Template not found.");
                }
                return;
            }
        }

        PunishmentResult result = punishmentService.create(new PunishmentRequest(
                invocation.source(),
                target,
                appliedType,
                duration,
                reason,
                templateName,
                null,
                false
        ));

        String executorName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        messageService.sendActionCreated(invocation.source(), appliedType.name(), target.username(), result.punishment().id());
        messageService.broadcastStaffAction(result.punishment(), executorName, target.username(), target.optionalOnlinePlayer().orElse(null));
    }

    private void runIpBan(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_IPBAN);
            return;
        }

        String targetArg = args[0];
        String resolvedIp = null;
        String targetName = targetArg;
        String targetUuid = null;
        Player onlinePlayer = null;

        if (NetworkUtil.isIpLiteral(targetArg)) {
            resolvedIp = targetArg;
        } else {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(targetArg);
            if (targetMaybe.isEmpty()) {
                messageService.sendPlayerNotFound(invocation.source(), targetArg);
                return;
            }
            TargetProfile target = targetMaybe.get();
            if (!hierarchyService.canTarget(invocation.source(), target)) {
                messageService.sendTargetProtected(invocation.source());
                return;
            }
            resolvedIp = target.ip();
            targetName = target.username();
            targetUuid = target.uuid();
            onlinePlayer = target.optionalOnlinePlayer().orElse(null);
        }

        ParseResult parseResult = parsePunishmentPayload(Arrays.copyOfRange(args, 1, args.length), true);
        if (parseResult == null || parseResult.reason().isBlank()) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_IPBAN);
            return;
        }

        TargetProfile target = new TargetProfile(targetUuid, targetName, resolvedIp, onlinePlayer);
        PunishmentResult result = punishmentService.create(new PunishmentRequest(
                invocation.source(),
                target,
                PunishmentType.BAN,
                parseResult.duration(),
                parseResult.reason(),
                null,
                resolvedIp,
                true
        ));

        String executorName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        messageService.sendActionCreated(invocation.source(), "IPBAN", targetName, result.punishment().id());
        messageService.broadcastStaffAction(result.punishment(), executorName, targetName, onlinePlayer);
    }

    private void runUnban(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_UNBAN);
            return;
        }

        String input = args[0];
        boolean changed;
        String targetDisplay = input;
        boolean ipPunishment = false;
        if (NetworkUtil.isIpLiteral(input)) {
            Optional<PunishmentRecord> activeBan = punishmentService.activeBan(null, null, input);
            changed = punishmentService.unbanByIp(input);
            ipPunishment = activeBan.map(punishmentService::isIpPunishment).orElse(false);
        } else {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(input);
            if (targetMaybe.isPresent()) {
                TargetProfile target = targetMaybe.get();
                Optional<PunishmentRecord> activeBan = punishmentService.activeBan(target.uuid(), target.username(), target.ip());
                changed = punishmentService.unbanByTarget(target.uuid(), target.username());
                targetDisplay = target.username();
                ipPunishment = activeBan.map(punishmentService::isIpPunishment).orElse(false);
            } else if (looksLikePunishmentIdToken(input)) {
                String id = input.toUpperCase(Locale.ROOT);
                Optional<PunishmentRecord> recordById = punishmentService.findById(id);
                if (recordById.isEmpty() || recordById.get().type() != PunishmentType.BAN) {
                    messageService.sendActionNotFound(invocation.source());
                    return;
                }
                PunishmentRecord record = recordById.get();
                changed = punishmentService.unbanById(id);
                targetDisplay = displayTarget(record, id);
                ipPunishment = punishmentService.isIpPunishment(record);
            } else {
                messageService.sendPlayerNotFound(invocation.source(), input);
                return;
            }
        }

        if (!changed) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        punishmentService.sendUnbanWebhook(targetDisplay, invocation.source(), ipPunishment);
        messageService.sendActionUpdated(invocation.source(), targetDisplay);
    }

    private void runUnmute(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_UNMUTE);
            return;
        }

        String input = args[0];
        boolean changed;
        String targetDisplay = input;
        Optional<PunishmentRecord> unmutedRecord = Optional.empty();

        Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(input);
        if (targetMaybe.isPresent()) {
            TargetProfile target = targetMaybe.get();
            Optional<PunishmentRecord> activeMute = punishmentService.activeMute(target.uuid(), target.username());
            unmutedRecord = activeMute == null ? Optional.empty() : activeMute;
            changed = punishmentService.unmuteByTarget(target.uuid(), target.username());
            targetDisplay = target.username();
        } else if (looksLikePunishmentIdToken(input)) {
            String id = input.toUpperCase(Locale.ROOT);
            Optional<PunishmentRecord> recordById = punishmentService.findById(id);
            if (recordById.isEmpty() || recordById.get().type() != PunishmentType.MUTE) {
                messageService.sendActionNotFound(invocation.source());
                return;
            }
            PunishmentRecord record = recordById.get();
            changed = punishmentService.unmuteById(id);
            targetDisplay = displayTarget(record, id);
            unmutedRecord = Optional.of(record);
        } else {
            messageService.sendPlayerNotFound(invocation.source(), input);
            return;
        }

        if (!changed) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        punishmentService.sendUnmuteWebhook(targetDisplay, invocation.source());
        unmutedRecord.ifPresent(punishmentService::notifyPlayerUnmuted);
        messageService.sendActionUpdated(invocation.source(), targetDisplay);
    }

    private void runVoid(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_VOID);
            return;
        }

        String targetId = args[0].toUpperCase(Locale.ROOT);
        Optional<PunishmentRecord> record = punishmentService.findById(targetId);
        if (record.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        if (!punishmentService.voidAction(targetId)) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }

        String executorName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        punishmentService.sendVoidWebhook(targetId, invocation.source(), punishmentService.isIpPunishment(record.get()));
        punishmentService.notifyPlayerWarnRemoved(record.get());
        messageService.sendActionUpdated(invocation.source(), targetId);
        messageService.broadcastVoid(targetId, targetId, executorName);
    }

    private List<String> suggestVoid(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 1) {
            return List.of();
        }
        return punishmentRepository.findRecentIdsByPrefix(currentToken(invocation), DEFAULT_COMPLETION_LIMIT);
    }

    private void runCheck(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1 || normalizeToken(args[0]).isEmpty()) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_CHECK);
            return;
        }

        String targetId = args[0].toUpperCase(Locale.ROOT);
        Optional<PunishmentRecord> record = punishmentService.findById(targetId);
        if (record.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        sendPunishmentDetail(invocation.source(), record.get());
    }

    private void runCheckPlayer(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_CHECKPLAYER);
            return;
        }

        Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(args[0]);
        if (targetMaybe.isEmpty()) {
            messageService.sendPlayerNotFound(invocation.source(), args[0]);
            return;
        }

        TargetProfile target = targetMaybe.get();
        List<PunishmentRecord> active = punishmentRepository.activeByTarget(target.uuid(), target.username());
        messageService.send(invocation.source(), "<gray>Active punishments for <target>:</gray>", Map.of("target", target.username()));
        if (active.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        for (PunishmentRecord punishment : active) {
            sendPunishmentSummary(invocation.source(), punishment, false);
        }
    }

    private void runCheckIp(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_CHECKIP);
            return;
        }

        String input = args[0];
        String ip = input;
        if (!NetworkUtil.isIpLiteral(input)) {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(input);
            if (targetMaybe.isEmpty()) {
                messageService.sendPlayerNotFound(invocation.source(), input);
                return;
            }
            TargetProfile target = targetMaybe.get();
            if (target.ip() == null || target.ip().isBlank()) {
                messageService.sendActionNotFound(invocation.source());
                return;
            }
            ip = target.ip();
        }

        List<PunishmentRecord> active = punishmentRepository.activeByIp(ip);
        messageService.send(invocation.source(), "<gray>Active punishments for IP <ip>:</gray>", Map.of("ip", ip));
        if (active.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        for (PunishmentRecord punishment : active) {
            sendPunishmentSummary(invocation.source(), punishment, true);
        }
    }

    private List<String> suggestCheck(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 1) {
            return List.of();
        }
        return punishmentRepository.findRecentIdsByPrefix(currentToken(invocation), DEFAULT_COMPLETION_LIMIT);
    }

    private void runHistory(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_HISTORY);
            return;
        }

        Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(args[0]);
        if (targetMaybe.isEmpty()) {
            messageService.sendPlayerNotFound(invocation.source(), args[0]);
            return;
        }
        TargetProfile target = targetMaybe.get();
        List<PunishmentRecord> history = punishmentRepository.historyByTarget(target.uuid(), target.username());
        messageService.sendHistoryHeader(invocation.source(), target.username());
        if (history.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        for (PunishmentRecord punishment : history) {
            messageService.sendHistoryEntry(
                    invocation.source(),
                    punishment.id(),
                    punishment.type().name(),
                    punishment.reason(),
                    Boolean.toString(punishment.voided())
            );
        }
    }

    private void runAlts(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_ALTS);
            return;
        }
        Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(args[0]);
        if (targetMaybe.isEmpty()) {
            messageService.sendPlayerNotFound(invocation.source(), args[0]);
            return;
        }
        TargetProfile target = targetMaybe.get();
        if (target.ip() == null || target.ip().isBlank()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }

        List<String> usernames = playerRepository.findUsernamesByIp(target.ip());
        messageService.sendAltsHeader(invocation.source(), target.ip());
        for (String username : usernames) {
            messageService.sendAltsEntry(invocation.source(), username);
        }
    }

    private void runIpHistory(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messageService.sendRawError(invocation.source(), "Usage: " + USAGE_IPHISTORY);
            return;
        }
        String input = args[0];
        if (NetworkUtil.isIpLiteral(input)) {
            List<AccountVisit> accounts = playerRepository.findAccountsByIp(input);
            messageService.sendIpHistoryHeader(invocation.source(), input);
            if (accounts.isEmpty()) {
                messageService.sendActionNotFound(invocation.source());
                return;
            }
            for (AccountVisit account : accounts) {
                messageService.send(
                        invocation.source(),
                        "<gray>-</gray> <white><account></white> <dark_gray>(<seen>)</dark_gray>",
                        Map.of("account", account.account(), "seen", account.lastSeen().toString())
                );
            }
            return;
        }

        Optional<TargetProfile> targetMaybe = targetResolver.resolvePlayer(input);
        if (targetMaybe.isEmpty()) {
            messageService.sendPlayerNotFound(invocation.source(), input);
            return;
        }
        TargetProfile target = targetMaybe.get();
        messageService.sendIpHistoryHeader(invocation.source(), target.username());
        List<IpVisit> visits = playerRepository.findIpHistoryByUuid(target.uuid());
        if (visits.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        for (IpVisit visit : visits) {
            messageService.sendIpHistoryEntry(invocation.source(), visit.ip(), visit.lastSeen().toString());
        }
    }

    private void runFluxRoot(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendSafeVersion(invocation.source());
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("ver") || sub.equals("info") || sub.equals("version")) {
            if (args.length != 1) {
                messageService.sendRawError(invocation.source(), "Usage: " + USAGE_FLUX);
                return;
            }
            sendSafeVersion(invocation.source());
            return;
        }

        if (sub.equals("reload")) {
            if (args.length != 1) {
                messageService.sendRawError(invocation.source(), "Usage: " + USAGE_FLUX);
                return;
            }
            if (!permissionService.has(invocation.source(), "flux.command.reload")) {
                messageService.sendNoPermission(invocation.source());
                return;
            }
            boolean refreshed = reloadHandler.reload();
            messageService.sendReloadResult(invocation.source(), refreshed);
            return;
        }

        messageService.sendRawError(invocation.source(), "Usage: " + USAGE_FLUX);
    }

    private List<String> suggestFluxRoot(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return completeByPrefix(List.of("reload", "ver", "info", "version"), currentToken(invocation));
        }
        if ("reload".equalsIgnoreCase(tokenAt(invocation, 0))) {
            return List.of();
        }
        return List.of();
    }

    private List<String> suggestBanLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, true, false);
    }

    private List<String> suggestMuteLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, true, false);
    }

    private List<String> suggestWarnLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, false, false);
    }

    private List<String> suggestKickLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, false, false);
    }

    private List<String> suggestIpBan(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, true, true);
    }

    private List<String> suggestUnban(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, mergeCompletionSources(
                usernameOrIpTargetCompletionSource(0),
                punishmentIdCompletionSource(PunishmentType.BAN, 0)
        ));
    }

    private List<String> suggestUnmute(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, mergeCompletionSources(
                onlineUsernameCompletionSource(0),
                punishmentIdCompletionSource(PunishmentType.MUTE, 0)
        ));
    }

    private List<String> suggestHistory(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, onlineUsernameCompletionSource(0));
    }

    private List<String> suggestCheckPlayer(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, onlineUsernameCompletionSource(0));
    }

    private List<String> suggestCheckIp(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, usernameOrIpTargetCompletionSource(0));
    }

    private List<String> suggestAlts(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, onlineUsernameCompletionSource(0));
    }

    private List<String> suggestIpHistory(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(invocation, usernameOrIpTargetCompletionSource(0));
    }

    private List<String> suggestPunishmentCommand(
            SimpleCommand.Invocation invocation,
            boolean allowDuration,
            boolean allowIpLikeTarget
    ) {
        CommandSuggester targetSource = allowIpLikeTarget
                ? usernameOrIpTargetCompletionSource(0)
                : onlineUsernameCompletionSource(0);
        CommandSuggester payloadStartSource = allowDuration
                ? mergeCompletionSources(durationTokenCompletionSource(1), templateCompletionSource(1))
                : templateCompletionSource(1);
        CommandSuggester payloadAfterDurationSource = invocationCandidate -> {
            if (!allowDuration || !DurationParser.isDurationToken(tokenAt(invocationCandidate, 1))) {
                return List.of();
            }
            return suggestTemplates(invocationCandidate, 2);
        };
        return argumentPositionCompletionFramework(
                invocation,
                targetSource,
                payloadStartSource,
                payloadAfterDurationSource
        );
    }

    private List<String> argumentPositionCompletionFramework(
            SimpleCommand.Invocation invocation,
            CommandSuggester... argumentSources
    ) {
        int argumentIndex = currentArgumentIndex(invocation);
        if (argumentIndex < 0 || argumentIndex >= argumentSources.length) {
            return List.of();
        }
        CommandSuggester source = argumentSources[argumentIndex];
        if (source == null) {
            return List.of();
        }
        return source.suggest(invocation);
    }

    private CommandSuggester mergeCompletionSources(CommandSuggester... suggesters) {
        return invocation -> {
            if (suggesters == null || suggesters.length == 0) {
                return List.of();
            }
            List<String> merged = new ArrayList<>();
            for (CommandSuggester suggester : suggesters) {
                if (suggester == null) {
                    continue;
                }
                List<String> suggestions = suggester.suggest(invocation);
                if (suggestions == null || suggestions.isEmpty()) {
                    continue;
                }
                merged.addAll(suggestions);
            }
            return merged;
        };
    }

    private CommandSuggester durationTokenCompletionSource(int argumentIndex) {
        return invocation -> completeByPrefix(DURATION_TOKEN_SUGGESTIONS, tokenAt(invocation, argumentIndex));
    }

    private CommandSuggester onlineUsernameCompletionSource(int argumentIndex) {
        return invocation -> suggestOnlineUsernames(invocation, argumentIndex);
    }

    private CommandSuggester usernameOrIpTargetCompletionSource(int argumentIndex) {
        return invocation -> {
            String token = tokenAt(invocation, argumentIndex);
            if (looksLikeIpToken(token)) {
                return List.of();
            }
            return suggestOnlineUsernamesByPrefix(token);
        };
    }

    private CommandSuggester templateCompletionSource(int argumentIndex) {
        return invocation -> suggestTemplates(invocation, argumentIndex);
    }

    private CommandSuggester punishmentIdCompletionSource(PunishmentType type, int argumentIndex) {
        return invocation -> punishmentRepository.findRecentActiveIdsByTypePrefix(
                type,
                tokenAt(invocation, argumentIndex),
                DEFAULT_COMPLETION_LIMIT
        );
    }

    private List<String> suggestTemplates(SimpleCommand.Invocation invocation, int argumentIndex) {
        return suggestTemplatesByPrefix(invocation.source(), tokenAt(invocation, argumentIndex));
    }

    private List<String> suggestTemplatesByPrefix(CommandSource source, String prefix) {
        return completeByPrefix(templateSuggestions(source), prefix);
    }

    private List<String> templateSuggestions(CommandSource source) {
        Map<String, TemplatesConfig.TemplateDefinition> templates = templateService.allTemplates();
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }

        boolean hasWildcardTemplatePermission = permissionService.has(source, "flux.template.*");
        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, TemplatesConfig.TemplateDefinition> entry : templates.entrySet()) {
            String templateName = normalizeToken(entry.getKey());
            if (templateName.isEmpty()) {
                continue;
            }
            if (!hasWildcardTemplatePermission && !hasTemplatePermission(source, entry.getValue())) {
                continue;
            }
            suggestions.add("#" + templateName);
        }
        return suggestions;
    }

    private boolean hasTemplatePermission(CommandSource source, TemplatesConfig.TemplateDefinition definition) {
        if (definition == null) {
            return false;
        }
        String permissionNode = normalizeToken(definition.permission);
        if (permissionNode.isEmpty()) {
            return false;
        }
        return permissionService.has(source, permissionNode);
    }

    private List<String> suggestOnlineUsernames(SimpleCommand.Invocation invocation, int argumentIndex) {
        return suggestOnlineUsernamesByPrefix(tokenAt(invocation, argumentIndex));
    }

    private List<String> suggestOnlineUsernamesByPrefix(String prefix) {
        return completeByPrefix(onlineUsernames(), prefix);
    }

    private List<String> onlineUsernames() {
        List<String> usernames = new ArrayList<>();
        for (Player onlinePlayer : server.getAllPlayers()) {
            usernames.add(onlinePlayer.getUsername());
        }
        return usernames;
    }

    private static List<String> sanitizeSuggestions(Iterable<String> suggestions, int limit) {
        return completeByPrefix(suggestions, "", limit);
    }

    private static List<String> completeByPrefix(Iterable<String> candidates, String prefix) {
        return completeByPrefix(candidates, prefix, DEFAULT_COMPLETION_LIMIT);
    }

    private static List<String> completeByPrefix(Iterable<String> candidates, String prefix, int limit) {
        if (candidates == null) {
            return List.of();
        }

        int cappedLimit = Math.max(0, limit);
        if (cappedLimit == 0) {
            return List.of();
        }

        String normalizedPrefix = normalizeToken(prefix).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> suggestions = new ArrayList<>();
        for (String candidate : candidates) {
            String normalized = normalizeToken(candidate);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!normalizedPrefix.isEmpty() && !normalized.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                continue;
            }
            if (!seen.add(normalized)) {
                continue;
            }
            suggestions.add(normalized);
            if (suggestions.size() >= cappedLimit) {
                break;
            }
        }
        return suggestions;
    }

    private static String currentToken(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return "";
        }
        return normalizeToken(args[args.length - 1]);
    }

    private static int currentArgumentIndex(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return 0;
        }
        return args.length - 1;
    }

    private static String tokenAt(SimpleCommand.Invocation invocation, int index) {
        String[] args = invocation.arguments();
        if (index < 0 || index >= args.length) {
            return "";
        }
        return normalizeToken(args[index]);
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static boolean looksLikeIpToken(String value) {
        String token = normalizeToken(value);
        if (token.isEmpty()) {
            return false;
        }
        if (NetworkUtil.isIpLiteral(token)) {
            return true;
        }
        if (token.indexOf(':') >= 0) {
            return isIpv6LikeToken(token);
        }
        if (token.indexOf('.') >= 0) {
            return isIpv4LikeToken(token);
        }
        return isDigitsOnly(token);
    }

    private static boolean isIpv4LikeToken(String token) {
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character != '.' && !Character.isDigit(character)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6LikeToken(String token) {
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character == ':') {
                continue;
            }
            if (Character.digit(character, 16) == -1) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigitsOnly(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (!Character.isDigit(token.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikePunishmentIdToken(String value) {
        String token = normalizeToken(value);
        if (token.length() != 6) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (!Character.isLetterOrDigit(character)) {
                return false;
            }
        }
        return true;
    }

    private static String displayTarget(PunishmentRecord record, String fallback) {
        if (record == null) {
            return fallback;
        }
        String username = normalizeToken(record.targetUsername());
        if (!username.isEmpty()) {
            return username;
        }
        String ip = normalizeToken(record.targetIp());
        if (!ip.isEmpty()) {
            return ip;
        }
        String uuid = normalizeToken(record.targetUuid());
        if (!uuid.isEmpty()) {
            return uuid;
        }
        return fallback;
    }

    private static ParseResult parsePunishmentPayload(String[] payload, boolean allowDuration) {
        if (payload.length == 0) {
            return new ParseResult(null, "");
        }

        if (allowDuration && payload.length >= 2 && DurationParser.isDurationToken(payload[0])) {
            Duration duration = DurationParser.parse(payload[0]).orElse(null);
            return new ParseResult(duration, String.join(" ", Arrays.copyOfRange(payload, 1, payload.length)).trim());
        }

        return new ParseResult(null, String.join(" ", payload).trim());
    }

    private void sendSafeVersion(CommandSource source) {
        messageService.send(source, "<gray>Flux <white><version></white></gray>", Map.of("version", pluginVersion));
    }

    private void sendPunishmentDetail(CommandSource source, PunishmentRecord punishment) {
        String target = resolveTargetName(punishment);
        if (target == null || target.isBlank()) {
            target = "N/A";
        }
        String issuer = resolveExecutorName(punishment.executorUuid());
        String ip = punishment.targetIp() == null || punishment.targetIp().isBlank() ? "N/A" : punishment.targetIp();
        String expires = PunishmentTimeFormatter.formatExpiry(punishment.endTime());
        String duration = punishment.endTime() == null
                ? PunishmentTimeFormatter.PERMANENT_LABEL
                : PunishmentTimeFormatter.formatDuration(punishment.startTime(), punishment.endTime());
        String template = "N/A";
        if (punishment.metadata() != null) {
            String maybeTemplate = normalizeToken(punishment.metadata().get("template"));
            if (!maybeTemplate.isEmpty()) {
                template = maybeTemplate;
            }
        }

        messageService.send(source, "<gray>Punishment <white><id></white>:</gray>", Map.of("id", punishment.id()));
        messageService.send(source, "<gray>Type:</gray> <white><type></white>", Map.of("type", punishment.type().name()));
        messageService.send(source, "<gray>Target:</gray> <white><target></white> <dark_gray>(ip=<ip>)</dark_gray>", Map.of(
                "target", target,
                "ip", ip
        ));
        messageService.send(source, "<gray>Issuer:</gray> <white><issuer></white>", Map.of("issuer", issuer));
        messageService.send(source, "<gray>Reason:</gray> <white><reason></white>", Map.of("reason", punishment.reason()));
        messageService.send(source, "<gray>Started:</gray> <white><started></white>", Map.of(
                "started",
                PunishmentTimeFormatter.formatTimestamp(punishment.startTime())
        ));
        messageService.send(source, "<gray>Duration:</gray> <white><duration></white>", Map.of("duration", duration));
        messageService.send(source, "<gray>Expires:</gray> <white><expires></white>", Map.of("expires", expires));
        messageService.send(source, "<gray>Active:</gray> <white><active></white> <gray>| Voided:</gray> <white><voided></white>", Map.of(
                "active", Boolean.toString(punishment.active()),
                "voided", Boolean.toString(punishment.voided())
        ));
        messageService.send(source, "<gray>IP Punishment:</gray> <white><ip_punishment></white> <gray>| Template:</gray> <white><template></white>", Map.of(
                "ip_punishment", Boolean.toString(punishmentService.isIpPunishment(punishment)),
                "template", template
        ));
    }

    private void sendPunishmentSummary(CommandSource source, PunishmentRecord punishment, boolean includeTarget) {
        String expires = PunishmentTimeFormatter.formatExpiry(punishment.endTime());
        if (!includeTarget) {
            messageService.send(source, "<gray>-</gray> <white><id></white> <yellow><type></yellow> <gray><reason></gray> <dark_gray>(expires=<expires>)</dark_gray>", Map.of(
                    "id", punishment.id(),
                    "type", punishment.type().name(),
                    "reason", punishment.reason(),
                    "expires", expires
            ));
            return;
        }

        String target = resolveTargetName(punishment);
        if (target == null || target.isBlank()) {
            target = "N/A";
        }
        messageService.send(source, "<gray>-</gray> <white><id></white> <yellow><type></yellow> <gray><reason></gray> <dark_gray>(target=<target>, expires=<expires>)</dark_gray>", Map.of(
                "id", punishment.id(),
                "type", punishment.type().name(),
                "reason", punishment.reason(),
                "target", target,
                "expires", expires
        ));
    }

    private Optional<String> resolveUsername(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        return playerRepository.findByUuid(uuid).map(player -> player.username());
    }

    private String resolveTargetName(PunishmentRecord punishment) {
        String recordedName = normalizeToken(punishment.targetUsername());
        if (!recordedName.isEmpty()) {
            return recordedName;
        }
        return resolveUsername(punishment.targetUuid()).orElse(punishment.targetUuid());
    }

    private String resolveExecutorName(String executorUuid) {
        if (executorUuid == null || executorUuid.isBlank() || CONSOLE_UUID.equals(executorUuid)) {
            return "Console";
        }
        return resolveUsername(executorUuid).orElse(executorUuid);
    }

    @FunctionalInterface
    private interface CommandExecutor {
        void execute(SimpleCommand.Invocation invocation);
    }

    @FunctionalInterface
    private interface CommandSuggester {
        List<String> suggest(SimpleCommand.Invocation invocation);
    }

    private record ParseResult(
            Duration duration,
            String reason
    ) {
    }
}
