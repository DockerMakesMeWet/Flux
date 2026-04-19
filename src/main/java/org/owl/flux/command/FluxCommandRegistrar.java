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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.owl.flux.config.model.TemplatesConfig;
import org.owl.flux.data.model.AccountVisit;
import org.owl.flux.data.model.IpVisit;
import org.owl.flux.data.model.ModerationActionRecord;
import org.owl.flux.data.model.ModerationActionType;
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
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String CONSOLE_UUID = "00000000-0000-0000-0000-000000000000";
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
        private static final List<String> PAGE_TOKEN_SUGGESTIONS = List.of("1", "2", "3", "4", "5", "10");

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
        register("voidall", "flux.command.voidall", this::runVoidAll, this::suggestVoidAll);
        register("check", "flux.command.check", this::runCheck, this::suggestCheck);
        register("checkplayer", "flux.command.checkplayer", this::runCheckPlayer, this::suggestCheckPlayer);
        register("checkip", "flux.command.checkip", this::runCheckIp, this::suggestCheckIp);
        register("checkwarns", "flux.command.checkwarns", this::runCheckWarns, this::suggestCheckWarns);
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
        runPlayerPunishment(invocation, PunishmentType.BAN, true, messageService.usageBan());
    }

    private void runMuteLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.MUTE, true, messageService.usageMute());
    }

    private void runWarnLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.WARN, false, messageService.usageWarn());
    }

    private void runKickLike(SimpleCommand.Invocation invocation) {
        runPlayerPunishment(invocation, PunishmentType.KICK, false, messageService.usageKick());
    }

    private void runPlayerPunishment(
            SimpleCommand.Invocation invocation,
            PunishmentType baseType,
            boolean allowDuration,
            String usage
    ) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendUsage(invocation.source(), usage);
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
            messageService.sendInvalidDuration(invocation.source());
            return;
        }
        if (parseResult.reason().isBlank()) {
            messageService.sendUsage(invocation.source(), usage);
            return;
        }

        PunishmentType appliedType = baseType;
        Duration duration = parseResult.duration();
        String reason = parseResult.reason();
        String templateName = null;
        Integer templateOffenseStep = null;
        String templateOffenseLabel = null;

        if (reason.startsWith("#")) {
            try {
                TemplateResolution resolution = templateService.resolve(invocation.source(), reason, target.uuid());
                if (resolution.type() != baseType) {
                    messageService.sendTemplateTypeMismatch(
                            invocation.source(),
                            baseType.name(),
                            resolution.type().name()
                    );
                    return;
                }
                duration = resolution.optionalDuration().orElse(null);
                reason = resolution.reason();
                templateName = resolution.templateName();
                templateOffenseStep = resolution.offenseStep();
                templateOffenseLabel = resolution.offenseLabel();
            } catch (IllegalArgumentException exception) {
                if ("template:no-permission".equals(exception.getMessage())) {
                    messageService.sendNoPermission(invocation.source());
                } else {
                    messageService.sendTemplateNotFound(invocation.source());
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
                templateOffenseStep,
                templateOffenseLabel,
                null,
                false
        ));

        String executorName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        messageService.sendActionCreated(invocation.source(), appliedType.name(), target.username(), result.punishment().id());
        messageService.sendPunishmentSummaryHeader(invocation.source(), result.punishment().id());
        sendPunishmentDetail(invocation.source(), result.punishment());
        messageService.sendPunishmentSummaryFooter(invocation.source(), result.punishment().id());
        messageService.broadcastStaffAction(result.punishment(), executorName, target.username(), target.optionalOnlinePlayer().orElse(null));
    }

    private void runIpBan(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendUsage(invocation.source(), messageService.usageIpBan());
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
            messageService.sendUsage(invocation.source(), messageService.usageIpBan());
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
        messageService.sendPunishmentSummaryHeader(invocation.source(), result.punishment().id());
        sendPunishmentDetail(invocation.source(), result.punishment());
        messageService.sendPunishmentSummaryFooter(invocation.source(), result.punishment().id());
        messageService.broadcastStaffAction(result.punishment(), executorName, targetName, onlinePlayer);
    }

    private void runUnban(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendUsage(invocation.source(), messageService.usageUnban());
            return;
        }

        String input = args[0];
        String reason = normalizeToken(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (reason.isBlank()) {
            messageService.sendUsage(invocation.source(), messageService.usageUnban());
            return;
        }
        boolean changed = false;
        String targetDisplay = input;
        String auditTargetReference = input;
        boolean ipPunishment = false;
        Optional<PunishmentRecord> unbannedRecord = Optional.empty();
        String relatedPunishmentId = null;
        if (NetworkUtil.isIpLiteral(input)) {
            Optional<PunishmentRecord> activeBan = punishmentService.activeBan(null, null, input);
            changed = punishmentService.unbanByIp(input);
            ipPunishment = activeBan.map(punishmentService::isIpPunishment).orElse(false);
            unbannedRecord = activeBan;
            relatedPunishmentId = activeBan.map(PunishmentRecord::id).orElse(null);
            auditTargetReference = input;
        } else {
            boolean resolvedById = false;
            if (looksLikePunishmentIdToken(input)) {
                String id = input.toUpperCase(Locale.ROOT);
                Optional<PunishmentRecord> recordById = punishmentService.findById(id);
                if (recordById.isPresent() && recordById.get().type() == PunishmentType.BAN) {
                    PunishmentRecord record = recordById.get();
                    changed = punishmentService.unbanById(id);
                    targetDisplay = displayTarget(record, id);
                    auditTargetReference = auditTargetReference(record, id);
                    ipPunishment = punishmentService.isIpPunishment(record);
                    unbannedRecord = Optional.of(record);
                    relatedPunishmentId = id;
                    resolvedById = true;
                }
            }

            if (!resolvedById) {
                Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(input);
                if (targetMaybe.isEmpty()) {
                    messageService.sendPlayerNotFound(invocation.source(), input);
                    return;
                }

                TargetProfile target = targetMaybe.get();
                Optional<PunishmentRecord> activeBan = punishmentService.activeBan(target.uuid(), target.username(), target.ip());
                changed = punishmentService.unbanByTarget(target.uuid(), target.username());
                targetDisplay = target.username();
                auditTargetReference = target.username();
                ipPunishment = activeBan.map(punishmentService::isIpPunishment).orElse(false);
                unbannedRecord = activeBan;
                relatedPunishmentId = activeBan.map(PunishmentRecord::id).orElse(null);
            }
        }

        if (!changed) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        punishmentService.auditReversalAction(
                ModerationActionType.UNBAN,
            auditTargetReference,
                relatedPunishmentId,
                invocation.source(),
                reason
        );
            punishmentService.sendUnbanWebhook(
                targetDisplay,
                invocation.source(),
                ipPunishment,
                reason,
                unbannedRecord.orElse(null)
            );
        messageService.sendActionUpdated(invocation.source(), targetDisplay);
    }

    private void runUnmute(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendUsage(invocation.source(), messageService.usageUnmute());
            return;
        }

        String input = args[0];
        String reason = normalizeToken(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (reason.isBlank()) {
            messageService.sendUsage(invocation.source(), messageService.usageUnmute());
            return;
        }
        boolean changed = false;
        String targetDisplay = input;
        String auditTargetReference = input;
        Optional<PunishmentRecord> unmutedRecord = Optional.empty();
        String relatedPunishmentId = null;

        boolean resolvedById = false;
        if (looksLikePunishmentIdToken(input)) {
            String id = input.toUpperCase(Locale.ROOT);
            Optional<PunishmentRecord> recordById = punishmentService.findById(id);
            if (recordById.isPresent() && recordById.get().type() == PunishmentType.MUTE) {
                PunishmentRecord record = recordById.get();
                changed = punishmentService.unmuteById(id);
                targetDisplay = displayTarget(record, id);
                auditTargetReference = auditTargetReference(record, id);
                unmutedRecord = Optional.of(record);
                relatedPunishmentId = id;
                resolvedById = true;
            } else {
                changed = false;
            }
        } else {
            changed = false;
        }

        if (!resolvedById) {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(input);
            if (targetMaybe.isEmpty()) {
                messageService.sendPlayerNotFound(invocation.source(), input);
                return;
            }
            TargetProfile target = targetMaybe.get();
            Optional<PunishmentRecord> activeMute = punishmentService.activeMute(target.uuid(), target.username());
            unmutedRecord = activeMute == null ? Optional.empty() : activeMute;
            changed = punishmentService.unmuteByTarget(target.uuid(), target.username());
            targetDisplay = target.username();
            auditTargetReference = target.username();
            relatedPunishmentId = activeMute.map(PunishmentRecord::id).orElse(null);
        }

        if (!changed) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        punishmentService.auditReversalAction(
                ModerationActionType.UNMUTE,
            auditTargetReference,
                relatedPunishmentId,
                invocation.source(),
                reason
        );
            punishmentService.sendUnmuteWebhook(targetDisplay, invocation.source(), reason, unmutedRecord.orElse(null));
        unmutedRecord.ifPresent(punishmentService::notifyPlayerUnmuted);
        messageService.sendActionUpdated(invocation.source(), targetDisplay);
    }

    private void runVoid(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendUsage(invocation.source(), messageService.usageVoid());
            return;
        }

        String targetId = args[0].toUpperCase(Locale.ROOT);
        String reason = normalizeToken(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (reason.isBlank()) {
            messageService.sendUsage(invocation.source(), messageService.usageVoid());
            return;
        }
        Optional<PunishmentRecord> record = punishmentService.findById(targetId);
        if (record.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        if (!punishmentService.voidAction(targetId, reason)) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }

        String executorName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        String auditTargetReference = auditTargetReference(record.get(), targetId);
        punishmentService.auditReversalAction(
            ModerationActionType.VOID,
            auditTargetReference,
            targetId,
            invocation.source(),
            reason
        );
        punishmentService.sendVoidWebhook(targetId, invocation.source(), punishmentService.isIpPunishment(record.get()), reason, record.get());
        punishmentService.notifyPlayerWarnRemoved(record.get());
        messageService.sendVoidUpdated(invocation.source(), targetId);
        messageService.broadcastVoid(targetId, targetId, executorName, reason);
    }

    private void runVoidAll(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messageService.sendUsage(invocation.source(), messageService.usageVoidAll());
            return;
        }

        String targetInput = normalizeToken(args[0]);
        String reason = normalizeToken(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (targetInput.isBlank() || reason.isBlank()) {
            messageService.sendUsage(invocation.source(), messageService.usageVoidAll());
            return;
        }

        List<PunishmentRecord> candidates;
        String targetDisplay;
        String auditFallback;
        if (NetworkUtil.isIpLiteral(targetInput)) {
            candidates = punishmentRepository.voidAllCandidatesByIp(targetInput);
            targetDisplay = targetInput;
            auditFallback = targetInput;
        } else {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(targetInput);
            if (targetMaybe.isEmpty()) {
                messageService.sendPlayerNotFound(invocation.source(), targetInput);
                return;
            }
            TargetProfile target = targetMaybe.get();
            candidates = punishmentRepository.voidAllCandidatesByTarget(target.uuid(), target.username());
            targetDisplay = target.username();
            auditFallback = target.username();
        }

        if (candidates == null) {
            candidates = List.of();
        }

        if (candidates.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }

        String executorName = invocation.source() instanceof Player player ? player.getUsername() : "Console";
        int voidedCount = 0;
        for (PunishmentRecord record : candidates) {
            if (!punishmentService.voidAction(record.id(), reason)) {
                continue;
            }

            punishmentService.auditReversalAction(
                    ModerationActionType.VOID,
                    auditTargetReference(record, auditFallback),
                    record.id(),
                    invocation.source(),
                    reason
            );
            punishmentService.sendVoidWebhook(
                    record.id(),
                    invocation.source(),
                    punishmentService.isIpPunishment(record),
                    reason,
                    record
            );
            punishmentService.notifyPlayerWarnRemoved(record);
            messageService.broadcastVoid(record.id(), record.id(), executorName, reason);
            voidedCount++;
        }

        if (voidedCount <= 0) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        messageService.sendVoidAllUpdated(invocation.source(), targetDisplay, voidedCount);
    }

    private List<String> suggestVoid(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 2) {
            return List.of();
        }
        if (invocation.arguments().length == 2) {
            return List.of();
        }
        return punishmentRepository.findRecentIdsByPrefix(currentToken(invocation), DEFAULT_COMPLETION_LIMIT);
    }

    private List<String> suggestVoidAll(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 2) {
            return List.of();
        }
        if (invocation.arguments().length == 2) {
            return List.of();
        }
        return argumentPositionCompletionFramework(invocation, usernameOrIpTargetCompletionSource(0));
    }

    private void runCheck(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 1 || normalizeToken(args[0]).isEmpty()) {
            messageService.sendUsage(invocation.source(), messageService.usageCheck());
            return;
        }

        String targetId = args[0].toUpperCase(Locale.ROOT);
        Optional<PunishmentRecord> record = punishmentService.findById(targetId);
        if (record.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            return;
        }
        messageService.sendCheckHeader(invocation.source(), targetId);
        sendPunishmentDetail(invocation.source(), record.get());
        messageService.sendCheckFooter(invocation.source(), targetId);
    }

    private void runCheckPlayer(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1 || args.length > 2) {
            messageService.sendUsage(invocation.source(), messageService.usageCheckPlayer());
            return;
        }
        int requestedPage = parseRequestedPage(invocation, args, 1);
        if (requestedPage < 0) {
            return;
        }

        Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(args[0]);
        if (targetMaybe.isEmpty()) {
            messageService.sendPlayerNotFound(invocation.source(), args[0]);
            return;
        }

        TargetProfile target = targetMaybe.get();
        List<PunishmentRecord> active = punishmentRepository.activeByTarget(target.uuid(), target.username());
        PageWindow window = paginationWindow(active.size(), requestedPage);
        messageService.sendCheckPlayerHeader(invocation.source(), target.username());
        if (active.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                    commandBase("checkplayer", target.username())
            );
            return;
        }
        for (PunishmentRecord punishment : active.subList(window.fromIndex(), window.toIndex())) {
            sendPunishmentSummary(invocation.source(), punishment, false);
        }
        messageService.sendPaginationFooter(
                invocation.source(),
                window.currentPage(),
                window.totalPages(),
                commandBase("checkplayer", target.username())
        );
    }

    private void runCheckIp(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1 || args.length > 2) {
            messageService.sendUsage(invocation.source(), messageService.usageCheckIp());
            return;
        }
        int requestedPage = parseRequestedPage(invocation, args, 1);
        if (requestedPage < 0) {
            return;
        }

        String input = normalizeToken(args[0]);
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
        PageWindow window = paginationWindow(active.size(), requestedPage);
        messageService.sendCheckIpHeader(invocation.source(), ip);
        if (active.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                    commandBase("checkip", input)
            );
            return;
        }
        for (PunishmentRecord punishment : active.subList(window.fromIndex(), window.toIndex())) {
            sendPunishmentSummary(invocation.source(), punishment, true);
        }
        messageService.sendPaginationFooter(
                invocation.source(),
                window.currentPage(),
                window.totalPages(),
                commandBase("checkip", input)
        );
    }

    private void runCheckWarns(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1 || args.length > 2) {
            messageService.sendUsage(invocation.source(), messageService.usageCheckWarns());
            return;
        }
        int requestedPage = parseRequestedPage(invocation, args, 1);
        if (requestedPage < 0) {
            return;
        }

        String input = normalizeToken(args[0]);
        List<PunishmentRecord> warnCandidates;
        String targetDisplay;
        String commandTarget;
        boolean includeTargetInEntry = false;
        if (NetworkUtil.isIpLiteral(input)) {
            warnCandidates = punishmentRepository.historyByIp(input);
            targetDisplay = input;
            commandTarget = input;
            includeTargetInEntry = true;
        } else {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(input);
            if (targetMaybe.isEmpty()) {
                messageService.sendPlayerNotFound(invocation.source(), input);
                return;
            }
            TargetProfile target = targetMaybe.get();
            warnCandidates = punishmentRepository.historyByTarget(target.uuid(), target.username());
            targetDisplay = target.username();
            commandTarget = target.username();
        }

        if (warnCandidates == null) {
            warnCandidates = List.of();
        }
        List<PunishmentRecord> warns = warnCandidates.stream()
                .filter(punishment -> punishment.type() == PunishmentType.WARN)
                .filter(punishment -> !punishment.voided())
                .toList();

        PageWindow window = paginationWindow(warns.size(), requestedPage);
        messageService.sendCheckWarnsHeader(invocation.source(), targetDisplay);
        if (warns.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                    commandBase("checkwarns", commandTarget)
            );
            return;
        }

        for (PunishmentRecord warn : warns.subList(window.fromIndex(), window.toIndex())) {
            sendPunishmentSummary(invocation.source(), warn, includeTargetInEntry);
        }
        messageService.sendPaginationFooter(
                invocation.source(),
                window.currentPage(),
                window.totalPages(),
                commandBase("checkwarns", commandTarget)
        );
    }

    private List<String> suggestCheck(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 1) {
            return List.of();
        }
        return punishmentRepository.findRecentIdsByPrefix(currentToken(invocation), DEFAULT_COMPLETION_LIMIT);
    }

    private void runHistory(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1 || args.length > 2) {
            messageService.sendUsage(invocation.source(), messageService.usageHistory());
            return;
        }
        int requestedPage = parseRequestedPage(invocation, args, 1);
        if (requestedPage < 0) {
            return;
        }

        String input = normalizeToken(args[0]);
        List<PunishmentRecord> punishmentHistory;
        List<ModerationActionRecord> moderationHistory;
        String targetLabel;
        String commandTarget;
        if (NetworkUtil.isIpLiteral(input)) {
            punishmentHistory = punishmentRepository.historyByIp(input);
            moderationHistory = punishmentService.historyActionsByTarget(null, input);
            targetLabel = input;
            commandTarget = input;
        } else {
            Optional<TargetProfile> targetMaybe = targetResolver.resolvePunishmentTarget(input);
            if (targetMaybe.isEmpty()) {
                messageService.sendPlayerNotFound(invocation.source(), args[0]);
                return;
            }
            TargetProfile target = targetMaybe.get();
            punishmentHistory = punishmentRepository.historyByTarget(target.uuid(), target.username());
            moderationHistory = punishmentService.historyActionsByTarget(target.uuid(), target.username());
            targetLabel = target.username();
            commandTarget = target.username();
        }

        if (punishmentHistory == null) {
            punishmentHistory = List.of();
        }
        if (moderationHistory == null) {
            moderationHistory = List.of();
        }
        List<HistoryEntry> mergedHistory = new ArrayList<>(punishmentHistory.size() + moderationHistory.size());
        for (PunishmentRecord punishment : punishmentHistory) {
            mergedHistory.add(HistoryEntry.fromPunishment(punishment));
        }
        for (ModerationActionRecord action : moderationHistory) {
            mergedHistory.add(HistoryEntry.fromModerationAction(action));
        }
        mergedHistory.sort(Comparator
                .comparing(HistoryEntry::when, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(HistoryEntry::id, Comparator.reverseOrder()));

        PageWindow window = paginationWindow(mergedHistory.size(), requestedPage);
        messageService.sendHistoryHeader(invocation.source(), targetLabel);
        if (mergedHistory.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                commandBase("history", commandTarget)
            );
            return;
        }
        for (HistoryEntry historyEntry : mergedHistory.subList(window.fromIndex(), window.toIndex())) {
            messageService.sendHistoryEntry(
                    invocation.source(),
                    historyEntry.id(),
                    historyEntry.type(),
                    historyEntry.reason()
            );
        }
        messageService.sendPaginationFooter(
                invocation.source(),
                window.currentPage(),
                window.totalPages(),
            commandBase("history", commandTarget)
        );
    }

    private void runAlts(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1 || args.length > 2) {
            messageService.sendUsage(invocation.source(), messageService.usageAlts());
            return;
        }
        int requestedPage = parseRequestedPage(invocation, args, 1);
        if (requestedPage < 0) {
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
        PageWindow window = paginationWindow(usernames.size(), requestedPage);
        messageService.sendAltsHeader(invocation.source(), target.ip());
        if (usernames.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                    commandBase("alts", target.username())
            );
            return;
        }
        for (String username : usernames.subList(window.fromIndex(), window.toIndex())) {
            messageService.sendAltsEntry(invocation.source(), username);
        }
        messageService.sendPaginationFooter(
                invocation.source(),
                window.currentPage(),
                window.totalPages(),
                commandBase("alts", target.username())
        );
    }

    private void runIpHistory(SimpleCommand.Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1 || args.length > 2) {
            messageService.sendUsage(invocation.source(), messageService.usageIpHistory());
            return;
        }
        int requestedPage = parseRequestedPage(invocation, args, 1);
        if (requestedPage < 0) {
            return;
        }
        String input = args[0];
        if (NetworkUtil.isIpLiteral(input)) {
            List<AccountVisit> accounts = playerRepository.findAccountsByIp(input);
            PageWindow window = paginationWindow(accounts.size(), requestedPage);
            messageService.sendIpHistoryHeader(invocation.source(), input);
            if (accounts.isEmpty()) {
                messageService.sendActionNotFound(invocation.source());
                messageService.sendPaginationFooter(
                        invocation.source(),
                        window.currentPage(),
                        window.totalPages(),
                        commandBase("iphistory", input)
                );
                return;
            }
            for (AccountVisit account : accounts.subList(window.fromIndex(), window.toIndex())) {
                messageService.sendIpHistoryAccountEntry(
                        invocation.source(),
                        account.account(),
                        PunishmentTimeFormatter.formatTimestamp(account.lastSeen())
                );
            }
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                    commandBase("iphistory", input)
            );
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
        PageWindow window = paginationWindow(visits.size(), requestedPage);
        if (visits.isEmpty()) {
            messageService.sendActionNotFound(invocation.source());
            messageService.sendPaginationFooter(
                    invocation.source(),
                    window.currentPage(),
                    window.totalPages(),
                    commandBase("iphistory", target.username())
            );
            return;
        }
        for (IpVisit visit : visits.subList(window.fromIndex(), window.toIndex())) {
            messageService.sendIpHistoryEntry(
                    invocation.source(),
                    visit.ip(),
                    PunishmentTimeFormatter.formatTimestamp(visit.lastSeen())
            );
        }
        messageService.sendPaginationFooter(
                invocation.source(),
                window.currentPage(),
                window.totalPages(),
                commandBase("iphistory", target.username())
        );
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
                messageService.sendUsage(invocation.source(), messageService.usageFlux());
                return;
            }
            sendSafeVersion(invocation.source());
            return;
        }

        if (sub.equals("reload")) {
            if (args.length != 1) {
                messageService.sendUsage(invocation.source(), messageService.usageFlux());
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

        messageService.sendUsage(invocation.source(), messageService.usageFlux());
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
        return suggestPunishmentCommand(invocation, true, false, PunishmentType.BAN);
    }

    private List<String> suggestMuteLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, true, false, PunishmentType.MUTE);
    }

    private List<String> suggestWarnLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, false, false, PunishmentType.WARN);
    }

    private List<String> suggestKickLike(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, false, false, PunishmentType.KICK);
    }

    private List<String> suggestIpBan(SimpleCommand.Invocation invocation) {
        return suggestPunishmentCommand(invocation, true, true, PunishmentType.BAN);
    }

    private List<String> suggestUnban(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 2) {
            return List.of();
        }
        if (invocation.arguments().length == 2) {
            return List.of();
        }
        return argumentPositionCompletionFramework(invocation, mergeCompletionSources(
                usernameOrIpTargetCompletionSource(0),
                punishmentIdCompletionSource(PunishmentType.BAN, 0)
        ));
    }

    private List<String> suggestUnmute(SimpleCommand.Invocation invocation) {
        if (invocation.arguments().length > 2) {
            return List.of();
        }
        if (invocation.arguments().length == 2) {
            return List.of();
        }
        return argumentPositionCompletionFramework(invocation, mergeCompletionSources(
                onlineUsernameCompletionSource(0),
                punishmentIdCompletionSource(PunishmentType.MUTE, 0)
        ));
    }

    private List<String> suggestHistory(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(
            invocation,
            usernameOrIpTargetCompletionSource(0),
            pageNumberCompletionSource(1)
        );
    }

    private List<String> suggestCheckPlayer(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(
            invocation,
            onlineUsernameCompletionSource(0),
            pageNumberCompletionSource(1)
        );
    }

    private List<String> suggestCheckIp(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(
                invocation,
                usernameOrIpTargetCompletionSource(0),
                pageNumberCompletionSource(1)
        );
    }

    private List<String> suggestCheckWarns(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(
                invocation,
                usernameOrIpTargetCompletionSource(0),
                pageNumberCompletionSource(1)
        );
    }

    private List<String> suggestAlts(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(
                invocation,
                onlineUsernameCompletionSource(0),
                pageNumberCompletionSource(1)
        );
    }

    private List<String> suggestIpHistory(SimpleCommand.Invocation invocation) {
        return argumentPositionCompletionFramework(
                invocation,
                usernameOrIpTargetCompletionSource(0),
                pageNumberCompletionSource(1)
        );
    }

    private List<String> suggestPunishmentCommand(
            SimpleCommand.Invocation invocation,
            boolean allowDuration,
            boolean allowIpLikeTarget,
            PunishmentType templateType
    ) {
        CommandSuggester targetSource = allowIpLikeTarget
                ? usernameOrIpTargetCompletionSource(0)
                : onlineUsernameCompletionSource(0);
        CommandSuggester payloadStartSource = allowDuration
            ? mergeCompletionSources(durationTokenCompletionSource(1), templateCompletionSource(1, templateType))
            : templateCompletionSource(1, templateType);
        CommandSuggester payloadAfterDurationSource = invocationCandidate -> {
            if (!allowDuration || !DurationParser.isDurationToken(tokenAt(invocationCandidate, 1))) {
                return List.of();
            }
            return suggestTemplates(invocationCandidate, 2, templateType);
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

    private CommandSuggester pageNumberCompletionSource(int argumentIndex) {
        return invocation -> completeByPrefix(PAGE_TOKEN_SUGGESTIONS, tokenAt(invocation, argumentIndex));
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
        return templateCompletionSource(argumentIndex, null);
    }

    private CommandSuggester templateCompletionSource(int argumentIndex, PunishmentType typeFilter) {
        return invocation -> suggestTemplates(invocation, argumentIndex, typeFilter);
    }

    private CommandSuggester punishmentIdCompletionSource(PunishmentType type, int argumentIndex) {
        return invocation -> punishmentRepository.findRecentActiveIdsByTypePrefix(
                type,
                tokenAt(invocation, argumentIndex),
                DEFAULT_COMPLETION_LIMIT
        );
    }

    private List<String> suggestTemplates(SimpleCommand.Invocation invocation, int argumentIndex) {
        return suggestTemplates(invocation, argumentIndex, null);
    }

    private List<String> suggestTemplates(SimpleCommand.Invocation invocation, int argumentIndex, PunishmentType typeFilter) {
        return suggestTemplatesByPrefix(invocation.source(), tokenAt(invocation, argumentIndex), typeFilter);
    }

    private List<String> suggestTemplatesByPrefix(CommandSource source, String prefix) {
        return suggestTemplatesByPrefix(source, prefix, null);
    }

    private List<String> suggestTemplatesByPrefix(CommandSource source, String prefix, PunishmentType typeFilter) {
        return completeByPrefix(templateSuggestions(source, typeFilter), prefix);
    }

    private List<String> templateSuggestions(CommandSource source) {
        return templateSuggestions(source, null);
    }

    private List<String> templateSuggestions(CommandSource source, PunishmentType typeFilter) {
        Map<String, TemplatesConfig.TemplateDefinition> templates = templateService.allTemplates();
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }

        boolean hasWildcardTemplatePermission = permissionService.has(source, "flux.template.*");
        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, TemplatesConfig.TemplateDefinition> entry : templates.entrySet()) {
            PunishmentType templateType = templatePunishmentType(entry.getValue());
            if (templateType == null) {
                continue;
            }
            if (typeFilter != null && templateType != typeFilter) {
                continue;
            }
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

    private static PunishmentType templatePunishmentType(TemplatesConfig.TemplateDefinition definition) {
        if (definition == null) {
            return null;
        }
        String typeValue = normalizeToken(definition.type);
        if (typeValue.isEmpty()) {
            return null;
        }
        try {
            return PunishmentType.valueOf(typeValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    private static String auditTargetReference(PunishmentRecord record, String fallback) {
        if (record == null) {
            return fallback;
        }
        String username = normalizeToken(record.targetUsername());
        if (!username.isEmpty()) {
            return username;
        }
        String uuid = normalizeToken(record.targetUuid());
        if (!uuid.isEmpty()) {
            return uuid;
        }
        String ip = normalizeToken(record.targetIp());
        if (!ip.isEmpty()) {
            return ip;
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

    private int parseRequestedPage(SimpleCommand.Invocation invocation, String[] args, int pageArgumentIndex) {
        if (args.length <= pageArgumentIndex) {
            return 1;
        }
        String token = normalizeToken(args[pageArgumentIndex]);
        try {
            int parsed = Integer.parseInt(token);
            if (parsed <= 0) {
                messageService.sendInvalidPage(invocation.source(), token);
                return -1;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            messageService.sendInvalidPage(invocation.source(), token);
            return -1;
        }
    }

    private static PageWindow paginationWindow(int totalEntries, int requestedPage) {
        if (totalEntries <= 0) {
            return new PageWindow(1, 1, 0, 0);
        }
        int totalPages = (totalEntries + DEFAULT_PAGE_SIZE - 1) / DEFAULT_PAGE_SIZE;
        int currentPage = Math.max(1, Math.min(requestedPage, totalPages));
        int fromIndex = (currentPage - 1) * DEFAULT_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + DEFAULT_PAGE_SIZE, totalEntries);
        return new PageWindow(currentPage, totalPages, fromIndex, toIndex);
    }

    private static String commandBase(String command, String subject) {
        String normalizedCommand = normalizeToken(command);
        String normalizedSubject = normalizeToken(subject);
        if (normalizedSubject.isEmpty()) {
            return "/" + normalizedCommand;
        }
        return "/" + normalizedCommand + " " + normalizedSubject;
    }

    private void sendSafeVersion(CommandSource source) {
        messageService.sendVersion(source, pluginVersion);
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
        String templateStep = "N/A";
        if (punishment.metadata() != null) {
            String maybeTemplate = normalizeToken(punishment.metadata().get("template"));
            if (!maybeTemplate.isEmpty()) {
                template = maybeTemplate;
            }
            String maybeTemplateStep = normalizeToken(punishment.metadata().get("template_step"));
            if (!maybeTemplateStep.isEmpty()) {
                templateStep = maybeTemplateStep;
            }
        }

        messageService.sendCheckDetailType(source, punishment.type().name());
        messageService.sendCheckDetailTarget(source, target, ip);
        messageService.sendCheckDetailIssuer(source, issuer);
        messageService.sendCheckDetailReason(source, punishment.reason());
        messageService.sendCheckDetailStarted(source, PunishmentTimeFormatter.formatTimestamp(punishment.startTime()));
        messageService.sendCheckDetailDuration(source, duration);
        messageService.sendCheckDetailExpires(source, expires);
        messageService.sendCheckDetailStatus(
                source,
                Boolean.toString(punishment.active()),
                Boolean.toString(punishment.voided()),
                punishment.voidReason()
        );
        messageService.sendCheckDetailMeta(
            source,
            Boolean.toString(punishmentService.isIpPunishment(punishment)),
            template,
            templateStep
        );
    }

    private void sendPunishmentSummary(CommandSource source, PunishmentRecord punishment, boolean includeTarget) {
        if (!includeTarget) {
            messageService.sendCheckSummaryEntry(
                    source,
                    punishment.id(),
                    punishment.type().name(),
                    punishment.reason()
            );
            return;
        }

        String target = resolveTargetName(punishment);
        if (target == null || target.isBlank()) {
            target = "N/A";
        }
        messageService.sendCheckSummaryEntryWithTarget(
                source,
                punishment.id(),
                punishment.type().name(),
                punishment.reason(),
                target
        );
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

    private record HistoryEntry(
            String id,
            String type,
            String reason,
            Instant when
    ) {
        private static HistoryEntry fromPunishment(PunishmentRecord punishment) {
            return new HistoryEntry(
                    fallbackIfBlank(normalizeToken(punishment.id()), "N/A"),
                    fallbackIfBlank(normalizeToken(punishment.type() == null ? null : punishment.type().name()), "N/A"),
                    fallbackIfBlank(normalizeToken(punishment.reason()), "N/A"),
                    punishment.startTime()
            );
        }

        private static HistoryEntry fromModerationAction(ModerationActionRecord action) {
            String linkedId = normalizeToken(action.punishmentId());
            if (linkedId.isEmpty()) {
                linkedId = "MA" + action.id();
            }
            return new HistoryEntry(
                    linkedId,
                    fallbackIfBlank(normalizeToken(action.actionType() == null ? null : action.actionType().name()), "N/A"),
                    fallbackIfBlank(normalizeToken(action.reason()), "N/A"),
                    action.createdAt()
            );
        }

        private static String fallbackIfBlank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    private record PageWindow(
            int currentPage,
            int totalPages,
            int fromIndex,
            int toIndex
    ) {
    }
}
