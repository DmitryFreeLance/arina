package ru.arina.maxbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class AppConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15_000);
        requestFactory.setReadTimeout(30_000);
        return builder.requestFactory(requestFactory).build();
    }

    @Bean
    ZoneId zoneId() {
        return ZoneId.of("Asia/Novosibirsk");
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
