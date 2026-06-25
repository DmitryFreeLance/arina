package ru.arina.maxbot.max;

import java.util.List;
import java.util.Map;

public class InlineKeyboardAttachment {

    public static Map<String, Object> of(List<List<InlineKeyboardButton>> buttons) {
        return Map.of(
                "type", "inline_keyboard",
                "payload", Map.of("buttons", buttons)
        );
    }
}
