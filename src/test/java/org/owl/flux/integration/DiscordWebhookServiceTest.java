package org.owl.flux.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.owl.flux.config.model.DiscordConfig;
import org.slf4j.Logger;

class DiscordWebhookServiceTest {

    @Test
    void buildPayloadProducesBrandedEmbedWithConfiguredFields() {
        DiscordConfig config = new DiscordConfig();
        config.webhook.enabled = true;
        config.webhook.url = "https://example.com/webhook";
        config.webhook.username = "Flux";
        config.webhook.avatarUrl = "https://example.com/avatar/<target>";

        DiscordConfig.ActionWebhookConfig action = new DiscordConfig.ActionWebhookConfig();
        action.title = "Ban Issued - <id>";
        action.description = "<executor> banned <target>";
        action.color = "#ED4245";
        action.footerText = "Flux Moderation";
        action.showTimestamp = true;

        DiscordConfig.ActionFieldConfig reasonField = new DiscordConfig.ActionFieldConfig();
        reasonField.name = "Reason";
        reasonField.value = "<reason>";
        reasonField.inline = false;

        DiscordConfig.ActionFieldConfig durationField = new DiscordConfig.ActionFieldConfig();
        durationField.name = "Duration";
        durationField.value = "<duration>";
        durationField.inline = true;

        action.fields = List.of(reasonField, durationField);

        config.actions = new LinkedHashMap<>();
        config.actions.put("ban", action);

        DiscordWebhookService service = new DiscordWebhookService(mock(Logger.class), config);

        Map<String, Object> payload = service.buildPayload(
                action,
                "ban",
                "TargetUser",
                "Console",
                "Rule violation",
                "AB1001",
                "BAN",
                true,
                Map.of("duration", "3d")
        );

        assertEquals("Flux", payload.get("username"));
        assertEquals("https://example.com/avatar/TargetUser", payload.get("avatar_url"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> embeds = (List<Map<String, Object>>) payload.get("embeds");
        assertEquals(1, embeds.size());

        Map<String, Object> embed = embeds.get(0);
        assertEquals("Ban Issued - AB1001", embed.get("title"));
        assertEquals("Console banned TargetUser", embed.get("description"));
        assertEquals(15548997, embed.get("color"));

        @SuppressWarnings("unchecked")
        Map<String, Object> footer = (Map<String, Object>) embed.get("footer");
        String footerText = (String) footer.get("text");
        assertTrue(footerText.startsWith("Flux Moderation • "));
        assertFalse(embed.containsKey("timestamp"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) embed.get("fields");
        assertEquals(2, fields.size());
        assertEquals("Reason", fields.get(0).get("name"));
        assertEquals("Rule violation", fields.get(0).get("value"));
        assertEquals(false, fields.get(0).get("inline"));
        assertEquals("Duration", fields.get(1).get("name"));
        assertEquals("3d", fields.get(1).get("value"));
        assertEquals(true, fields.get(1).get("inline"));
    }

    @Test
    void buildPayloadFallsBackToDefaultFieldLayoutWhenNoFieldsConfigured() {
        DiscordConfig config = new DiscordConfig();
        config.webhook.enabled = true;
        config.webhook.url = "https://example.com/webhook";

        DiscordConfig.ActionWebhookConfig action = new DiscordConfig.ActionWebhookConfig();
        action.title = "Flux Action";
        action.description = "<executor> performed <action>";
        action.fields = List.of();

        config.actions = new LinkedHashMap<>();
        config.actions.put("void", action);

        DiscordWebhookService service = new DiscordWebhookService(mock(Logger.class), config);

        Map<String, Object> payload = service.buildPayload(
                action,
                "void",
                "AB1234",
                "Console",
                "Action voided",
                "AB1234",
                "VOID",
                false,
                Map.of()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> embeds = (List<Map<String, Object>>) payload.get("embeds");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) embeds.get(0).get("fields");

        assertFalse(fields.isEmpty());
        assertEquals("Target", fields.get(0).get("name"));
        assertEquals("AB1234", fields.get(0).get("value"));
    }
}
