package ru.arina.maxbot.util;

public final class TextUtils {

    private TextUtils() {
    }

    public static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
