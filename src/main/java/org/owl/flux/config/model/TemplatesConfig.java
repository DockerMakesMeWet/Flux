package org.owl.flux.config.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class TemplatesConfig {
    public Map<String, TemplateDefinition> templates = new LinkedHashMap<>();

    @ConfigSerializable
    public static final class TemplateDefinition {
        public String label = "";
        public String category = "";
        public String permission = "";
        public String type = "";
        public String notes = "";
        @Setting("ip-punishment")
        public boolean ipPunishment = false;
        public List<TemplateTier> tiers = new ArrayList<>();
    }

    @ConfigSerializable
    public static final class TemplateTier {
        public String duration = "";
        public String reason = "";
    }
}
