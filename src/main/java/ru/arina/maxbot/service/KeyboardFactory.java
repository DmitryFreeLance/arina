package ru.arina.maxbot.service;

import org.springframework.stereotype.Component;
import ru.arina.maxbot.max.InlineKeyboardAttachment;
import ru.arina.maxbot.max.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KeyboardFactory {

    public List<Map<String, Object>> mainMenu(boolean admin) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(InlineKeyboardButton.message("📝 Заявка на неисправность")));
        rows.add(List.of(InlineKeyboardButton.message("👤 Обновить мои данные")));
        if (admin) {
            rows.add(List.of(InlineKeyboardButton.message("🛠 Админ панель")));
        }
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> cancelOnly() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.message("❌ Отмена"))
        )));
    }

    public List<Map<String, Object>> issueTypes() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.message("💡 Электрика")),
                List.of(InlineKeyboardButton.message("🚰 Сантехника")),
                List.of(InlineKeyboardButton.message("🌐 Интернет и связь")),
                List.of(InlineKeyboardButton.message("🧹 Клининг")),
                List.of(InlineKeyboardButton.message("🧰 Другое"))
        )));
    }

    public List<Map<String, Object>> adminMenu() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.message("📂 Неисполненные заявки")),
                List.of(InlineKeyboardButton.message("👥 Пользователи")),
                List.of(InlineKeyboardButton.message("📣 Рассылка")),
                List.of(InlineKeyboardButton.message("🏠 Главное меню"))
        )));
    }

    public List<Map<String, Object>> ticketActions(long ticketId, boolean includeComplete) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(InlineKeyboardButton.message("✅ Принять заявку #" + ticketId)));
        if (includeComplete) {
            rows.add(List.of(InlineKeyboardButton.message("🎉 Отметить исполненной #" + ticketId)));
        }
        rows.add(List.of(InlineKeyboardButton.message("❌ Отклонить заявку #" + ticketId)));
        rows.add(List.of(InlineKeyboardButton.message("📂 К списку заявок")));
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> ticketList(List<List<InlineKeyboardButton>> rows) {
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> userCard(long userId, boolean isAdmin) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (isAdmin) {
            rows.add(List.of(InlineKeyboardButton.message("🔓 Снять админку у " + userId)));
        } else {
            rows.add(List.of(InlineKeyboardButton.message("🛡 Выдать админку " + userId)));
        }
        rows.add(List.of(InlineKeyboardButton.message("👥 К списку пользователей")));
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> simpleAdminPanelButton() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.message("🛠 Админ панель"))
        )));
    }
}
