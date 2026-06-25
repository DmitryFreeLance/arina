package ru.arina.maxbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.arina.maxbot.domain.BotUser;
import ru.arina.maxbot.domain.ConversationState;
import ru.arina.maxbot.domain.Ticket;
import ru.arina.maxbot.domain.TicketStatus;
import ru.arina.maxbot.max.InlineKeyboardAttachment;
import ru.arina.maxbot.max.InlineKeyboardButton;
import ru.arina.maxbot.max.MaxApiClient;
import ru.arina.maxbot.max.MaxIncomingUpdate;
import ru.arina.maxbot.repository.BotUserRepository;
import ru.arina.maxbot.util.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BotUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(BotUpdateHandler.class);

    private final UserService userService;
    private final TicketService ticketService;
    private final BotUserRepository botUserRepository;
    private final MaxApiClient maxApiClient;
    private final KeyboardFactory keyboardFactory;
    private final MessageFactory messageFactory;

    public BotUpdateHandler(UserService userService,
                            TicketService ticketService,
                            BotUserRepository botUserRepository,
                            MaxApiClient maxApiClient,
                            KeyboardFactory keyboardFactory,
                            MessageFactory messageFactory) {
        this.userService = userService;
        this.ticketService = ticketService;
        this.botUserRepository = botUserRepository;
        this.maxApiClient = maxApiClient;
        this.keyboardFactory = keyboardFactory;
        this.messageFactory = messageFactory;
    }

    @Transactional
    public void handle(JsonNode rawUpdate) {
        MaxIncomingUpdate update = new MaxIncomingUpdate(rawUpdate);
        String type = update.updateType();
        log.info("Received update type={}", type);

        if ("bot_started".equals(type)) {
            Long userId = Optional.ofNullable(update.actorUserId()).orElse(update.callbackUserId());
            if (userId != null) {
                BotUser user = userService.touchUser(userId, update.actorName(), update.actorUsername());
                sendMainMenu(user);
            }
            return;
        }

        if ("message_created".equals(type)) {
            handleIncomingText(update);
            return;
        }

        if ("message_callback".equals(type)) {
            handleCallback(update);
            return;
        }

        Long userId = Optional.ofNullable(update.actorUserId()).orElse(update.callbackUserId());
        if (userId != null) {
            BotUser user = userService.touchUser(userId, update.actorName(), update.actorUsername());
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.genericHelp(user.isAdmin()), keyboardFactory.mainMenu(user.isAdmin()));
        }
    }

    private void handleIncomingText(MaxIncomingUpdate update) {
        Long userId = update.actorUserId();
        if (userId == null) {
            return;
        }
        BotUser user = userService.touchUser(userId, update.actorName(), update.actorUsername());
        String text = Optional.ofNullable(update.messageText()).orElse("").trim();
        if (text.isEmpty()) {
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.genericHelp(user.isAdmin()), keyboardFactory.mainMenu(user.isAdmin()));
            return;
        }

        if (isStartCommand(text)) {
            user.setConversationState(ConversationState.IDLE);
            user.clearDraft();
            user.setListMode("NONE");
            botUserRepository.save(user);
            sendMainMenu(user);
            return;
        }

        if (isCancel(text)) {
            user.setConversationState(ConversationState.IDLE);
            user.clearDraft();
            user.setListMode("NONE");
            botUserRepository.save(user);
            maxApiClient.sendMessageToUser(user.getId(), "👌 Действие отменено. Возвращаю вас в главное меню.", keyboardFactory.mainMenu(user.isAdmin()));
            return;
        }

        if (handleStatefulMessage(user, text)) {
            return;
        }

        if ("Заявка на неисправность".equalsIgnoreCase(text)) {
            startTicketFlow(user);
            return;
        }

        if ("Обновить мои данные".equalsIgnoreCase(text)) {
            startProfileRefresh(user);
            return;
        }

        if ("Админ панель".equalsIgnoreCase(text) && user.isAdmin()) {
            showAdminMenu(user);
            return;
        }

        maxApiClient.sendMessageToUser(user.getId(), messageFactory.genericHelp(user.isAdmin()), keyboardFactory.mainMenu(user.isAdmin()));
    }

    private boolean handleStatefulMessage(BotUser user, String text) {
        switch (user.getConversationState()) {
            case WAITING_FULL_NAME -> {
                user.setDraftFullName(TextUtils.truncate(text, 255));
                user.setConversationState(ConversationState.WAITING_COMPANY);
                botUserRepository.save(user);
                maxApiClient.sendMessageToUser(user.getId(), messageFactory.askCompany(), keyboardFactory.cancelOnly());
                return true;
            }
            case WAITING_COMPANY -> {
                user.setDraftCompanyName(TextUtils.truncate(text, 255));
                if ("PROFILE".equals(user.getListMode())) {
                    user.setFullName(user.getDraftFullName());
                    user.setCompanyName(user.getDraftCompanyName());
                    user.setConversationState(ConversationState.IDLE);
                    user.clearDraft();
                    user.setListMode("NONE");
                    botUserRepository.save(user);
                    maxApiClient.sendMessageToUser(user.getId(), messageFactory.profileUpdated(), keyboardFactory.mainMenu(user.isAdmin()));
                    return true;
                }
                user.setConversationState(ConversationState.WAITING_PROBLEM_TYPE);
                botUserRepository.save(user);
                maxApiClient.sendMessageToUser(user.getId(), messageFactory.askProblemType(), keyboardFactory.issueTypes());
                return true;
            }
            case WAITING_PROBLEM_TYPE -> {
                user.setDraftProblemType(TextUtils.truncate(text, 255));
                user.setConversationState(ConversationState.WAITING_DESCRIPTION);
                botUserRepository.save(user);
                maxApiClient.sendMessageToUser(user.getId(), messageFactory.askDescription(), keyboardFactory.cancelOnly());
                return true;
            }
            case WAITING_DESCRIPTION -> {
                user.setDraftDescription(TextUtils.truncate(text, 4000));
                user.setFullName(user.getDraftFullName());
                user.setCompanyName(user.getDraftCompanyName());
                Ticket ticket = ticketService.create(user);
                user.setConversationState(ConversationState.IDLE);
                user.clearDraft();
                user.setListMode("NONE");
                botUserRepository.save(user);
                maxApiClient.sendMessageToUser(user.getId(), messageFactory.ticketCreated(ticket), keyboardFactory.mainMenu(user.isAdmin()));
                notifyAdminsAboutNewTicket(ticket);
                return true;
            }
            case WAITING_REJECTION_REASON -> {
                if (!user.isAdmin() || user.getActiveTicketId() == null) {
                    user.setConversationState(ConversationState.IDLE);
                    user.setActiveTicketId(null);
                    botUserRepository.save(user);
                    return true;
                }
                Ticket ticket = ticketService.findById(user.getActiveTicketId()).orElse(null);
                user.setConversationState(ConversationState.IDLE);
                user.setActiveTicketId(null);
                botUserRepository.save(user);
                if (ticket == null) {
                    maxApiClient.sendMessageToUser(user.getId(), "Не удалось найти заявку для отклонения.", keyboardFactory.adminMenu());
                    return true;
                }
                ticketService.reject(ticket, user, TextUtils.truncate(text, 2000));
                maxApiClient.sendMessageToUser(user.getId(), "❌ Заявка #" + ticket.getId() + " отклонена. Пользователь уведомлён.", keyboardFactory.adminMenu());
                sendUserNotification(ticket.getRequesterId(), messageFactory.ticketRejectedForUser(ticket));
                return true;
            }
            case WAITING_BROADCAST_TEXT -> {
                if (!user.isAdmin()) {
                    user.setConversationState(ConversationState.IDLE);
                    botUserRepository.save(user);
                    return true;
                }
                user.setConversationState(ConversationState.IDLE);
                botUserRepository.save(user);
                int delivered = broadcast(text);
                maxApiClient.sendMessageToUser(user.getId(), "📣 Рассылка завершена. Сообщение отправлено " + delivered + " пользователям.", keyboardFactory.adminMenu());
                return true;
            }
            case WAITING_ADMIN_ID -> {
                if (!user.isAdmin()) {
                    user.setConversationState(ConversationState.IDLE);
                    botUserRepository.save(user);
                    return true;
                }
                user.setConversationState(ConversationState.IDLE);
                botUserRepository.save(user);
                try {
                    long adminId = Long.parseLong(text.replaceAll("[^0-9]", ""));
                    BotUser candidate = userService.findById(adminId).orElseGet(() -> userService.touchUser(adminId, "Пользователь " + adminId, null));
                    boolean wasAdmin = candidate.isAdmin();
                    userService.promoteToAdmin(candidate);
                    maxApiClient.sendMessageToUser(user.getId(), "🛡 Пользователь " + adminId + " теперь администратор.", keyboardFactory.adminMenu());
                    if (!wasAdmin) {
                        maxApiClient.sendMessageToUser(candidate.getId(), messageFactory.adminGranted(), keyboardFactory.simpleAdminPanelButton());
                    }
                } catch (NumberFormatException ex) {
                    maxApiClient.sendMessageToUser(user.getId(), "Не удалось распознать user_id. Пришлите только цифры.", keyboardFactory.adminMenu());
                }
                return true;
            }
            case IDLE -> {
                return false;
            }
        }
        return false;
    }

    private void startTicketFlow(BotUser user) {
        user.clearDraft();
        user.setListMode("TICKET");
        user.setConversationState(ConversationState.WAITING_FULL_NAME);
        botUserRepository.save(user);
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.askFullName(), keyboardFactory.cancelOnly());
    }

    private void startProfileRefresh(BotUser user) {
        user.clearDraft();
        user.setListMode("PROFILE");
        user.setConversationState(ConversationState.WAITING_FULL_NAME);
        botUserRepository.save(user);
        maxApiClient.sendMessageToUser(user.getId(), "🔄 Обновим данные профиля.\n\n" + messageFactory.askFullName(), keyboardFactory.cancelOnly());
    }

    private void sendMainMenu(BotUser user) {
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.welcome(user), keyboardFactory.mainMenu(user.isAdmin()));
    }

    private void showAdminMenu(BotUser user) {
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.adminPanelIntro(), keyboardFactory.adminMenu());
    }

    private void handleCallback(MaxIncomingUpdate update) {
        Long userId = Optional.ofNullable(update.callbackUserId()).orElse(update.actorUserId());
        if (userId == null) {
            return;
        }
        BotUser user = userService.touchUser(userId, update.actorName(), update.actorUsername());
        String payload = Optional.ofNullable(update.callbackPayload()).orElse("");
        String callbackId = update.callbackId();
        if (!user.isAdmin()) {
            maxApiClient.answerCallback(callbackId, "Эта кнопка доступна только администраторам");
            return;
        }

        if ("admin:menu".equals(payload) || "admin:home".equals(payload)) {
            maxApiClient.answerCallback(callbackId, "Открываю админ панель");
            showAdminMenu(user);
            return;
        }

        if ("admin:broadcast".equals(payload)) {
            user.setConversationState(ConversationState.WAITING_BROADCAST_TEXT);
            botUserRepository.save(user);
            maxApiClient.answerCallback(callbackId, "Жду текст рассылки");
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.askBroadcastText(), keyboardFactory.cancelOnly());
            return;
        }

        if ("admin:add".equals(payload)) {
            user.setConversationState(ConversationState.WAITING_ADMIN_ID);
            botUserRepository.save(user);
            maxApiClient.answerCallback(callbackId, "Жду user_id нового администратора");
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.askAdminId(), keyboardFactory.cancelOnly());
            return;
        }

        if (payload.startsWith("admin:tickets:")) {
            int page = parsePage(payload);
            maxApiClient.answerCallback(callbackId, "Показываю заявки");
            renderOpenTickets(user, page);
            return;
        }

        if (payload.startsWith("admin:users:")) {
            int page = parsePage(payload);
            maxApiClient.answerCallback(callbackId, "Показываю пользователей");
            renderUsers(user, page);
            return;
        }

        if (payload.startsWith("ticket:view:")) {
            long ticketId = parseId(payload);
            maxApiClient.answerCallback(callbackId, "Открываю заявку");
            renderTicket(ticketId, user);
            return;
        }

        if (payload.startsWith("ticket:accept:")) {
            long ticketId = parseId(payload);
            onAcceptTicket(user, ticketId, callbackId);
            return;
        }

        if (payload.startsWith("ticket:reject:")) {
            long ticketId = parseId(payload);
            user.setConversationState(ConversationState.WAITING_REJECTION_REASON);
            user.setActiveTicketId(ticketId);
            botUserRepository.save(user);
            maxApiClient.answerCallback(callbackId, "Напишите причину отклонения");
            maxApiClient.sendMessageToUser(user.getId(), "✍️ Напишите причину отклонения для заявки #" + ticketId + ".", keyboardFactory.cancelOnly());
            return;
        }

        if (payload.startsWith("ticket:complete:")) {
            long ticketId = parseId(payload);
            onCompleteTicket(user, ticketId, callbackId);
            return;
        }

        if (payload.startsWith("user:view:")) {
            long targetUserId = parseId(payload);
            maxApiClient.answerCallback(callbackId, "Открываю карточку пользователя");
            renderUserCard(targetUserId, user);
            return;
        }

        if (payload.startsWith("user:promote:")) {
            long targetUserId = parseId(payload);
            onPromote(targetUserId, user, callbackId);
            return;
        }

        if (payload.startsWith("user:demote:")) {
            long targetUserId = parseId(payload);
            onDemote(targetUserId, user, callbackId);
            return;
        }

        maxApiClient.answerCallback(callbackId, "Команда не распознана");
        showAdminMenu(user);
    }

    private void renderOpenTickets(BotUser user, int page) {
        Page<Ticket> tickets = ticketService.pagedOpenTickets(Math.max(page, 0));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        tickets.getContent().forEach(ticket -> rows.add(List.of(
                InlineKeyboardButton.callback(
                        "#" + ticket.getId() + " | " + messageFactory.statusLabel(ticket.getStatus()) + " | " + TextUtils.truncate(ticket.getFullName(), 24),
                        "ticket:view:" + ticket.getId()
                )
        )));
        addPager(rows, "admin:tickets", tickets.getNumber(), tickets.getTotalPages());
        rows.add(List.of(InlineKeyboardButton.callback("В админ панель", "admin:menu")));
        maxApiClient.sendMessageToUser(
                user.getId(),
                messageFactory.pendingTicketsHeader(tickets.getNumber(), tickets.getTotalPages(), tickets.getTotalElements()),
                keyboardFactory.ticketList(rows)
        );
    }

    private void renderUsers(BotUser user, int page) {
        Page<BotUser> users = userService.pagedUsers(Math.max(page, 0));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        users.getContent().forEach(target -> rows.add(List.of(
                InlineKeyboardButton.callback(
                        target.getId() + " | " + (target.isAdmin() ? "админ" : "пользователь") + " | " + TextUtils.truncate(target.getDisplayName(), 18),
                        "user:view:" + target.getId()
                )
        )));
        addPager(rows, "admin:users", users.getNumber(), users.getTotalPages());
        rows.add(List.of(InlineKeyboardButton.callback("В админ панель", "admin:menu")));
        maxApiClient.sendMessageToUser(
                user.getId(),
                messageFactory.usersHeader(users.getNumber(), users.getTotalPages(), users.getTotalElements()),
                keyboardFactory.ticketList(rows)
        );
    }

    private void renderTicket(long ticketId, BotUser admin) {
        Ticket ticket = ticketService.findById(ticketId).orElse(null);
        if (ticket == null) {
            maxApiClient.sendMessageToUser(admin.getId(), "Заявка не найдена.", keyboardFactory.adminMenu());
            return;
        }
        maxApiClient.sendMessageToUser(
                admin.getId(),
                messageFactory.adminTicketCard(ticket),
                keyboardFactory.ticketActions(ticket.getId(), ticket.getStatus() == TicketStatus.ACCEPTED)
        );
    }

    private void renderUserCard(long targetUserId, BotUser admin) {
        BotUser target = userService.findById(targetUserId).orElse(null);
        if (target == null) {
            maxApiClient.sendMessageToUser(admin.getId(), "Пользователь не найден.", keyboardFactory.adminMenu());
            return;
        }
        maxApiClient.sendMessageToUser(
                admin.getId(),
                messageFactory.userCard(target, ticketService.countUserTickets(targetUserId)),
                keyboardFactory.userCard(target.getId(), target.isAdmin())
        );
    }

    private void onAcceptTicket(BotUser admin, long ticketId, String callbackId) {
        Ticket ticket = ticketService.findById(ticketId).orElse(null);
        if (ticket == null) {
            maxApiClient.answerCallback(callbackId, "Заявка не найдена");
            return;
        }
        ticketService.accept(ticket, admin);
        maxApiClient.answerCallback(callbackId, "Заявка принята");
        maxApiClient.sendMessageToUser(admin.getId(), "✅ Принято | Заявка #" + ticketId + " | Админ: " + admin.getId(), keyboardFactory.adminMenu());
        sendUserNotification(ticket.getRequesterId(), messageFactory.ticketAcceptedForUser(ticket));
    }

    private void onCompleteTicket(BotUser admin, long ticketId, String callbackId) {
        Ticket ticket = ticketService.findById(ticketId).orElse(null);
        if (ticket == null) {
            maxApiClient.answerCallback(callbackId, "Заявка не найдена");
            return;
        }
        ticketService.complete(ticket, admin);
        maxApiClient.answerCallback(callbackId, "Заявка отмечена исполненной");
        maxApiClient.sendMessageToUser(admin.getId(), "🎉 Заявка #" + ticketId + " отмечена исполненной.", keyboardFactory.adminMenu());
        sendUserNotification(ticket.getRequesterId(), messageFactory.ticketCompletedForUser(ticket));
    }

    private void onPromote(long targetUserId, BotUser actor, String callbackId) {
        BotUser target = userService.findById(targetUserId).orElse(null);
        if (target == null) {
            maxApiClient.answerCallback(callbackId, "Пользователь не найден");
            return;
        }
        boolean wasAdmin = target.isAdmin();
        userService.promoteToAdmin(target);
        maxApiClient.answerCallback(callbackId, wasAdmin ? "У пользователя уже есть права администратора" : "Права администратора выданы");
        renderUserCard(targetUserId, actor);
        if (!wasAdmin) {
            maxApiClient.sendMessageToUser(target.getId(), messageFactory.adminGranted(), keyboardFactory.simpleAdminPanelButton());
        }
    }

    private void onDemote(long targetUserId, BotUser actor, String callbackId) {
        if (targetUserId == actor.getId()) {
            maxApiClient.answerCallback(callbackId, "Нельзя снять права у самого себя");
            return;
        }
        BotUser target = userService.findById(targetUserId).orElse(null);
        if (target == null) {
            maxApiClient.answerCallback(callbackId, "Пользователь не найден");
            return;
        }
        userService.demoteFromAdmin(target);
        maxApiClient.answerCallback(callbackId, "Права администратора сняты");
        renderUserCard(targetUserId, actor);
        sendUserNotification(target.getId(), "ℹ️ Права администратора были сняты.");
    }

    private void notifyAdminsAboutNewTicket(Ticket ticket) {
        List<BotUser> admins = userService.admins();
        for (BotUser admin : admins) {
            maxApiClient.sendMessageToUser(
                    admin.getId(),
                    messageFactory.adminTicketCard(ticket),
                    keyboardFactory.ticketActions(ticket.getId(), false)
            );
        }
    }

    private int broadcast(String text) {
        List<BotUser> users = botUserRepository.findAll();
        int delivered = 0;
        for (BotUser target : users) {
            try {
                maxApiClient.sendMessageToUser(target.getId(), "📣 Сообщение от администрации\n\n" + text, keyboardFactory.mainMenu(target.isAdmin()));
                delivered++;
            } catch (Exception ex) {
                log.warn("Broadcast failed for user {}: {}", target.getId(), ex.getMessage());
            }
        }
        return delivered;
    }

    private void sendUserNotification(Long userId, String text) {
        BotUser target = userService.findById(userId).orElse(null);
        maxApiClient.sendMessageToUser(userId, text, keyboardFactory.mainMenu(target != null && target.isAdmin()));
    }

    private void addPager(List<List<InlineKeyboardButton>> rows, String prefix, int currentPage, int totalPages) {
        List<InlineKeyboardButton> pager = new ArrayList<>();
        if (currentPage > 0) {
            pager.add(InlineKeyboardButton.callback("⬅️ Назад", prefix + ":" + (currentPage - 1)));
        }
        if (currentPage + 1 < totalPages) {
            pager.add(InlineKeyboardButton.callback("Вперёд ➡️", prefix + ":" + (currentPage + 1)));
        }
        if (!pager.isEmpty()) {
            rows.add(pager);
        }
    }

    private int parsePage(String payload) {
        String[] parts = payload.split(":");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    private long parseId(String payload) {
        String[] parts = payload.split(":");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private boolean isStartCommand(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.equals("/start") || normalized.equals("start") || normalized.equals("начать");
    }

    private boolean isCancel(String text) {
        return "отмена".equalsIgnoreCase(text);
    }
}
