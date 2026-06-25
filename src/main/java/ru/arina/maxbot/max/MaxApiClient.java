package ru.arina.maxbot.max;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import ru.arina.maxbot.config.BotProperties;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MaxApiClient {

    private static final Logger log = LoggerFactory.getLogger(MaxApiClient.class);

    private final RestClient restClient;
    private final BotProperties botProperties;

    public MaxApiClient(RestClient restClient, BotProperties botProperties) {
        this.restClient = restClient;
        this.botProperties = botProperties;
    }

    public void sendMessageToUser(Long userId, String text, List<Map<String, Object>> attachments) {
        if (userId == null) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        if (attachments != null && !attachments.isEmpty()) {
            body.put("attachments", attachments);
        }
        post("/messages?user_id=" + userId, body);
    }

    public void sendMessageToChat(Long chatId, String text, List<Map<String, Object>> attachments) {
        if (chatId == null) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        if (attachments != null && !attachments.isEmpty()) {
            body.put("attachments", attachments);
        }
        post("/messages?chat_id=" + chatId, body);
    }

    public void answerCallback(String callbackId, String notification) {
        if (!StringUtils.hasText(callbackId)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        if (StringUtils.hasText(notification)) {
            body.put("notification", notification);
        }
        post("/answers?callback_id=" + callbackId, body);
    }

    public void replaceCallbackMessage(String callbackId, String text, List<Map<String, Object>> attachments, String notification) {
        if (!StringUtils.hasText(callbackId)) {
            return;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("text", text);
        if (attachments != null && !attachments.isEmpty()) {
            message.put("attachments", attachments);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        if (StringUtils.hasText(notification)) {
            body.put("notification", notification);
        }
        post("/answers?callback_id=" + callbackId, body);
    }

    public void registerWebhook() {
        if (!StringUtils.hasText(botProperties.getWebhookUrl())) {
            log.info("WEBHOOK_URL is empty, webhook auto-registration skipped");
            return;
        }
        try {
            deleteWebhook(botProperties.getWebhookUrl());
        } catch (Exception ex) {
            log.warn("Could not delete previous webhook subscription: {}", ex.getMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("url", botProperties.getWebhookUrl());
        body.put("update_types", List.of("bot_started", "message_created", "message_callback"));
        if (StringUtils.hasText(botProperties.getWebhookSecret())) {
            body.put("secret", botProperties.getWebhookSecret());
        }
        post("/subscriptions", body);
        log.info("Webhook registered for {}", botProperties.getWebhookUrl());
    }

    public void deleteWebhook(String url) {
        restClient.delete()
                .uri(botProperties.getApiBaseUrl() + "/subscriptions?url=" + UriUtils.encode(url, StandardCharsets.UTF_8))
                .header("Authorization", botProperties.getToken())
                .retrieve()
                .toBodilessEntity();
    }

    public JsonNode getMe() {
        return restClient.get()
                .uri(botProperties.getApiBaseUrl() + "/me")
                .header("Authorization", botProperties.getToken())
                .retrieve()
                .body(JsonNode.class);
    }

    private void post(String path, Map<String, Object> body) {
        restClient.post()
                .uri(botProperties.getApiBaseUrl() + path)
                .header("Authorization", botProperties.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
