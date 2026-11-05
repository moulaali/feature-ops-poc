package com.example.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unleash")
public record UnleashConfigProperties(
        String apiUrl,
        String apiKey,
        String adminToken,
        String appName,
        String instanceId,
        long fetchTogglesIntervalSeconds
) {
    public UnleashConfigProperties {
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "http://localhost:4242/api";
        }
        if (apiKey == null || apiKey.isBlank()) {
            // Clearly identified local-only demo backend token
            apiKey = "default:development.local-demo-backend-token-for-java-service";
        }
        if (adminToken == null || adminToken.isBlank()) {
            adminToken = "*:*.local-demo-admin-token-not-for-production";
        }
        if (appName == null || appName.isBlank()) {
            appName = "search-service";
        }
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = "search-service-local-1";
        }
        if (fetchTogglesIntervalSeconds <= 0) {
            fetchTogglesIntervalSeconds = 5;
        }
    }
}
