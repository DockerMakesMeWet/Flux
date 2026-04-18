package org.owl.flux.service;

import com.velocitypowered.api.command.CommandSource;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.owl.flux.config.model.TemplatesConfig;
import org.owl.flux.data.model.PunishmentType;
import org.owl.flux.data.repository.PunishmentRepository;
import org.owl.flux.service.model.TemplateResolution;
import org.owl.flux.util.OrdinalFormatter;
import org.owl.flux.util.DurationParser;

public final class TemplateService {
    private volatile TemplatesConfig templatesConfig;
    private final PunishmentRepository punishmentRepository;
    private final PermissionService permissionService;

    public TemplateService(
            TemplatesConfig templatesConfig,
            PunishmentRepository punishmentRepository,
            PermissionService permissionService
    ) {
        this.templatesConfig = templatesConfig == null ? new TemplatesConfig() : templatesConfig;
        this.punishmentRepository = punishmentRepository;
        this.permissionService = permissionService;
    }

    public void updateTemplates(TemplatesConfig templatesConfig) {
        this.templatesConfig = templatesConfig == null ? new TemplatesConfig() : templatesConfig;
    }

    public TemplateResolution resolve(
            CommandSource source,
            String rawTemplate,
            String targetUuid
    ) {
        TemplatesConfig currentTemplates = this.templatesConfig;
        String templateName = rawTemplate.startsWith("#")
                ? rawTemplate.substring(1).toLowerCase(Locale.ROOT)
                : rawTemplate.toLowerCase(Locale.ROOT);

        TemplatesConfig.TemplateDefinition definition = currentTemplates.templates.get(templateName);
        if (definition == null) {
            throw new IllegalArgumentException("template:not-found");
        }

        if (!permissionService.has(source, "flux.template.*") && !permissionService.has(source, definition.permission)) {
            throw new IllegalArgumentException("template:no-permission");
        }

        PunishmentType type = PunishmentType.valueOf(definition.type.toUpperCase(Locale.ROOT));
        int priorCount = punishmentRepository.countTemplateHistory(targetUuid, type, templateName);
        int tierIndex = Math.min(priorCount, definition.tiers.size() - 1);
        int offenseStep = tierIndex + 1;
        TemplatesConfig.TemplateTier tier = definition.tiers.get(tierIndex);

        Duration duration = DurationParser.parse(tier.duration).orElse(null);
        return new TemplateResolution(
            type,
            duration,
            tier.reason,
            templateName,
            offenseStep,
            OrdinalFormatter.offenseLabel(offenseStep)
        );
    }

    public Map<String, TemplatesConfig.TemplateDefinition> allTemplates() {
        return templatesConfig.templates;
    }
}
