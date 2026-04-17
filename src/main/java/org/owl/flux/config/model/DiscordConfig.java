package org.owl.flux.config.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class DiscordConfig {
    public WebhookConfig webhook = new WebhookConfig();
    public Map<String, ActionWebhookConfig> actions = new LinkedHashMap<>();

    @ConfigSerializable
    public static final class WebhookConfig {
        public boolean enabled = false;
        public String url = "";
        public String username = "Flux";

        @Setting("avatar-url")
        public String avatarUrl = "https://crafatar.com/avatars/<target>";

        @Setting("timeout-ms")
        public long timeoutMs = 8000L;
    }

    @ConfigSerializable
    public static final class ActionWebhookConfig {
        public boolean enabled = true;
        public String title = "Flux Action";
        public String description = "<executor> executed <action> on <target> for <reason>";
    }
}
