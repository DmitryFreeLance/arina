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
        rows.add(List.of(InlineKeyboardButton.message("Заявка на неисправность")));
        rows.add(List.of(InlineKeyboardButton.message("Обновить мои данные")));
        if (admin) {
            rows.add(List.of(InlineKeyboardButton.callback("Админ панель", "admin:menu")));
        }
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> cancelOnly() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.message("Отмена"))
        )));
    }

    public List<Map<String, Object>> issueTypes() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.message("Электрика")),
                List.of(InlineKeyboardButton.message("Сантехника")),
                List.of(InlineKeyboardButton.message("Интернет и связь")),
                List.of(InlineKeyboardButton.message("Клининг")),
                List.of(InlineKeyboardButton.message("Другое"))
        )));
    }

    public List<Map<String, Object>> adminMenu() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.callback("Неисполненные заявки", "admin:tickets:0")),
                List.of(InlineKeyboardButton.callback("Пользователи", "admin:users:0")),
                List.of(InlineKeyboardButton.callback("Рассылка", "admin:broadcast")),
                List.of(InlineKeyboardButton.callback("Добавить админа по ID", "admin:add")),
                List.of(InlineKeyboardButton.callback("Главное меню", "admin:home"))
        )));
    }

    public List<Map<String, Object>> ticketActions(long ticketId, boolean includeComplete) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(InlineKeyboardButton.callback("Принять", "ticket:accept:" + ticketId)));
        if (includeComplete) {
            rows.add(List.of(InlineKeyboardButton.callback("Отметить исполненной", "ticket:complete:" + ticketId)));
        }
        rows.add(List.of(InlineKeyboardButton.callback("Отклонить", "ticket:reject:" + ticketId)));
        rows.add(List.of(InlineKeyboardButton.callback("К заявкам", "admin:tickets:0")));
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> ticketList(List<List<InlineKeyboardButton>> rows) {
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> userCard(long userId, boolean isAdmin) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (isAdmin) {
            rows.add(List.of(InlineKeyboardButton.callback("Снять права администратора", "user:demote:" + userId)));
        } else {
            rows.add(List.of(InlineKeyboardButton.callback("Сделать администратором", "user:promote:" + userId)));
        }
        rows.add(List.of(InlineKeyboardButton.callback("К пользователям", "admin:users:0")));
        return List.of(InlineKeyboardAttachment.of(rows));
    }

    public List<Map<String, Object>> simpleAdminPanelButton() {
        return List.of(InlineKeyboardAttachment.of(List.of(
                List.of(InlineKeyboardButton.callback("Админ панель", "admin:menu"))
        )));
    }
}
