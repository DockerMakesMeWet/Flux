package org.owl.flux.config.model;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class MainConfig {
    public DatabaseConfig database = new DatabaseConfig();
    public MastersConfig masters = new MastersConfig();

    @ConfigSerializable
    public static final class DatabaseConfig {
        public String provider = "postgresql";
        public PostgreSqlConfig postgresql = new PostgreSqlConfig();
        public H2Config h2 = new H2Config();
    }

    @ConfigSerializable
    public static final class PostgreSqlConfig {
        public String host = "127.0.0.1";
        public int port = 5432;
        public String database = "flux";
        public String username = "flux";
        public String password = "change-me";

        @Setting("server-version")
        public String serverVersion = "17";

        public PoolConfig pool = new PoolConfig();
    }

    @ConfigSerializable
    public static final class PoolConfig {
        @Setting("maximum-pool-size")
        public int maximumPoolSize = 10;

        @Setting("minimum-idle")
        public int minimumIdle = 2;

        @Setting("connection-timeout-ms")
        public long connectionTimeoutMs = 30000L;
    }

    @ConfigSerializable
    public static final class H2Config {
        public String file = "flux-data";
    }

    @ConfigSerializable
    public static final class MastersConfig {
        @Setting("refresh-on-reload")
        public boolean refreshOnReload = true;

        @Setting("auto-update")
        public boolean autoUpdate = false;

        @Setting("auto-update-interval-minutes")
        public long autoUpdateIntervalMinutes = 30L;
    }
}
