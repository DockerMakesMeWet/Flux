package org.owl.flux.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;
import org.owl.flux.config.model.MainConfig;
import org.slf4j.Logger;

public final class DatabaseManager {
    private static final String POSTGRES_DRIVER_CLASS = "org.postgresql.Driver";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";

    private final Logger logger;
    private final Path dataDirectory;
    private final MainConfig.DatabaseConfig databaseConfig;
    private HikariDataSource dataSource;
    private DatabaseEngine engine;

    public DatabaseManager(Logger logger, Path dataDirectory, MainConfig.DatabaseConfig databaseConfig) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.databaseConfig = databaseConfig;
    }

    public void start() {
        String provider = databaseConfig.provider == null ? "" : databaseConfig.provider.toLowerCase(Locale.ROOT);
        if (provider.equals("postgresql")) {
            try {
                ensureDriverAvailable(POSTGRES_DRIVER_CLASS);
                ensurePostgreSqlDatabaseExists();
                this.dataSource = createPostgreSqlDataSource();
                this.engine = DatabaseEngine.POSTGRESQL;
                testConnection(dataSource);
                logger.info("Flux database connected using PostgreSQL.");
                return;
            } catch (SQLException exception) {
                logger.error("Flux failed to connect to PostgreSQL, falling back to H2.", exception);
                close();
            }
        }

        try {
            ensureDriverAvailable(H2_DRIVER_CLASS);
            this.dataSource = createH2DataSource();
            this.engine = DatabaseEngine.H2;
            testConnection(dataSource);
        } catch (SQLException exception) {
            close();
            throw new IllegalStateException("Failed to initialize fallback H2 database.", exception);
        }
        logger.warn("Flux database is running on fallback H2.");
    }

    private void ensurePostgreSqlDatabaseExists() throws SQLException {
        MainConfig.PostgreSqlConfig pg = databaseConfig.postgresql;
        String databaseName = pg.database == null ? "" : pg.database.trim();
        if (databaseName.isEmpty()) {
            throw new SQLException("PostgreSQL database name is empty.");
        }

        String adminJdbcUrl = "jdbc:postgresql://" + pg.host + ":" + pg.port + "/postgres";
        try (Connection adminConnection = DriverManager.getConnection(adminJdbcUrl, pg.username, pg.password)) {
            if (databaseExists(adminConnection, databaseName)) {
                return;
            }

            try (Statement statement = adminConnection.createStatement()) {
                statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
            }
            logger.info("Flux created missing PostgreSQL database '{}'.", databaseName);
        }
    }

    public void stop() {
        close();
    }

    public DataSource dataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseManager has not been started.");
        }
        return dataSource;
    }

    public DatabaseEngine engine() {
        if (engine == null) {
            throw new IllegalStateException("DatabaseManager has not been started.");
        }
        return engine;
    }

    private HikariDataSource createPostgreSqlDataSource() {
        MainConfig.PostgreSqlConfig pg = databaseConfig.postgresql;
        String jdbcUrl = "jdbc:postgresql://" + pg.host + ":" + pg.port + "/" + pg.database;

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(pg.username);
        hikari.setPassword(pg.password);
        hikari.setDriverClassName(POSTGRES_DRIVER_CLASS);
        hikari.setMaximumPoolSize(pg.pool.maximumPoolSize);
        hikari.setMinimumIdle(pg.pool.minimumIdle);
        hikari.setConnectionTimeout(pg.pool.connectionTimeoutMs);
        hikari.setPoolName("Flux-PostgreSQL");
        hikari.addDataSourceProperty("ApplicationName", "Flux");
        hikari.addDataSourceProperty("sslmode", "disable");
        return new HikariDataSource(hikari);
    }

    private HikariDataSource createH2DataSource() {
        MainConfig.H2Config h2 = databaseConfig.h2;
        String fileName = h2.file == null || h2.file.isBlank() ? "flux-data" : h2.file;
        Path dbPath = dataDirectory.resolve(fileName).toAbsolutePath();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:file:" + dbPath + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE");
        hikari.setUsername("sa");
        hikari.setPassword("");
        hikari.setDriverClassName(H2_DRIVER_CLASS);
        hikari.setMaximumPoolSize(4);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(30000L);
        hikari.setPoolName("Flux-H2");
        return new HikariDataSource(hikari);
    }

    private static void testConnection(DataSource dataSource) throws SQLException {
        try (Connection ignored = dataSource.getConnection()) {
            // no-op
        }
    }

    private static boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void ensureDriverAvailable(String driverClassName) throws SQLException {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Required JDBC driver class not found: " + driverClassName, exception);
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
