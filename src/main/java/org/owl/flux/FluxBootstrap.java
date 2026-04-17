package org.owl.flux;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.owl.flux.command.FluxCommandRegistrar;
import org.owl.flux.command.ReloadHandler;
import org.owl.flux.config.ConfigFileInstaller;
import org.owl.flux.config.ConfigurationBundle;
import org.owl.flux.config.FluxConfigLoader;
import org.owl.flux.core.ServiceRegistry;
import org.owl.flux.data.DatabaseManager;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.integration.DiscordWebhookService;
import org.owl.flux.listener.ModerationListener;
import org.owl.flux.service.ActionIdService;
import org.owl.flux.service.HierarchyService;
import org.owl.flux.service.MastersService;
import org.owl.flux.service.MessageService;
import org.owl.flux.service.PermissionService;
import org.owl.flux.service.PunishmentService;
import org.owl.flux.service.TargetResolver;
import org.owl.flux.service.TemplateService;
import org.owl.flux.ui.FluxStartupBanner;
import org.slf4j.Logger;

public final class FluxBootstrap {
    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final ServiceRegistry serviceRegistry;
    private final FluxConfigLoader configLoader;
    private Runtime runtime;

    public FluxBootstrap(Object plugin, ProxyServer server, Logger logger, Path dataDirectory) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.serviceRegistry = new ServiceRegistry();
        this.configLoader = new FluxConfigLoader(dataDirectory);
    }

    public void start() {
        try {
            ConfigFileInstaller.installDefaults(dataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to install default Flux configuration files.", exception);
        }

        this.runtime = buildRuntime(configLoader.load());
        runtime.mastersService().refreshAsync();
        FluxStartupBanner.sendBanner(logger, resolveVersion());

        logger.info("Flux bootstrap complete.");
    }

    public void stop() {
        if (runtime != null) {
            runtime.shutdown(server, plugin);
            runtime = null;
        }
        serviceRegistry.clear();
        logger.info("Flux shutdown complete.");
    }

    public synchronized boolean reload() {
        Runtime previous = this.runtime;
        Runtime next;
        try {
            next = buildRuntime(configLoader.load());
        } catch (Exception exception) {
            logger.error("Failed to reload Flux configuration.", exception);
            return false;
        }

        this.runtime = next;
        if (previous != null) {
            previous.shutdown(server, plugin);
        }

        try {
            return next.mastersService().refreshAsync().get(10, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            logger.warn("Flux masters refresh timed out after reload; cached entries retained.");
            return false;
        } catch (Exception exception) {
            logger.error("Flux masters refresh failed after reload.", exception);
            return false;
        }
    }

    private Runtime buildRuntime(ConfigurationBundle configuration) {
        DatabaseManager databaseManager = new DatabaseManager(logger, dataDirectory, configuration.main().database);
        databaseManager.start();

        DataSource dataSource = databaseManager.dataSource();
        new SchemaManager(dataSource, databaseManager.engine()).initialize();

        PlayerRepository playerRepository = new PlayerRepository(dataSource);
        PunishmentRepository punishmentRepository = new PunishmentRepository(dataSource);

        Executor executor = Runnable::run;
        MastersService mastersService = new MastersService(logger, executor);
        PermissionService permissionService = new PermissionService(mastersService);
        HierarchyService hierarchyService = new HierarchyService(mastersService, permissionService);
        TargetResolver targetResolver = new TargetResolver(server, playerRepository);
        MessageService messageService = new MessageService(server, configuration.messages(), permissionService, mastersService);
        DiscordWebhookService discordWebhookService = new DiscordWebhookService(logger, configuration.discord());
        ActionIdService actionIdService = new ActionIdService(punishmentRepository);
        TemplateService templateService = new TemplateService(configuration.templates(), punishmentRepository, permissionService);
        PunishmentService punishmentService = new PunishmentService(
                server,
                punishmentRepository,
                actionIdService,
                messageService,
                discordWebhookService
        );

        ModerationListener moderationListener = new ModerationListener(
                playerRepository,
                punishmentService,
                permissionService,
                mastersService,
                messageService
        );

        server.getEventManager().register(plugin, moderationListener);

        FluxCommandRegistrar commandRegistrar = new FluxCommandRegistrar(
                plugin,
                server,
                messageService,
                permissionService,
                hierarchyService,
                targetResolver,
                templateService,
                punishmentService,
                punishmentRepository,
                playerRepository,
                mastersService,
                this::reload,
                resolveVersion()
        );
        commandRegistrar.registerAll();

        ScheduledTask muteExpiryNotificationTask = server.getScheduler()
                .buildTask(plugin, () -> {
                    try {
                        punishmentService.processMuteExpiryNotificationsForOnlinePlayers();
                    } catch (RuntimeException exception) {
                        logger.warn("Flux mute-expiry notification poll failed.", exception);
                    }
                })
                .repeat(1, TimeUnit.SECONDS)
                .schedule();

        serviceRegistry.clear();
        serviceRegistry.register(ProxyServer.class, server);
        serviceRegistry.register(Logger.class, logger);
        serviceRegistry.register(Path.class, dataDirectory);
        serviceRegistry.register(FluxConfigLoader.class, configLoader);
        serviceRegistry.register(ConfigurationBundle.class, configuration);

        return new Runtime(databaseManager, commandRegistrar, mastersService, muteExpiryNotificationTask);
    }

    private String resolveVersion() {
        return server.getPluginManager()
                .fromInstance(plugin)
                .flatMap(container -> container.getDescription().getVersion())
                .orElse("unknown");
    }

    private record Runtime(
            DatabaseManager databaseManager,
            FluxCommandRegistrar commandRegistrar,
            MastersService mastersService,
            ScheduledTask muteExpiryNotificationTask
    ) {
        void shutdown(ProxyServer server, Object plugin) {
            muteExpiryNotificationTask.cancel();
            commandRegistrar.unregisterAll();
            server.getEventManager().unregisterListeners(plugin);
            databaseManager.stop();
        }
    }
}
