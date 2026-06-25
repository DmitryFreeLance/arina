package ru.arina.maxbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private String token;
    private String apiBaseUrl;
    private String companyName;
    private String adminIds;
    private boolean notifyOnStartup;
    private int pageSize = 8;
    private long pollingDelayMs = 1500;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getAdminIds() {
        return adminIds;
    }

    public void setAdminIds(String adminIds) {
        this.adminIds = adminIds;
    }

    public boolean isNotifyOnStartup() {
        return notifyOnStartup;
    }

    public void setNotifyOnStartup(boolean notifyOnStartup) {
        this.notifyOnStartup = notifyOnStartup;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getPollingDelayMs() {
        return pollingDelayMs;
    }

    public void setPollingDelayMs(long pollingDelayMs) {
        this.pollingDelayMs = pollingDelayMs;
    }
}
