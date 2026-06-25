package ru.arina.maxbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.arina.maxbot.config.BotProperties;
import ru.arina.maxbot.service.BotUpdateHandler;

@RestController
@RequestMapping
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final BotUpdateHandler botUpdateHandler;
    private final BotProperties botProperties;

    public WebhookController(BotUpdateHandler botUpdateHandler, BotProperties botProperties) {
        this.botUpdateHandler = botUpdateHandler;
        this.botProperties = botProperties;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body,
                                        @RequestHeader(name = "X-Max-Bot-Api-Secret", required = false) String secret) {
        if (StringUtils.hasText(botProperties.getWebhookSecret()) && !botProperties.getWebhookSecret().equals(secret)) {
            log.warn("Rejected webhook because secret header did not match");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        botUpdateHandler.handle(body);
        return ResponseEntity.ok().build();
    }
}
