package org.owl.flux.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.DatabaseEngine;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.model.AccountVisit;
import org.owl.flux.data.model.IpVisit;

class PlayerRepositoryTest {
    private HikariDataSource dataSource;
    private PlayerRepository playerRepository;

    @BeforeEach
    void setup() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:mem:flux-player-repository-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        hikari.setUsername("sa");
        hikari.setPassword("");
        this.dataSource = new HikariDataSource(hikari);
        new SchemaManager(dataSource, DatabaseEngine.H2).initialize();
        this.playerRepository = new PlayerRepository(dataSource);
    }

    @AfterEach
    void cleanup() {
        dataSource.close();
    }

    @Test
    void findIpHistoryByUuidReturnsDistinctIpsMostRecentFirst() throws Exception {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
        Instant t3 = Instant.parse("2026-01-03T00:00:00Z");
        insertPlayer("uuid-1", "Target", "198.51.100.1");
        insertIpHistory("uuid-1", "198.51.100.1", t1);
        insertIpHistory("uuid-1", "203.0.113.7", t2);
        insertIpHistory("uuid-1", "198.51.100.1", t3);

        List<IpVisit> visits = playerRepository.findIpHistoryByUuid("uuid-1");

        assertEquals(2, visits.size());
        assertEquals("198.51.100.1", visits.get(0).ip());
        assertEquals(t3, visits.get(0).lastSeen());
        assertEquals("203.0.113.7", visits.get(1).ip());
        assertEquals(t2, visits.get(1).lastSeen());
    }

    @Test
    void findAccountsByIpReturnsDistinctAccountsWithLatestSeen() throws Exception {
        Instant t1 = Instant.parse("2026-02-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-02-02T00:00:00Z");
        Instant t3 = Instant.parse("2026-02-03T00:00:00Z");
        insertPlayer("uuid-1", "Alpha", "203.0.113.10");
        insertPlayer("uuid-2", "Bravo", "203.0.113.10");
        insertIpHistory("uuid-1", "203.0.113.10", t1);
        insertIpHistory("uuid-2", "203.0.113.10", t2);
        insertIpHistory("uuid-1", "203.0.113.10", t3);

        List<AccountVisit> accounts = playerRepository.findAccountsByIp("203.0.113.10");

        assertEquals(2, accounts.size());
        assertEquals("Alpha", accounts.get(0).account());
        assertEquals(t3, accounts.get(0).lastSeen());
        assertEquals("Bravo", accounts.get(1).account());
        assertEquals(t2, accounts.get(1).lastSeen());
    }

    private void insertPlayer(String uuid, String username, String ip) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO players (uuid, username, last_ip) VALUES (?, ?, ?)")) {
            statement.setString(1, uuid);
            statement.setString(2, username);
            statement.setString(3, ip);
            statement.executeUpdate();
        }
    }

    private void insertIpHistory(String uuid, String ip, Instant lastSeen) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO ip_history (uuid, ip, last_seen) VALUES (?, ?, ?)")) {
            statement.setString(1, uuid);
            statement.setString(2, ip);
            statement.setTimestamp(3, Timestamp.from(lastSeen));
            statement.executeUpdate();
        }
    }
}
