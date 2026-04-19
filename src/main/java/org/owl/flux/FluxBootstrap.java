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
import org.owl.flux.config.model.MainConfig;
import org.owl.flux.core.ServiceRegistry;
import org.owl.flux.data.DatabaseManager;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.repository.PlayerRepository;
import org.owl.flux.data.repository.ModerationActionRepository;
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

        ConfigurationBundle configuration = configLoader.load();
        Runtime next = buildRuntime(configuration);
        next.start(server, plugin, logger);
        this.runtime = next;
        updateServiceRegistry(configuration);
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
        Runtime activeRuntime = this.runtime;
        if (activeRuntime == null) {
            logger.warn("Flux reload requested before runtime startup.");
            return false;
        }

        ConfigurationBundle configuration;
        try {
            configuration = configLoader.load();
        } catch (Exception exception) {
            logger.error("Failed to reload Flux configuration.", exception);
            return false;
        }

        try {
            activeRuntime.applyConfiguration(configuration, logger, server, plugin);
        } catch (Exception exception) {
            logger.error("Failed to apply reloaded Flux configuration.", exception);
            return false;
        }

        updateServiceRegistry(configuration);
        if (!configuration.main().masters.refreshOnReload) {
            return true;
        }

        try {
            return activeRuntime.mastersService().refreshAsync().get(10, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            logger.warn("Flux masters refresh timed out after reload; cached entries retained.");
            return false;
        } catch (Exception exception) {
            logger.error("Flux masters refresh failed after reload.", exception);
            return false;
        }
    }

    private Runtime buildRuntime(ConfigurationBundle configuration) {
        MainConfig.DatabaseConfig databaseConfig = configuration.main().database;
        DatabaseManager databaseManager = new DatabaseManager(logger, dataDirectory, databaseConfig);
        databaseManager.start();

        DataSource dataSource = databaseManager.dataSource();
        new SchemaManager(dataSource, databaseManager.engine()).initialize();

        PlayerRepository playerRepository = new PlayerRepository(dataSource);
        PunishmentRepository punishmentRepository = new PunishmentRepository(dataSource);
        ModerationActionRepository moderationActionRepository = new ModerationActionRepository(dataSource);

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
                moderationActionRepository,
                actionIdService,
                messageService,
            discordWebhookService,
            mastersService
        );

        ModerationListener moderationListener = new ModerationListener(
                playerRepository,
                punishmentService,
                permissionService,
                mastersService,
            messageService,
            configuration.mutedCommands()
        );

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

        return new Runtime(
                databaseManager,
                commandRegistrar,
                mastersService,
                moderationListener,
                punishmentService,
                messageService,
                templateService,
                discordWebhookService,
            configuration.main().masters != null && configuration.main().masters.autoUpdate,
            mastersAutoUpdateIntervalMinutes(configuration.main().masters),
                databaseConfigSignature(databaseConfig)
        );
    }

    private static String databaseConfigSignature(MainConfig.DatabaseConfig databaseConfig) {
        if (databaseConfig == null) {
            return "";
        }

        MainConfig.PostgreSqlConfig postgresql = databaseConfig.postgresql;
        MainConfig.PoolConfig pool = postgresql == null ? null : postgresql.pool;
        MainConfig.H2Config h2 = databaseConfig.h2;
        return String.join("|",
                safe(databaseConfig.provider),
                safe(postgresql == null ? null : postgresql.host),
                Integer.toString(postgresql == null ? 0 : postgresql.port),
                safe(postgresql == null ? null : postgresql.database),
                safe(postgresql == null ? null : postgresql.username),
                safe(postgresql == null ? null : postgresql.serverVersion),
                Integer.toString(pool == null ? 0 : pool.maximumPoolSize),
                Integer.toString(pool == null ? 0 : pool.minimumIdle),
                Long.toString(pool == null ? 0L : pool.connectionTimeoutMs),
                safe(h2 == null ? null : h2.file)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static long mastersAutoUpdateIntervalMinutes(MainConfig.MastersConfig mastersConfig) {
        if (mastersConfig == null || mastersConfig.autoUpdateIntervalMinutes <= 0) {
            return 30L;
        }
        return mastersConfig.autoUpdateIntervalMinutes;
    }

    private void updateServiceRegistry(ConfigurationBundle configuration) {
        serviceRegistry.clear();
        serviceRegistry.register(ProxyServer.class, server);
        serviceRegistry.register(Logger.class, logger);
        serviceRegistry.register(Path.class, dataDirectory);
        serviceRegistry.register(FluxConfigLoader.class, configLoader);
        serviceRegistry.register(ConfigurationBundle.class, configuration);
    }

    private String resolveVersion() {
        return server.getPluginManager()
                .fromInstance(plugin)
                .flatMap(container -> container.getDescription().getVersion())
                .orElse("unknown");
    }

    private static final class Runtime {
        private final DatabaseManager databaseManager;
        private final FluxCommandRegistrar commandRegistrar;
        private final MastersService mastersService;
        private final ModerationListener moderationListener;
        private final PunishmentService punishmentService;
        private final MessageService messageService;
        private final TemplateService templateService;
        private final DiscordWebhookService discordWebhookService;
        private boolean mastersAutoUpdateEnabled;
        private long mastersAutoUpdateIntervalMinutes;
        private ScheduledTask mastersAutoRefreshTask;
        private final String appliedDatabaseConfigSignature;
        private ScheduledTask muteExpiryNotificationTask;

        private Runtime(
                DatabaseManager databaseManager,
                FluxCommandRegistrar commandRegistrar,
                MastersService mastersService,
                ModerationListener moderationListener,
                PunishmentService punishmentService,
                MessageService messageService,
                TemplateService templateService,
                DiscordWebhookService discordWebhookService,
                boolean mastersAutoUpdateEnabled,
                long mastersAutoUpdateIntervalMinutes,
                String appliedDatabaseConfigSignature
        ) {
            this.databaseManager = databaseManager;
            this.commandRegistrar = commandRegistrar;
            this.mastersService = mastersService;
            this.moderationListener = moderationListener;
            this.punishmentService = punishmentService;
            this.messageService = messageService;
            this.templateService = templateService;
            this.discordWebhookService = discordWebhookService;
            this.mastersAutoUpdateEnabled = mastersAutoUpdateEnabled;
            this.mastersAutoUpdateIntervalMinutes = Math.max(1L, mastersAutoUpdateIntervalMinutes);
            this.appliedDatabaseConfigSignature = appliedDatabaseConfigSignature == null ? "" : appliedDatabaseConfigSignature;
        }

        void start(ProxyServer server, Object plugin, Logger logger) {
            server.getEventManager().register(plugin, moderationListener);
            commandRegistrar.registerAll();
            muteExpiryNotificationTask = server.getScheduler()
                    .buildTask(plugin, () -> {
                        try {
                            punishmentService.processMuteExpiryNotificationsForOnlinePlayers();
                        } catch (RuntimeException exception) {
                            logger.warn("Flux mute-expiry notification poll failed.", exception);
                        }
                    })
                    .repeat(1, TimeUnit.SECONDS)
                    .schedule();
                    rescheduleMastersAutoRefreshTask(server, plugin, logger);
        }

        MastersService mastersService() {
            return mastersService;
        }

        void applyConfiguration(ConfigurationBundle configuration, Logger logger, ProxyServer server, Object plugin) {
            if (configuration == null) {
                return;
            }

            messageService.updateMessages(configuration.messages());
            templateService.updateTemplates(configuration.templates());
            discordWebhookService.updateConfig(configuration.discord());
            moderationListener.updateMutedCommands(configuration.mutedCommands());
            MainConfig.MastersConfig mastersConfig = configuration.main().masters;
            this.mastersAutoUpdateEnabled = mastersConfig != null && mastersConfig.autoUpdate;
            this.mastersAutoUpdateIntervalMinutes = FluxBootstrap.mastersAutoUpdateIntervalMinutes(mastersConfig);
            rescheduleMastersAutoRefreshTask(server, plugin, logger);

            String reloadedDatabaseSignature = FluxBootstrap.databaseConfigSignature(configuration.main().database);
            if (!appliedDatabaseConfigSignature.equals(reloadedDatabaseSignature)) {
                logger.warn("Flux detected database configuration changes during /flux reload; restart the proxy to apply database setting updates.");
            }
        }

        private void rescheduleMastersAutoRefreshTask(ProxyServer server, Object plugin, Logger logger) {
            if (mastersAutoRefreshTask != null) {
                mastersAutoRefreshTask.cancel();
                mastersAutoRefreshTask = null;
            }
            if (!mastersAutoUpdateEnabled) {
                return;
            }

            long intervalMinutes = Math.max(1L, mastersAutoUpdateIntervalMinutes);
            mastersAutoRefreshTask = server.getScheduler()
                    .buildTask(plugin, () -> {
                        try {
                            mastersService.refreshAsync();
                        } catch (RuntimeException exception) {
                            logger.warn("Flux masters auto-refresh task failed to trigger.", exception);
                        }
                    })
                    .delay(intervalMinutes, TimeUnit.MINUTES)
                    .repeat(intervalMinutes, TimeUnit.MINUTES)
                    .schedule();
        }

        void shutdown(ProxyServer server, Object plugin) {
            if (muteExpiryNotificationTask != null) {
                muteExpiryNotificationTask.cancel();
                muteExpiryNotificationTask = null;
            }
            if (mastersAutoRefreshTask != null) {
                mastersAutoRefreshTask.cancel();
                mastersAutoRefreshTask = null;
            }
            commandRegistrar.unregisterAll();
            server.getEventManager().unregisterListeners(plugin);
            databaseManager.stop();
        }
    }
}
