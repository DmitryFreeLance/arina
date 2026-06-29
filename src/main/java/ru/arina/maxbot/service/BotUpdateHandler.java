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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.Optional;

@Service
public class BotUpdateHandler {

    private static final Pattern TICKET_VIEW_PATTERN = Pattern.compile("^📄 Заявка #(\\d+).*$");
    private static final Pattern TICKET_ACCEPT_PATTERN = Pattern.compile("^✅ Принять заявку #(\\d+)$");
    private static final Pattern TICKET_REJECT_PATTERN = Pattern.compile("^❌ Отклонить заявку #(\\d+)$");
    private static final Pattern TICKET_COMPLETE_PATTERN = Pattern.compile("^🎉 Отметить исполненной #(\\d+)$");
    private static final Pattern USER_VIEW_PATTERN = Pattern.compile("^👤 Пользователь (\\d+).*$");
    private static final Pattern USER_PROMOTE_PATTERN = Pattern.compile("^🛡 Выдать админку (\\d+)$");
    private static final Pattern USER_DEMOTE_PATTERN = Pattern.compile("^🔓 Снять админку у (\\d+)$");
    private static final Pattern TICKETS_PAGE_PATTERN = Pattern.compile("^(?:⬅️ |➡️ )?📂 Страница заявок (\\d+)$");
    private static final Pattern USERS_PAGE_PATTERN = Pattern.compile("^(?:⬅️ |➡️ )?👥 Страница пользователей (\\d+)$");

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
                sendStartOrMenu(user);
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

    @Transactional
    public void handleFailure(JsonNode rawUpdate, Exception exception) {
        MaxIncomingUpdate update = new MaxIncomingUpdate(rawUpdate);
        Long userId = Optional.ofNullable(update.actorUserId()).orElse(update.callbackUserId());
        String callbackId = update.callbackId();

        log.warn("Update handling failed for type={}: {}", update.updateType(), exception.getMessage(), exception);

        if (StringUtils.hasText(callbackId)) {
            try {
                maxApiClient.answerCallback(callbackId, "Внутренняя ошибка, но бот уже восстановился");
            } catch (Exception callbackError) {
                log.warn("Could not answer failed callback {}: {}", callbackId, callbackError.getMessage());
            }
        }

        if (userId == null) {
            return;
        }

        try {
            BotUser user = userService.touchUser(userId, update.actorName(), update.actorUsername());
            user.setConversationState(ConversationState.IDLE);
            user.clearDraft();
            user.setListMode("NONE");
            user.setActiveTicketId(null);
            botUserRepository.save(user);
            maxApiClient.sendMessageToUser(
                    user.getId(),
                    messageFactory.recoveryMessage(user.isAdmin()),
                    keyboardFactory.mainMenu(user.isAdmin())
            );
        } catch (Exception notifyError) {
            log.warn("Could not notify user {} after failure: {}", userId, notifyError.getMessage());
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
            sendStartOrMenu(user);
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

        if ("📝 Заявка в УК".equalsIgnoreCase(text)) {
            startTicketFlow(user);
            return;
        }

        if ("👤 Обновить мои данные".equalsIgnoreCase(text)) {
            startProfileRefresh(user);
            return;
        }

        if ("🛠 Админ панель".equalsIgnoreCase(text) && user.isAdmin()) {
            showAdminMenu(user);
            return;
        }

        if ("🏠 Главное меню".equalsIgnoreCase(text)) {
            sendStartOrMenu(user);
            return;
        }

        if ("📂 Неисполненные заявки".equalsIgnoreCase(text) && user.isAdmin()) {
            renderOpenTickets(user, 0);
            return;
        }

        if ("👥 Пользователи".equalsIgnoreCase(text) && user.isAdmin()) {
            renderUsers(user, 0);
            return;
        }

        if ("📣 Рассылка".equalsIgnoreCase(text) && user.isAdmin()) {
            user.setConversationState(ConversationState.WAITING_BROADCAST_TEXT);
            botUserRepository.save(user);
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.askBroadcastText(), keyboardFactory.cancelOnly());
            return;
        }

        if ("📂 К списку заявок".equalsIgnoreCase(text) && user.isAdmin()) {
            renderOpenTickets(user, user.getListPage() == null ? 0 : user.getListPage());
            return;
        }

        if ("👥 К списку пользователей".equalsIgnoreCase(text) && user.isAdmin()) {
            renderUsers(user, user.getListPage() == null ? 0 : user.getListPage());
            return;
        }

        if (user.isAdmin() && handleAdminTextCommand(user, text)) {
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
                if ("REGISTRATION".equals(user.getListMode())) {
                    user.setFullName(user.getDraftFullName());
                    user.setCompanyName(user.getDraftCompanyName());
                    user.setConversationState(ConversationState.IDLE);
                    user.clearDraft();
                    user.setListMode("NONE");
                    botUserRepository.save(user);
                    maxApiClient.sendMessageToUser(user.getId(), messageFactory.registrationCompleted(), keyboardFactory.mainMenu(user.isAdmin()));
                    return true;
                }
                user.setConversationState(ConversationState.WAITING_PROBLEM_TYPE);
                botUserRepository.save(user);
                maxApiClient.sendMessageToUser(user.getId(), messageFactory.askProblemType(), keyboardFactory.issueTypes());
                return true;
            }
            case WAITING_PROBLEM_TYPE -> {
                user.setDraftProblemType(cleanProblemType(text));
                user.setConversationState(ConversationState.WAITING_DESCRIPTION);
                botUserRepository.save(user);
                maxApiClient.sendMessageToUser(user.getId(), messageFactory.askDescription(), keyboardFactory.cancelOnly());
                return true;
            }
            case WAITING_DESCRIPTION -> {
                user.setDraftDescription(TextUtils.truncate(text, 4000));
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
        if (!user.isRegistered()) {
            startRegistration(user);
            return;
        }
        user.clearDraft();
        user.setDraftFullName(user.getFullName());
        user.setDraftCompanyName(user.getCompanyName());
        user.setListMode("TICKET");
        user.setConversationState(ConversationState.WAITING_PROBLEM_TYPE);
        botUserRepository.save(user);
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.askProblemType(), keyboardFactory.issueTypes());
    }

    private void startProfileRefresh(BotUser user) {
        user.clearDraft();
        user.setListMode("PROFILE");
        user.setConversationState(ConversationState.WAITING_FULL_NAME);
        botUserRepository.save(user);
        maxApiClient.sendMessageToUser(user.getId(), "🔄 Обновим данные профиля.\n\n" + messageFactory.askFullName(), keyboardFactory.cancelOnly());
    }

    private void startRegistration(BotUser user) {
        user.clearDraft();
        user.setListMode("REGISTRATION");
        user.setConversationState(ConversationState.WAITING_FULL_NAME);
        botUserRepository.save(user);
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.askFullName(), keyboardFactory.cancelOnly());
    }

    private void sendStartOrMenu(BotUser user) {
        if (!user.isRegistered()) {
            user.setListMode("REGISTRATION");
            user.setConversationState(ConversationState.WAITING_FULL_NAME);
            botUserRepository.save(user);
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.welcome(user), keyboardFactory.cancelOnly());
            return;
        }
        sendMainMenu(user);
    }

    private void sendMainMenu(BotUser user) {
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.welcome(user), keyboardFactory.mainMenu(user.isAdmin()));
    }

    private void showAdminMenu(BotUser user) {
        maxApiClient.sendMessageToUser(user.getId(), messageFactory.adminPanelIntro(), keyboardFactory.adminMenu());
    }

    private boolean handleAdminTextCommand(BotUser user, String text) {
        Long ticketId = extractLong(text, TICKET_VIEW_PATTERN);
        if (ticketId != null) {
            renderTicket(ticketId, user);
            return true;
        }

        ticketId = extractLong(text, TICKET_ACCEPT_PATTERN);
        if (ticketId != null) {
            onAcceptTicket(user, ticketId, null);
            return true;
        }

        ticketId = extractLong(text, TICKET_REJECT_PATTERN);
        if (ticketId != null) {
            user.setConversationState(ConversationState.WAITING_REJECTION_REASON);
            user.setActiveTicketId(ticketId);
            botUserRepository.save(user);
            maxApiClient.sendMessageToUser(user.getId(), "✍️ Напишите причину отклонения для заявки #" + ticketId + ".", keyboardFactory.cancelOnly());
            return true;
        }

        ticketId = extractLong(text, TICKET_COMPLETE_PATTERN);
        if (ticketId != null) {
            onCompleteTicket(user, ticketId, null);
            return true;
        }

        Long targetUserId = extractLong(text, USER_VIEW_PATTERN);
        if (targetUserId != null) {
            renderUserCard(targetUserId, user);
            return true;
        }

        targetUserId = extractLong(text, USER_PROMOTE_PATTERN);
        if (targetUserId != null) {
            onPromote(targetUserId, user, null);
            return true;
        }

        targetUserId = extractLong(text, USER_DEMOTE_PATTERN);
        if (targetUserId != null) {
            onDemote(targetUserId, user, null);
            return true;
        }

        Long page = extractLong(text, TICKETS_PAGE_PATTERN);
        if (page != null) {
            renderOpenTickets(user, Math.max(0, page.intValue() - 1));
            return true;
        }

        page = extractLong(text, USERS_PAGE_PATTERN);
        if (page != null) {
            renderUsers(user, Math.max(0, page.intValue() - 1));
            return true;
        }

        return false;
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
            maxApiClient.answerCallback(callbackId, null);
            return;
        }

        if ("admin:menu".equals(payload) || "admin:home".equals(payload)) {
            maxApiClient.answerCallback(callbackId, null);
            showAdminMenu(user);
            return;
        }

        if ("admin:broadcast".equals(payload)) {
            user.setConversationState(ConversationState.WAITING_BROADCAST_TEXT);
            botUserRepository.save(user);
            maxApiClient.answerCallback(callbackId, null);
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.askBroadcastText(), keyboardFactory.cancelOnly());
            return;
        }

        if ("admin:add".equals(payload)) {
            user.setConversationState(ConversationState.WAITING_ADMIN_ID);
            botUserRepository.save(user);
            maxApiClient.answerCallback(callbackId, null);
            maxApiClient.sendMessageToUser(user.getId(), messageFactory.askAdminId(), keyboardFactory.cancelOnly());
            return;
        }

        if (payload.startsWith("admin:tickets:")) {
            int page = parsePage(payload);
            maxApiClient.answerCallback(callbackId, null);
            renderOpenTickets(user, page);
            return;
        }

        if (payload.startsWith("admin:users:")) {
            int page = parsePage(payload);
            maxApiClient.answerCallback(callbackId, null);
            renderUsers(user, page);
            return;
        }

        if (payload.startsWith("ticket:view:")) {
            long ticketId = parseId(payload);
            maxApiClient.answerCallback(callbackId, null);
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
            maxApiClient.answerCallback(callbackId, null);
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
            maxApiClient.answerCallback(callbackId, null);
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

        maxApiClient.answerCallback(callbackId, null);
        showAdminMenu(user);
    }

    private void renderOpenTickets(BotUser user, int page) {
        user.setListPage(page);
        botUserRepository.save(user);
        Page<Ticket> tickets = ticketService.pagedOpenTickets(Math.max(page, 0));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        tickets.getContent().forEach(ticket -> rows.add(List.of(
                InlineKeyboardButton.message(
                        "📄 Заявка #" + ticket.getId() + " | " + messageFactory.statusLabel(ticket.getStatus()) + " | " + TextUtils.truncate(ticket.getFullName(), 24)
                )
        )));
        addPager(rows, "tickets", tickets.getNumber(), tickets.getTotalPages());
        rows.add(List.of(InlineKeyboardButton.message("🛠 Админ панель")));
        maxApiClient.sendMessageToUser(
                user.getId(),
                messageFactory.pendingTicketsHeader(tickets.getNumber(), tickets.getTotalPages(), tickets.getTotalElements()),
                keyboardFactory.ticketList(rows)
        );
    }

    private void renderUsers(BotUser user, int page) {
        user.setListPage(page);
        botUserRepository.save(user);
        Page<BotUser> users = userService.pagedUsers(Math.max(page, 0));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        users.getContent().forEach(target -> rows.add(List.of(
                InlineKeyboardButton.message(
                        "👤 Пользователь " + target.getId() + " | " + (target.isAdmin() ? "админ" : "пользователь") + " | " + TextUtils.truncate(target.getDisplayName(), 18)
                )
        )));
        addPager(rows, "users", users.getNumber(), users.getTotalPages());
        rows.add(List.of(InlineKeyboardButton.message("🛠 Админ панель")));
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
            maxApiClient.answerCallback(callbackId, null);
            return;
        }

        if (ticket.getStatus() == TicketStatus.ACCEPTED) {
            maxApiClient.answerCallback(callbackId, null);
            if (admin.getId().equals(ticket.getAssignedAdminId())) {
                maxApiClient.sendMessageToUser(admin.getId(), messageFactory.ticketAlreadyAcceptedByYou(ticket), keyboardFactory.adminMenu());
            } else {
                maxApiClient.sendMessageToUser(admin.getId(), messageFactory.ticketAlreadyAccepted(ticket), keyboardFactory.adminMenu());
            }
            return;
        }

        if (ticket.getStatus() == TicketStatus.REJECTED || ticket.getStatus() == TicketStatus.COMPLETED) {
            maxApiClient.answerCallback(callbackId, null);
            maxApiClient.sendMessageToUser(
                    admin.getId(),
                    "ℹ️ Заявка #" + ticket.getId() + " уже имеет статус: " + messageFactory.statusLabel(ticket.getStatus()) + ".",
                    keyboardFactory.adminMenu()
            );
            return;
        }

        ticketService.accept(ticket, admin);
        maxApiClient.answerCallback(callbackId, null);
        maxApiClient.sendMessageToUser(admin.getId(), "✅ Принято | Заявка #" + ticketId + " | Админ: " + admin.getId(), keyboardFactory.adminMenu());
        sendUserNotification(ticket.getRequesterId(), messageFactory.ticketAcceptedForUser(ticket));
        notifyOtherAdminsAboutAcceptance(ticket, admin);
    }

    private void onCompleteTicket(BotUser admin, long ticketId, String callbackId) {
        Ticket ticket = ticketService.findById(ticketId).orElse(null);
        if (ticket == null) {
            maxApiClient.answerCallback(callbackId, null);
            return;
        }
        ticketService.complete(ticket, admin);
        maxApiClient.answerCallback(callbackId, null);
        maxApiClient.sendMessageToUser(admin.getId(), "🎉 Заявка #" + ticketId + " отмечена исполненной.", keyboardFactory.adminMenu());
        sendUserNotification(ticket.getRequesterId(), messageFactory.ticketCompletedForUser(ticket));
    }

    private void onPromote(long targetUserId, BotUser actor, String callbackId) {
        BotUser target = userService.findById(targetUserId).orElse(null);
        if (target == null) {
            maxApiClient.answerCallback(callbackId, null);
            return;
        }
        boolean wasAdmin = target.isAdmin();
        userService.promoteToAdmin(target);
        maxApiClient.answerCallback(callbackId, null);
        renderUserCard(targetUserId, actor);
        if (!wasAdmin) {
            maxApiClient.sendMessageToUser(target.getId(), messageFactory.adminGranted(), keyboardFactory.simpleAdminPanelButton());
        }
    }

    private void onDemote(long targetUserId, BotUser actor, String callbackId) {
        if (targetUserId == actor.getId()) {
            maxApiClient.answerCallback(callbackId, null);
            return;
        }
        BotUser target = userService.findById(targetUserId).orElse(null);
        if (target == null) {
            maxApiClient.answerCallback(callbackId, null);
            return;
        }
        userService.demoteFromAdmin(target);
        maxApiClient.answerCallback(callbackId, null);
        renderUserCard(targetUserId, actor);
        sendUserNotification(target.getId(), "ℹ️ Права администратора были сняты.");
    }

    private void notifyAdminsAboutNewTicket(Ticket ticket) {
        List<BotUser> admins = userService.admins();
        for (BotUser admin : admins) {
            try {
                maxApiClient.sendMessageToUser(
                        admin.getId(),
                        messageFactory.adminTicketCard(ticket),
                        keyboardFactory.ticketActions(ticket.getId(), false)
                );
            } catch (Exception ex) {
                log.warn("Could not notify admin {} about ticket {}: {}", admin.getId(), ticket.getId(), ex.getMessage());
            }
        }
    }

    private void notifyOtherAdminsAboutAcceptance(Ticket ticket, BotUser actor) {
        List<BotUser> admins = userService.admins();
        for (BotUser admin : admins) {
            if (admin.getId().equals(actor.getId())) {
                continue;
            }
            try {
                maxApiClient.sendMessageToUser(
                        admin.getId(),
                        messageFactory.ticketAcceptedForAdmins(ticket),
                        keyboardFactory.adminMenu()
                );
            } catch (Exception ex) {
                log.warn("Could not notify admin {} about accepted ticket {}: {}", admin.getId(), ticket.getId(), ex.getMessage());
            }
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
        if (currentPage > 0) {
            String label = prefix.equals("tickets") ? "📂 Страница заявок " + currentPage : "👥 Страница пользователей " + currentPage;
            rows.add(List.of(InlineKeyboardButton.message("⬅️ " + label)));
        }
        if (currentPage + 1 < totalPages) {
            String label = prefix.equals("tickets") ? "📂 Страница заявок " + (currentPage + 2) : "👥 Страница пользователей " + (currentPage + 2);
            rows.add(List.of(InlineKeyboardButton.message("➡️ " + label)));
        }
    }

    private Long extractLong(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    private String cleanProblemType(String text) {
        return TextUtils.truncate(text.replaceFirst("^[^\\p{L}\\p{N}]+\\s*", "").trim(), 255);
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
