package ru.arina.maxbot.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.arina.maxbot.config.BotProperties;
import ru.arina.maxbot.domain.BotUser;
import ru.arina.maxbot.domain.ConversationState;
import ru.arina.maxbot.domain.UserRole;
import ru.arina.maxbot.repository.BotUserRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final BotUserRepository botUserRepository;
    private final BotProperties botProperties;

    public UserService(BotUserRepository botUserRepository, BotProperties botProperties) {
        this.botUserRepository = botUserRepository;
        this.botProperties = botProperties;
    }

    @Transactional
    public BotUser touchUser(Long userId, String displayName, String username) {
        BotUser user = botUserRepository.findById(userId).orElseGet(() -> {
            BotUser newUser = new BotUser();
            newUser.setId(userId);
            return newUser;
        });
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        if (username != null && !username.isBlank()) {
            user.setUsername(username);
        }
        user.setLastSeenAt(Instant.now());
        return botUserRepository.save(user);
    }

    public Optional<BotUser> findById(Long userId) {
        return botUserRepository.findById(userId);
    }

    public List<BotUser> admins() {
        return botUserRepository.findByRole(UserRole.ADMIN);
    }

    public Page<BotUser> pagedUsers(int page) {
        return botUserRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, botProperties.getPageSize()));
    }

    @Transactional
    public void promoteToAdmin(BotUser user) {
        user.setRole(UserRole.ADMIN);
        botUserRepository.save(user);
    }

    @Transactional
    public void demoteFromAdmin(BotUser user) {
        user.setRole(UserRole.USER);
        if (user.getConversationState() == ConversationState.WAITING_ADMIN_ID
                || user.getConversationState() == ConversationState.WAITING_BROADCAST_TEXT
                || user.getConversationState() == ConversationState.WAITING_REJECTION_REASON) {
            user.setConversationState(ConversationState.IDLE);
        }
        botUserRepository.save(user);
    }

    @Transactional
    public void applyBootstrapAdmins() {
        if (botProperties.getAdminIds() == null || botProperties.getAdminIds().isBlank()) {
            return;
        }
        Arrays.stream(botProperties.getAdminIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .forEach(id -> {
                    BotUser user = botUserRepository.findById(id).orElseGet(() -> {
                        BotUser created = new BotUser();
                        created.setId(id);
                        created.setDisplayName("Администратор " + id);
                        return created;
                    });
                    user.setRole(UserRole.ADMIN);
                    botUserRepository.save(user);
                });
    }
}
