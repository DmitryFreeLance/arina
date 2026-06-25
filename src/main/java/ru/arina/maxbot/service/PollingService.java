package ru.arina.maxbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.arina.maxbot.config.BotProperties;
import ru.arina.maxbot.max.MaxApiClient;

@Service
public class PollingService {

    private static final Logger log = LoggerFactory.getLogger(PollingService.class);

    private final UserService userService;
    private final MaxApiClient maxApiClient;
    private final BotProperties botProperties;
    private final BotUpdateHandler botUpdateHandler;

    private volatile Long marker;
    private volatile int consecutiveFailures;

    public PollingService(UserService userService,
                          MaxApiClient maxApiClient,
                          BotProperties botProperties,
                          BotUpdateHandler botUpdateHandler) {
        this.userService = userService;
        this.maxApiClient = maxApiClient;
        this.botProperties = botProperties;
        this.botUpdateHandler = botUpdateHandler;
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

        Thread pollingThread = new Thread(this::pollLoop, "max-long-polling");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    private void pollLoop() {
        log.info("Starting MAX long polling loop");
        while (true) {
            try {
                JsonNode response = maxApiClient.getUpdates(marker);
                consecutiveFailures = 0;
                JsonNode updates = response.path("updates");
                if (updates.isArray()) {
                    for (JsonNode update : updates) {
                        try {
                            botUpdateHandler.handle(update);
                        } catch (Exception updateEx) {
                            botUpdateHandler.handleFailure(update, updateEx);
                        }
                    }
                }
                if (response.hasNonNull("marker")) {
                    marker = response.get("marker").asLong();
                }
            } catch (Exception ex) {
                consecutiveFailures++;
                log.warn("Long polling request failed ({}): {}", consecutiveFailures, ex.getMessage());
            }

            try {
                Thread.sleep(botProperties.getPollingDelayMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Long polling thread interrupted");
                return;
            }
        }
    }
}
