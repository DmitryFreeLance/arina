package ru.arina.maxbot.service;

import org.springframework.stereotype.Component;
import ru.arina.maxbot.config.BotProperties;
import ru.arina.maxbot.domain.BotUser;
import ru.arina.maxbot.domain.Ticket;
import ru.arina.maxbot.domain.TicketStatus;
import ru.arina.maxbot.util.TextUtils;
import ru.arina.maxbot.util.TimeFormatter;

@Component
public class MessageFactory {

    private final BotProperties botProperties;
    private final TimeFormatter timeFormatter;

    public MessageFactory(BotProperties botProperties, TimeFormatter timeFormatter) {
        this.botProperties = botProperties;
        this.timeFormatter = timeFormatter;
    }

    public String welcome(BotUser user) {
        StringBuilder builder = new StringBuilder();
        if (!user.isRegistered()) {
            builder.append("🏢 Добро пожаловать в систему заявок ").append(botProperties.getCompanyName()).append("\n\n");
            builder.append("Для начала работы необходимо пройти регистрацию.\n");
            builder.append("Пожалуйста, введите ваше полное ФИО:");
        } else {
            builder.append("🏢 Добро пожаловать в систему заявок ").append(botProperties.getCompanyName()).append("!\n\n");
            builder.append("Я помогу быстро передать информацию о неисправности и сообщу, что происходит с вашей заявкой.\n\n");
            builder.append("Вы зарегистрированы как:\n");
            builder.append("👤 ").append(TextUtils.safe(user.getFullName())).append("\n");
            builder.append("🏢 ").append(TextUtils.safe(user.getCompanyName())).append("\n\n");
            builder.append("Готов принять новую заявку в любой момент.");
        }
        if (user.isAdmin()) {
            builder.append("\n\n🛠 Для служебной работы вам доступна админ панель.");
        }
        return builder.toString();
    }

    public String askFullName() {
        return "📝 Для начала работы необходимо пройти регистрацию.\n\nПожалуйста, введите ваше полное ФИО:";
    }

    public String askCompany() {
        return "🏢 Теперь введите название Вашей организации:";
    }

    public String askProblemType() {
        return "🧰 Выберите тип проблемы кнопкой ниже.";
    }

    public String askDescription() {
        return "📋 Опишите вашу проблему подробно в одном сообщении.\n\nМожете указать кабинет, этаж, что именно сломалось и что нужно сделать.";
    }

    public String registrationCompleted() {
        return "✅ Регистрация завершена!\n\nТеперь вы можете создавать заявки на неисправности.";
    }

    public String ticketCreated(Ticket ticket) {
        return "✅ Заявка #" + ticket.getId() + " отправлена администраторам.\n\n"
                + "Я обязательно сообщу, когда её примут, отклонят или отметят исполненной.";
    }

    public String adminTicketCard(Ticket ticket) {
        return "🆕 НОВАЯ ЗАЯВКА #" + ticket.getId() + "\n\n"
                + "👤 ФИО: " + TextUtils.safe(ticket.getFullName()) + "\n"
                + "🏢 Компания: " + TextUtils.safe(ticket.getCompanyName()) + "\n"
                + "🛠 Тип: " + TextUtils.safe(ticket.getProblemType()) + "\n"
                + "📝 Описание: " + TextUtils.safe(ticket.getDescription()) + "\n\n"
                + "🆔 User ID: " + ticket.getRequesterId() + "\n"
                + "⏰ Время: " + timeFormatter.format(ticket.getCreatedAt()) + "\n"
                + "📌 Статус: " + statusLabel(ticket.getStatus());
    }

    public String ticketAcceptedForUser(Ticket ticket) {
        return "✅ Заявка #" + ticket.getId() + " принята в работу.\n\n"
                + "Администратор: " + TextUtils.safe(ticket.getAssignedAdminName()) + ".";
    }

    public String ticketRejectedForUser(Ticket ticket) {
        return "❌ Заявка #" + ticket.getId() + " отклонена.\n\n"
                + "Причина: " + TextUtils.safe(ticket.getRejectionReason());
    }

    public String ticketCompletedForUser(Ticket ticket) {
        return "🎉 Заявка #" + ticket.getId() + " отмечена как исполненная.\n\n"
                + "Если проблема ещё актуальна, просто создайте новую заявку, и я помогу снова.";
    }

    public String adminPanelIntro() {
        return "🛠 Админ панель\n\nВыберите раздел ниже. Я покажу заявки, пользователей, рассылку и управление администраторами.";
    }

    public String pendingTicketsHeader(int page, int totalPages, long totalElements) {
        return "📂 Неисполненные заявки\n\n"
                + "Всего открытых: " + totalElements + "\n"
                + "Страница: " + (page + 1) + " / " + Math.max(totalPages, 1);
    }

    public String usersHeader(int page, int totalPages, long totalElements) {
        return "👥 Пользователи\n\n"
                + "Всего пользователей: " + totalElements + "\n"
                + "Страница: " + (page + 1) + " / " + Math.max(totalPages, 1);
    }

    public String userCard(BotUser user, long ticketsCount) {
        return "👤 Пользователь " + user.getId() + "\n\n"
                + "Имя в MAX: " + TextUtils.safe(user.getDisplayName()) + "\n"
                + "ФИО: " + TextUtils.safe(user.getFullName()) + "\n"
                + "Компания: " + TextUtils.safe(user.getCompanyName()) + "\n"
                + "Username: " + TextUtils.safe(user.getUsername()) + "\n"
                + "Роль: " + (user.isAdmin() ? "Администратор" : "Пользователь") + "\n"
                + "Заявок: " + ticketsCount + "\n"
                + "Последняя активность: " + timeFormatter.format(user.getLastSeenAt());
    }

    public String askBroadcastText() {
        return "📣 Пришлите текст рассылки одним сообщением. Я отправлю его всем пользователям бота.";
    }

    public String askAdminId() {
        return "🔐 Пришлите `user_id` пользователя, которого нужно сделать администратором.";
    }

    public String adminGranted() {
        return "🛡 Вам выданы права администратора.\n\nТеперь у вас есть доступ к админ панели и обработке заявок.";
    }

    public String profileUpdated() {
        return "✅ Данные обновлены.\n\nТеперь можно сразу переходить к созданию новой заявки.";
    }

    public String genericHelp(boolean admin) {
        return admin
                ? "Я на связи. Используйте кнопки меню ниже: можно создать заявку, обновить данные или открыть админ панель."
                : "Я на связи. Используйте кнопки меню ниже: можно создать заявку или обновить ваши данные.";
    }

    public String recoveryMessage(boolean admin) {
        return admin
                ? "⚠️ Я поймал нестандартную ситуацию, но остаюсь на связи. Давайте продолжим с главного меню или админ панели."
                : "⚠️ Я поймал нестандартную ситуацию, но остаюсь на связи. Давайте продолжим с главного меню.";
    }

    public String statusLabel(TicketStatus status) {
        return switch (status) {
            case NEW -> "Новая";
            case ACCEPTED -> "Принята в работу";
            case REJECTED -> "Отклонена";
            case COMPLETED -> "Исполнена";
        };
    }
}
