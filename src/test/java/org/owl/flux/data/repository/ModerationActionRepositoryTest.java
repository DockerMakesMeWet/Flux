package org.owl.flux.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.DatabaseEngine;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.model.ModerationActionType;

class ModerationActionRepositoryTest {
    private HikariDataSource dataSource;
    private ModerationActionRepository repository;

    @BeforeEach
    void setup() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:mem:flux-moderation-action-repository-test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        hikari.setUsername("sa");
        hikari.setPassword("");
        this.dataSource = new HikariDataSource(hikari);
        new SchemaManager(dataSource, DatabaseEngine.H2).initialize();
        this.repository = new ModerationActionRepository(dataSource);
    }

    @AfterEach
    void cleanup() {
        dataSource.close();
    }

    @Test
    void savePersistsReversalAuditRecord() throws Exception {
        Instant createdAt = Instant.parse("2026-04-18T05:32:49Z");

        repository.save(
                ModerationActionType.UNBAN,
                "TargetUser",
                "AB1234",
                "00000000-0000-0000-0000-000000000999",
                "appeal accepted",
                createdAt
        );

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM moderation_actions")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("UNBAN", resultSet.getString("action_type"));
                assertEquals("TargetUser", resultSet.getString("target_reference"));
                assertEquals("AB1234", resultSet.getString("punishment_id"));
                assertEquals("00000000-0000-0000-0000-000000000999", resultSet.getString("executor_uuid"));
                assertEquals("appeal accepted", resultSet.getString("reason"));
                assertEquals(createdAt, resultSet.getTimestamp("created_at").toInstant());
            }
        }
    }
}
