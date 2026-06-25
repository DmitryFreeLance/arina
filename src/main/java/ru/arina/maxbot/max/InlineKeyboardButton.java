package ru.arina.maxbot.max;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineKeyboardButton {

    private String type;
    private String text;
    private String payload;
    private String url;

    public static InlineKeyboardButton callback(String text, String payload) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.type = "callback";
        button.text = text;
        button.payload = payload;
        return button;
    }

    public static InlineKeyboardButton message(String text) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.type = "message";
        button.text = text;
        return button;
    }

    public static InlineKeyboardButton link(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.type = "link";
        button.text = text;
        button.url = url;
        return button;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getPayload() {
        return payload;
    }

    public String getUrl() {
        return url;
    }
}
