package org.owl.flux.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.command.CommandSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.owl.flux.config.model.TemplatesConfig;
import org.owl.flux.data.DatabaseEngine;
import org.owl.flux.data.SchemaManager;
import org.owl.flux.data.model.PunishmentRecord;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.service.model.TemplateResolution;
import org.slf4j.Logger;

class TemplateServiceTest {
    private HikariDataSource dataSource;
    private PunishmentRepository punishmentRepository;

    @BeforeEach
    void setup() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:h2:mem:flux-template-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
    void resolveEscalatesIgnoringVoidedActions() {
        String targetUuid = "01234567-89ab-cdef-0123-456789abcdef";
        Instant now = Instant.now();
        punishmentRepository.save(new PunishmentRecord(
                "AAAAAA",
                PunishmentType.BAN,
                targetUuid,
                "127.0.0.1",
                "00000000-0000-0000-0000-000000000000",
                "tier1",
                now,
                now.plus(Duration.ofDays(1)),
                true,
                false,
                Map.of("template", "hacking")
        ));
        punishmentRepository.save(new PunishmentRecord(
                "BBBBBB",
                PunishmentType.BAN,
                targetUuid,
                "127.0.0.1",
                "00000000-0000-0000-0000-000000000000",
                "voided",
                now,
                now.plus(Duration.ofDays(1)),
                false,
                true,
                Map.of("template", "hacking")
        ));

        TemplatesConfig templatesConfig = new TemplatesConfig();
        TemplatesConfig.TemplateDefinition definition = new TemplatesConfig.TemplateDefinition();
        definition.permission = "flux.template.hacking";
        definition.type = "BAN";
        definition.tiers = List.of(
                tier("1d", "tier1"),
                tier("7d", "tier2"),
                tier("permanent", "tier3")
        );
        templatesConfig.templates.put("hacking", definition);

        Logger logger = mock(Logger.class);
        MastersService mastersService = new MastersService(logger, Runnable::run);
        PermissionService permissionService = new PermissionService(mastersService);
        TemplateService templateService = new TemplateService(templatesConfig, punishmentRepository, permissionService);
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("flux.template.*")).thenReturn(false);
        when(source.hasPermission("flux.template.hacking")).thenReturn(true);

        TemplateResolution resolution = templateService.resolve(source, "#hacking", targetUuid);
        assertEquals(PunishmentType.BAN, resolution.type());
        assertEquals(Duration.ofDays(7), resolution.duration());
        assertEquals("tier2", resolution.reason());
        assertEquals("hacking", resolution.templateName());
    }

    @Test
    void resolveRejectsTemplateWithoutPermission() {
        TemplatesConfig templatesConfig = new TemplatesConfig();
        TemplatesConfig.TemplateDefinition definition = new TemplatesConfig.TemplateDefinition();
        definition.permission = "flux.template.hacking";
        definition.type = "BAN";
        definition.tiers = List.of(tier("1d", "tier1"));
        templatesConfig.templates.put("hacking", definition);

        Logger logger = mock(Logger.class);
        MastersService mastersService = new MastersService(logger, Runnable::run);
        PermissionService permissionService = new PermissionService(mastersService);
        TemplateService templateService = new TemplateService(templatesConfig, punishmentRepository, permissionService);

        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("flux.template.*")).thenReturn(false);
        when(source.hasPermission("flux.template.hacking")).thenReturn(false);

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> templateService.resolve(source, "#hacking", "abc"));
        assertEquals("template:no-permission", exception.getMessage());
    }

    private static TemplatesConfig.TemplateTier tier(String duration, String reason) {
        TemplatesConfig.TemplateTier tier = new TemplatesConfig.TemplateTier();
        tier.duration = duration;
        tier.reason = reason;
        return tier;
    }
}
