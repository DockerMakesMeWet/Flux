package org.owl.flux.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.DatabaseEngine;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;

class PunishmentRepositoryTest {
    private HikariDataSource dataSource;
    private PunishmentRepository punishmentRepository;

    @BeforeEach
    void setup() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:mem:flux-punishment-repository-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        hikari.setUsername("sa");
        hikari.setPassword("");
        this.dataSource = new HikariDataSource(hikari);
        new SchemaManager(dataSource, DatabaseEngine.H2).initialize();
        this.punishmentRepository = new PunishmentRepository(dataSource);
    }

    @AfterEach
    void cleanup() {
        dataSource.close();
    }

    @Test
    void findRecentIdsByPrefixMatchesPrefixInRecentOrderWithLimit() {
        Instant now = Instant.now();
        savePunishment("AB0001", now.minusSeconds(30));
        savePunishment("AB0002", now.minusSeconds(20));
        savePunishment("ZZ0001", now.minusSeconds(10));
        savePunishment("AB0003", now);

        List<String> suggestions = punishmentRepository.findRecentIdsByPrefix("ab", 2);
        assertEquals(List.of("AB0003", "AB0002"), suggestions);
    }

    @Test
    void findRecentIdsByPrefixEscapesLikeWildcards() {
        savePunishment("AB0001", Instant.now());

        List<String> suggestions = punishmentRepository.findRecentIdsByPrefix("AB%", 10);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void findRecentActiveIdsByTypePrefixReturnsOnlyActiveMatchingType() {
        punishmentRepository.save(new PunishmentRecord(
                "MU0001",
                PunishmentType.MUTE,
                null,
                "203.0.113.7",
                null,
                "00000000-0000-0000-0000-000000000000",
                "active mute",
                Instant.now().minusSeconds(120),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "MU0002",
                PunishmentType.MUTE,
                null,
                "203.0.113.7",
                null,
                "00000000-0000-0000-0000-000000000000",
                "voided mute",
                Instant.now().minusSeconds(60),
                null,
                true,
                true,
                false,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "BA0001",
                PunishmentType.BAN,
                null,
                "203.0.113.7",
                null,
                "00000000-0000-0000-0000-000000000000",
                "active ban",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));

        List<String> muteIds = punishmentRepository.findRecentActiveIdsByTypePrefix(PunishmentType.MUTE, "mu", 10);
        assertEquals(List.of("MU0001"), muteIds);
    }

    @Test
    void insertSqlForDatabaseProductUsesJsonbCastForPostgreSql() {
        String sql = PunishmentRepository.insertSqlForDatabaseProduct("PostgreSQL");
        assertTrue(sql.contains("?::jsonb"));
    }

    @Test
    void insertSqlForDatabaseProductUsesStandardPlaceholderForH2() {
        String sql = PunishmentRepository.insertSqlForDatabaseProduct("H2");
        assertTrue(sql.contains("?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"));
        assertTrue(!sql.contains("?::jsonb"));
    }

    @Test
    void saveAndFindByIdRoundTripsOfflineTargetFields() {
        PunishmentRecord punishment = new PunishmentRecord(
                "OF0001",
                PunishmentType.BAN,
                null,
                "198.51.100.77",
                "NeverSeenUser",
                "00000000-0000-0000-0000-000000000000",
                "offline target",
                Instant.now(),
                null,
                true,
                false,
                true,
                true,
                Map.of("template", "offline")
        );
        punishmentRepository.save(punishment);

        PunishmentRecord loaded = punishmentRepository.findById("OF0001").orElseThrow();
        assertEquals("NeverSeenUser", loaded.targetUsername());
        assertTrue(loaded.issuedOffline());
        assertTrue(loaded.joinNoticeDelivered());
    }

    @Test
    void saveThrowsDuplicatePunishmentIdExceptionOnPrimaryKeyCollision() {
        PunishmentRecord original = new PunishmentRecord(
                "DUPE01",
                PunishmentType.BAN,
                null,
                "203.0.113.1",
                null,
                "00000000-0000-0000-0000-000000000000",
                "original",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        );
        PunishmentRecord duplicate = new PunishmentRecord(
                "DUPE01",
                PunishmentType.MUTE,
                null,
                "203.0.113.2",
                null,
                "00000000-0000-0000-0000-000000000000",
                "duplicate",
                Instant.now(),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        );
        punishmentRepository.save(original);

        assertThrows(DuplicatePunishmentIdException.class, () -> punishmentRepository.save(duplicate));
    }

    @Test
    void initializeMigratesLegacyPunishmentsTableForOfflineTargetColumns() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE punishments");
            statement.execute("""
                    CREATE TABLE punishments (
                      id VARCHAR(6) PRIMARY KEY,
                      type VARCHAR(16) NOT NULL,
                      target_uuid VARCHAR(36),
                      target_ip VARCHAR(45),
                      executor_uuid VARCHAR(36),
                      reason TEXT NOT NULL,
                      start_time TIMESTAMP NOT NULL,
                      end_time TIMESTAMP NULL,
                      active BOOLEAN NOT NULL,
                      voided BOOLEAN NOT NULL DEFAULT FALSE,
                      metadata CLOB NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO punishments (
                        id, type, target_uuid, target_ip, executor_uuid, reason, start_time, end_time, active, voided, metadata
                    ) VALUES ('LG0001', 'BAN', NULL, '198.51.100.21', '00000000-0000-0000-0000-000000000000',
                        'legacy', CURRENT_TIMESTAMP, NULL, TRUE, FALSE, '{}')
                    """);
        }

        new SchemaManager(dataSource, DatabaseEngine.H2).initialize();

        assertPunishmentColumnExists("TARGET_USERNAME");
        assertPunishmentColumnExists("ISSUED_OFFLINE");
        assertPunishmentColumnExists("JOIN_NOTICE_DELIVERED");
        assertPunishmentColumnExists("MUTE_EXPIRY_NOTICE_PENDING");
        assertPunishmentColumnExists("MUTE_EXPIRY_NOTICE_DELIVERED");

        PunishmentRecord migrated = punishmentRepository.findById("LG0001").orElseThrow();
        assertNull(migrated.targetUsername());
        assertFalse(migrated.issuedOffline());
        assertFalse(migrated.joinNoticeDelivered());
        assertFalse(punishmentBooleanColumnValue("LG0001", "MUTE_EXPIRY_NOTICE_PENDING"));
        assertFalse(punishmentBooleanColumnValue("LG0001", "MUTE_EXPIRY_NOTICE_DELIVERED"));
    }

    @Test
    void activeByTargetReturnsOnlyActivePunishments() {
        punishmentRepository.save(new PunishmentRecord(
                "AB0001",
                PunishmentType.BAN,
                "target-uuid",
                "203.0.113.7",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "active",
                Instant.now().minusSeconds(120),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "AB0002",
                PunishmentType.BAN,
                "target-uuid",
                "203.0.113.7",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "inactive",
                Instant.now().minusSeconds(60),
                null,
                false,
                false,
                false,
                false,
                Map.of()
        ));

        List<PunishmentRecord> active = punishmentRepository.activeByTarget("target-uuid", "TargetUser");
        assertEquals(List.of("AB0001"), active.stream().map(PunishmentRecord::id).toList());
    }

    @Test
    void activeByIpReturnsOnlyActivePunishments() {
        punishmentRepository.save(new PunishmentRecord(
                "AB0010",
                PunishmentType.BAN,
                null,
                "198.51.100.10",
                null,
                "00000000-0000-0000-0000-000000000000",
                "active",
                Instant.now().minusSeconds(120),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "AB0011",
                PunishmentType.BAN,
                null,
                "198.51.100.10",
                null,
                "00000000-0000-0000-0000-000000000000",
                "voided",
                Instant.now().minusSeconds(60),
                null,
                true,
                true,
                false,
                false,
                Map.of()
        ));

        List<PunishmentRecord> active = punishmentRepository.activeByIp("198.51.100.10");
        assertEquals(List.of("AB0010"), active.stream().map(PunishmentRecord::id).toList());
    }

    @Test
    void deactivateActiveByIdDeactivatesOnlyRequestedType() {
        punishmentRepository.save(new PunishmentRecord(
                "MX1001",
                PunishmentType.MUTE,
                null,
                "198.51.100.10",
                null,
                "00000000-0000-0000-0000-000000000000",
                "active mute",
                Instant.now().minusSeconds(120),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));

        boolean wrongType = punishmentRepository.deactivateActiveById("MX1001", PunishmentType.BAN);
        boolean changed = punishmentRepository.deactivateActiveById("MX1001", PunishmentType.MUTE);

        assertFalse(wrongType);
        assertTrue(changed);
        PunishmentRecord updated = punishmentRepository.findById("MX1001").orElseThrow();
        assertFalse(updated.active());
    }

    @Test
    void findPendingJoinNoticesReturnsOnlyUndeliveredOfflinePunishments() {
        Instant now = Instant.now();
        punishmentRepository.save(new PunishmentRecord(
                "JN0001",
                PunishmentType.WARN,
                null,
                "198.51.100.30",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "warn-uuid",
                now.minusSeconds(30),
                null,
                false,
                false,
                true,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "JN0002",
                PunishmentType.MUTE,
                null,
                "198.51.100.30",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "mute-username",
                now.minusSeconds(20),
                null,
                true,
                false,
                true,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "JN0003",
                PunishmentType.BAN,
                null,
                "198.51.100.30",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "already-delivered",
                now.minusSeconds(10),
                null,
                true,
                false,
                true,
                true,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "JN0004",
                PunishmentType.BAN,
                null,
                "198.51.100.30",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "not-offline",
                now.minusSeconds(5),
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));

        List<PunishmentRecord> pending = punishmentRepository.findPendingJoinNotices(null, "targetuser");
        assertEquals(List.of("JN0001", "JN0002"), pending.stream().map(PunishmentRecord::id).toList());
    }

    @Test
    void markJoinNoticesDeliveredPreventsRepeatDelivery() {
        Instant now = Instant.now();
        punishmentRepository.save(new PunishmentRecord(
                "JN0010",
                PunishmentType.WARN,
                null,
                "198.51.100.31",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "one",
                now.minusSeconds(20),
                null,
                false,
                false,
                true,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "JN0011",
                PunishmentType.KICK,
                null,
                "198.51.100.31",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "two",
                now.minusSeconds(10),
                null,
                false,
                false,
                true,
                false,
                Map.of()
        ));

        List<PunishmentRecord> firstPending = punishmentRepository.findPendingJoinNotices(null, "TargetUser");
        int updated = punishmentRepository.markJoinNoticesDelivered(firstPending.stream().map(PunishmentRecord::id).toList());
        List<PunishmentRecord> secondPending = punishmentRepository.findPendingJoinNotices(null, "TargetUser");

        assertEquals(2, updated);
        assertTrue(secondPending.isEmpty());
    }

    @Test
    void expireEndedPunishmentsQueuesMuteExpiryNoticeForRecentlyExpiredMute() {
        Instant now = Instant.now();
        punishmentRepository.save(new PunishmentRecord(
                "ME1001",
                PunishmentType.MUTE,
                null,
                "198.51.100.90",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "expired mute",
                now.minusSeconds(180),
                now.minusSeconds(1),
                true,
                false,
                false,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "ME1002",
                PunishmentType.MUTE,
                null,
                "198.51.100.90",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "active mute",
                now.minusSeconds(30),
                now.plusSeconds(300),
                true,
                false,
                false,
                false,
                Map.of()
        ));

        punishmentRepository.expireEndedPunishments();

        List<PunishmentRecord> pending = punishmentRepository.findPendingMuteExpiryNotices(null, "TargetUser");
        assertEquals(List.of("ME1001"), pending.stream().map(PunishmentRecord::id).toList());
        assertFalse(punishmentRepository.findById("ME1001").orElseThrow().active());
        assertTrue(punishmentRepository.findById("ME1002").orElseThrow().active());
    }

    @Test
    void markMuteExpiryNoticesDeliveredPreventsRepeatDelivery() {
        Instant now = Instant.now();
        punishmentRepository.save(new PunishmentRecord(
                "ME2001",
                PunishmentType.MUTE,
                null,
                "198.51.100.91",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "uuid mute",
                now.minusSeconds(120),
                now.minusSeconds(10),
                true,
                false,
                false,
                false,
                Map.of()
        ));
        punishmentRepository.save(new PunishmentRecord(
                "ME2002",
                PunishmentType.MUTE,
                null,
                "198.51.100.91",
                "TargetUser",
                "00000000-0000-0000-0000-000000000000",
                "username mute",
                now.minusSeconds(90),
                now.minusSeconds(5),
                true,
                false,
                false,
                false,
                Map.of()
        ));

        punishmentRepository.expireEndedPunishments();

        List<PunishmentRecord> firstPending = punishmentRepository.findPendingMuteExpiryNotices(null, "targetuser");
        int updated = punishmentRepository.markMuteExpiryNoticesDelivered(firstPending.stream().map(PunishmentRecord::id).toList());
        int secondUpdate = punishmentRepository.markMuteExpiryNoticesDelivered(firstPending.stream().map(PunishmentRecord::id).toList());
        List<PunishmentRecord> secondPending = punishmentRepository.findPendingMuteExpiryNotices(null, "targetuser");

        assertEquals(List.of("ME2001", "ME2002"), firstPending.stream().map(PunishmentRecord::id).toList());
        assertEquals(2, updated);
        assertEquals(0, secondUpdate);
        assertTrue(secondPending.isEmpty());
    }

    private void savePunishment(String id, Instant startTime) {
        punishmentRepository.save(new PunishmentRecord(
                id,
                PunishmentType.BAN,
                null,
                "203.0.113.7",
                null,
                "00000000-0000-0000-0000-000000000000",
                "test",
                startTime,
                null,
                true,
                false,
                false,
                false,
                Map.of()
        ));
    }

    private void assertPunishmentColumnExists(String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, "PUNISHMENTS", columnName)) {
            assertTrue(columns.next(), () -> "Expected column to exist: " + columnName);
        }
    }

    private boolean punishmentBooleanColumnValue(String punishmentId, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
            statement.setString(1, punishmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), () -> "Expected punishment row to exist: " + punishmentId);
                return resultSet.getBoolean(columnName);
            }
        }
    }
}
