package org.owl.flux.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.owl.flux.config.model.DiscordConfig;
import org.slf4j.Logger;

public final class DiscordWebhookService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Logger logger;
    private final DiscordConfig config;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiscordWebhookService(Logger logger, DiscordConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public void sendAction(
            String actionKey,
            String target,
            String executor,
            String reason,
            String id,
            String type,
            boolean ipPunishment
    ) {
        if (!config.webhook.enabled || config.webhook.url == null || config.webhook.url.isBlank()) {
            return;
        }

        DiscordConfig.ActionWebhookConfig actionConfig = resolveActionConfig(actionKey);
        if (actionConfig == null || !actionConfig.enabled) {
            return;
        }

        try {
            String description = actionConfig.description
                    .replace("<executor>", safe(executor))
                    .replace("<type>", safe(type))
                    .replace("<action>", safe(actionKey))
                    .replace("<target>", safe(target))
                    .replace("<reason>", safe(reason))
                    .replace("<id>", safe(id))
                    .replace("<ip_punishment>", Boolean.toString(ipPunishment));

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", actionConfig.title);
            embed.put("description", description);

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", config.webhook.username);
            if (config.webhook.avatarUrl != null && !config.webhook.avatarUrl.isBlank()) {
                payload.put("avatar_url", config.webhook.avatarUrl.replace("<target>", safe(target)));
            }
            payload.put("embeds", List.of(embed));

            long timeoutMs = Math.max(1000L, config.webhook.timeoutMs);
            String body = OBJECT_MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.webhook.url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> {
                        logger.error("Failed to send Flux Discord webhook payload for action '{}'.", actionKey, error);
                        return null;
                    });
        } catch (Exception exception) {
            logger.error("Failed to build Flux Discord webhook payload for action '{}'.", actionKey, exception);
        }
    }

    private DiscordConfig.ActionWebhookConfig resolveActionConfig(String actionKey) {
        if (config.actions == null || config.actions.isEmpty()) {
            return null;
        }

        String normalizedAction = actionKey == null ? "" : actionKey.toLowerCase(Locale.ROOT);
        return config.actions.get(normalizedAction);
    }

    private static String safe(String value) {
        return value == null ? "N/A" : value;
    }
}
