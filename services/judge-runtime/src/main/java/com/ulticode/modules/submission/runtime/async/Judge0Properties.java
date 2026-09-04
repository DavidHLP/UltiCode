package com.ulticode.modules.submission.runtime.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Optional, deployment-only configuration for the Judge0 Adapter. */
@ConfigurationProperties(prefix = "judge0")
public class Judge0Properties {

    private boolean enabled;
    private String endpoint;
    private String apiKey;
    private String apiKeyHeader = "X-Auth-Token";
    private long pollIntervalMs = 250;
    private long requestTimeoutMs = 10_000;
    private int maxOutputBytes = 65_536;
    private final Map<String, Integer> languageIds = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public Map<String, Integer> getLanguageIds() {
        return languageIds;
    }

    public int languageId(String language) {
        if (language == null) {
            throw new IllegalArgumentException("language is required");
        }
        Integer id = languageIds.get(language.trim().toLowerCase(Locale.ROOT));
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Judge0 language mapping is missing");
        }
        return id;
    }

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("judge0.endpoint is required when Judge0 is enabled");
        }
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("judge0.endpoint must be a valid URI", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("judge0.endpoint must use https");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("judge0.api-key is required when Judge0 is enabled");
        }
        if (apiKeyHeader == null || apiKeyHeader.isBlank()
                || pollIntervalMs < 0 || requestTimeoutMs < 100
                || maxOutputBytes < 1 || maxOutputBytes > 8 * 1024 * 1024) {
            throw new IllegalStateException("Judge0 timeout/output configuration is invalid");
        }
        languageIds.forEach((language, id) -> {
            if (language == null || language.isBlank() || id == null || id < 1) {
                throw new IllegalStateException("Judge0 language mappings must be non-empty and positive");
            }
        });
        if (languageIds.isEmpty()) {
            throw new IllegalStateException("judge0.language-ids is required when Judge0 is enabled");
        }
    }
}
