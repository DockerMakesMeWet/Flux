package org.owl.flux.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.owl.flux.config.model.DiscordConfig;
import org.slf4j.Logger;

public final class DiscordWebhookService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter WEBHOOK_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

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
        sendAction(actionKey, target, executor, reason, id, type, ipPunishment, Map.of());
    }

    public void sendAction(
            String actionKey,
            String target,
            String executor,
            String reason,
            String id,
            String type,
            boolean ipPunishment,
            Map<String, String> context
    ) {
        if (!config.webhook.enabled || config.webhook.url == null || config.webhook.url.isBlank()) {
            return;
        }

        DiscordConfig.ActionWebhookConfig actionConfig = resolveActionConfig(actionKey);
        if (actionConfig == null || !actionConfig.enabled) {
            return;
        }

        try {
            Map<String, Object> payload = buildPayload(actionConfig, actionKey, target, executor, reason, id, type, ipPunishment, context);

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

    Map<String, Object> buildPayload(
            DiscordConfig.ActionWebhookConfig actionConfig,
            String actionKey,
            String target,
            String executor,
            String reason,
            String id,
            String type,
            boolean ipPunishment,
            Map<String, String> context
    ) {
        Instant eventTime = Instant.now();
        String timestamp = formatWebhookTime(eventTime);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("executor", safe(executor));
        placeholders.put("type", safe(type));
        placeholders.put("action", safe(actionKey));
        placeholders.put("target", safe(target));
        placeholders.put("reason", safe(reason));
        placeholders.put("id", safe(id));
        placeholders.put("ip_punishment", Boolean.toString(ipPunishment));
        placeholders.put("timestamp", timestamp);
        if (context != null) {
            context.forEach((key, value) -> placeholders.put(normalizeKey(key), safe(value)));
        }

        String description = replacePlaceholders(actionConfig.description, placeholders);

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", replacePlaceholders(actionConfig.title, placeholders));
        embed.put("description", description);

        Integer color = parseHexColor(actionConfig.color);
        if (color != null) {
            embed.put("color", color);
        }

        List<Map<String, Object>> fields = buildFields(actionConfig, placeholders);
        if (!fields.isEmpty()) {
            embed.put("fields", fields);
        }

        String footerText = replacePlaceholders(actionConfig.footerText, placeholders);
        String normalizedFooter = normalizeFooter(footerText, timestamp);
        embed.put("footer", Map.of("text", normalizedFooter));

        // Keep all visible timestamps in strict formatter output via placeholders/footer.
        // Avoid emitting ISO timestamp fields here to keep output format consistent.

        Map<String, Object> payload = new HashMap<>();
        payload.put("username", config.webhook.username);

        String content = replacePlaceholders(actionConfig.content, placeholders);
        if (!content.isBlank()) {
            payload.put("content", content);
        }

        if (config.webhook.avatarUrl != null && !config.webhook.avatarUrl.isBlank()) {
            payload.put("avatar_url", replacePlaceholders(config.webhook.avatarUrl, placeholders));
        }
        payload.put("embeds", List.of(embed));
        return payload;
    }

    private DiscordConfig.ActionWebhookConfig resolveActionConfig(String actionKey) {
        if (config.actions == null || config.actions.isEmpty()) {
            return null;
        }

        String normalizedAction = actionKey == null ? "" : actionKey.toLowerCase(Locale.ROOT);
        return config.actions.get(normalizedAction);
    }

    private List<Map<String, Object>> buildFields(DiscordConfig.ActionWebhookConfig actionConfig, Map<String, String> placeholders) {
        List<DiscordConfig.ActionFieldConfig> configuredFields = actionConfig.fields == null
                ? List.of()
                : actionConfig.fields;

        List<Map<String, Object>> fields = new ArrayList<>();
        if (configuredFields.isEmpty()) {
            fields.add(field("Target", "<target>", true, placeholders));
            fields.add(field("Executor", "<executor>", true, placeholders));
            fields.add(field("Action ID", "<id>", true, placeholders));
            fields.add(field("Type", "<type>", true, placeholders));
            fields.add(field("IP Punishment", "<ip_punishment>", true, placeholders));
            fields.add(field("Reason", "<reason>", false, placeholders));
            return fields;
        }

        for (DiscordConfig.ActionFieldConfig configured : configuredFields) {
            if (configured == null) {
                continue;
            }

            String name = replacePlaceholders(configured.name, placeholders);
            String value = replacePlaceholders(configured.value, placeholders);
            if (name.isBlank() || value.isBlank()) {
                continue;
            }

            Map<String, Object> field = new HashMap<>();
            field.put("name", name);
            field.put("value", value);
            field.put("inline", configured.inline);
            fields.add(field);
        }
        return fields;
    }

    private static Map<String, Object> field(String name, String value, boolean inline, Map<String, String> placeholders) {
        Map<String, Object> field = new HashMap<>();
        field.put("name", replacePlaceholders(name, placeholders));
        field.put("value", replacePlaceholders(value, placeholders));
        field.put("inline", inline);
        return field;
    }

    private static String replacePlaceholders(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return "";
        }

        String output = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", safe(entry.getValue()));
        }
        return output;
    }

    private Integer parseHexColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }

        String normalized = color.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        if (normalized.length() != 6) {
            return null;
        }

        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            logger.warn("Invalid webhook embed color '{}'; expected 6-digit hex.", color);
            return null;
        }
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "N/A" : value;
    }

    private static String formatWebhookTime(Instant instant) {
        return WEBHOOK_TIME_FORMATTER.format(instant);
    }

    private static String normalizeFooter(String configuredFooter, String formattedTime) {
        return "Flux Moderation • " + formattedTime;
    }
}
