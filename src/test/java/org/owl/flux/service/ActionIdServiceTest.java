package org.owl.flux.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.owl.flux.data.DatabaseEngine;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.repository.PunishmentRepository;

class ActionIdServiceTest {
    private HikariDataSource dataSource;
    private ActionIdService actionIdService;

    @BeforeEach
    void setup() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:mem:flux-id-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        hikari.setUsername("sa");
        hikari.setPassword("");
        this.dataSource = new HikariDataSource(hikari);
        new SchemaManager(dataSource, DatabaseEngine.H2).initialize();
        this.actionIdService = new ActionIdService(new PunishmentRepository(dataSource));
    }

    @AfterEach
    void cleanup() {
        dataSource.close();
    }

    @Test
    void generatesUppercaseAlphanumericIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String id = actionIdService.nextUniqueId();
            assertEquals(6, id.length());
            assertTrue(id.matches("^[A-Z0-9]{6}$"));
            ids.add(id);
        }
        assertEquals(100, ids.size());
    }
}
