package org.owl.flux.config.model;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
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

        // Optional plain text content (for mentions) sent alongside embeds.
        public String content = "";

        // Hex color string such as #ED4245. Empty or invalid values are ignored.
        public String color = "#5865F2";

        @Setting("show-timestamp")
        public boolean showTimestamp = true;

        @Setting("footer-text")
        public String footerText = "Flux Moderation";

        public List<ActionFieldConfig> fields = new ArrayList<>();
    }

    @ConfigSerializable
    public static final class ActionFieldConfig {
        public String name = "Details";
        public String value = "<reason>";
        public boolean inline = false;
    }
}
