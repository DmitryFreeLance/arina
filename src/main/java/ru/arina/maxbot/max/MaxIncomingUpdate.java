package ru.arina.maxbot.max;

import com.fasterxml.jackson.databind.JsonNode;

public record MaxIncomingUpdate(JsonNode raw) {

    public String updateType() {
        return text(raw, "update_type");
    }

    public Long callbackUserId() {
        return firstLong(
                raw.at("/callback/user/user_id"),
                raw.at("/callback/user/id"),
                raw.at("/user/user_id"),
                raw.at("/user/id")
        );
    }

    public Long actorUserId() {
        return firstLong(
                raw.at("/message/sender/user_id"),
                raw.at("/message/sender/id"),
                raw.at("/user/user_id"),
                raw.at("/user/id"),
                raw.at("/sender/user_id")
        );
    }

    public String actorName() {
        String candidate = firstText(
                raw.at("/message/sender/name"),
                raw.at("/user/name"),
                raw.at("/sender/name")
        );
        return candidate == null ? "" : candidate;
    }

    public String actorUsername() {
        return firstText(
                raw.at("/message/sender/username"),
                raw.at("/user/username"),
                raw.at("/sender/username")
        );
    }

    public String messageText() {
        return firstText(
                raw.at("/message/body/text"),
                raw.at("/message/text"),
                raw.at("/body/text")
        );
    }

    public String callbackId() {
        return firstText(raw.at("/callback/callback_id"), raw.at("/callback/id"));
    }

    public String callbackPayload() {
        return firstText(raw.at("/callback/payload"), raw.at("/payload"));
    }

    public Long chatId() {
        return firstLong(raw.at("/chat_id"), raw.at("/message/recipient/chat_id"), raw.at("/message/chat_id"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                String text = node.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Long firstLong(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull() && node.canConvertToLong()) {
                return node.asLong();
            }
        }
        return null;
    }
}
