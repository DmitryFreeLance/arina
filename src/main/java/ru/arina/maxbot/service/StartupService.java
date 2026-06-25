package ru.arina.maxbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.arina.maxbot.config.BotProperties;
import ru.arina.maxbot.max.MaxApiClient;

@Service
public class StartupService {

    private static final Logger log = LoggerFactory.getLogger(StartupService.class);

    private final UserService userService;
    private final MaxApiClient maxApiClient;
    private final BotProperties botProperties;

    public StartupService(UserService userService, MaxApiClient maxApiClient, BotProperties botProperties) {
        this.userService = userService;
        this.maxApiClient = maxApiClient;
        this.botProperties = botProperties;
    }

    @PostConstruct
    public void init() {
        userService.applyBootstrapAdmins();
        try {
            JsonNode me = maxApiClient.getMe();
            log.info("Connected to MAX bot {} ({})", me.path("name").asText(), me.path("user_id").asLong());
        } catch (Exception ex) {
            log.warn("Could not fetch bot profile from MAX API: {}", ex.getMessage());
        }
        try {
            maxApiClient.registerWebhook();
        } catch (Exception ex) {
            log.warn("Webhook registration failed: {}", ex.getMessage());
        }
        if (botProperties.isNotifyOnStartup()) {
            log.info("Startup notification flag is enabled");
        }
    }
}
