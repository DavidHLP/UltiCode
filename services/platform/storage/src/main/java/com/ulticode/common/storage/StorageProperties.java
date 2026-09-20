package com.ulticode.common.storage;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Configuration for the mandatory S3-compatible object store. */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties implements InitializingBean {
    public static final String TYPE_S3 = "s3";

    private String type = TYPE_S3;
    private final S3 s3 = new S3();
    private final StartupProbe startupProbe = new StartupProbe();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public S3 getS3() {
        return s3;
    }

    public StartupProbe getStartupProbe() {
        return startupProbe;
    }

    public boolean isS3() {
        return TYPE_S3.equals(type);
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    void validate() {
        if (type == null || !TYPE_S3.equalsIgnoreCase(type.trim())) {
            if ("local".equalsIgnoreCase(type)) {
                throw new IllegalStateException(
                        "app.storage.type=local is unsupported: LocalStorage was removed; object storage is mandatory.");
            }
            throw new IllegalStateException("Invalid app.storage.type='" + type + "'; only 's3' is accepted.");
        }
        type = TYPE_S3;

        List<String> missing = new ArrayList<>();
        if (blank(s3.endpoint)) {
            missing.add("app.storage.s3.endpoint");
        }
        if (blank(s3.region)) {
            missing.add("app.storage.s3.region");
        }
        if (blank(s3.bucket)) {
            missing.add("app.storage.s3.bucket");
        }
        if (blank(s3.accessKey)) {
            missing.add("app.storage.s3.access-key");
        }
        if (blank(s3.secretKey)) {
            missing.add("app.storage.s3.secret-key");
        }
        if (s3.tlsEnabled == null) {
            missing.add("app.storage.s3.tls-enabled");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required object-storage properties: " + String.join(", ", missing));
        }

        URI endpoint;
        try {
            endpoint = URI.create(s3.endpoint.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.storage.s3.endpoint must be a valid URI.", exception);
        }
        String scheme = endpoint.getScheme();
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean loopbackHttp = "http".equalsIgnoreCase(scheme) && isLoopbackHost(endpoint.getHost());
        if (s3.tlsEnabled && !https) {
            throw new IllegalStateException("app.storage.s3.endpoint must use https when tls-enabled=true.");
        }
        if (!s3.tlsEnabled && !loopbackHttp) {
            throw new IllegalStateException(
                    "app.storage.s3.tls-enabled=false is only allowed for a loopback http endpoint.");
        }
        if (endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new IllegalStateException("app.storage.s3.endpoint must include a host.");
        }
        if (s3.connectTimeoutMs < 100 || s3.connectTimeoutMs > 30_000
                || s3.requestTimeoutMs < 100 || s3.requestTimeoutMs > 120_000) {
            throw new IllegalStateException("S3 connect/request timeout is outside the supported range.");
        }
        if (s3.maxConcurrentRequests < 1 || s3.maxConcurrentRequests > 128) {
            throw new IllegalStateException("S3 max concurrent requests must be between 1 and 128.");
        }
        if (startupProbe.attempts < 1 || startupProbe.delayMs < 0) {
            throw new IllegalStateException("S3 startup probe attempts must be positive and delay-ms cannot be negative.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized);
    }

    public static class S3 {
        private String endpoint;
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private Boolean tlsEnabled;
        private int connectTimeoutMs = 10_000;
        private int requestTimeoutMs = 30_000;
        private int maxConcurrentRequests = 16;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public Boolean getTlsEnabled() { return tlsEnabled; }
        public boolean isTlsEnabled() { return Boolean.TRUE.equals(tlsEnabled); }
        public void setTlsEnabled(Boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) { this.maxConcurrentRequests = maxConcurrentRequests; }
    }

    public static class StartupProbe {
        private boolean enabled = true;
        private int attempts = 30;
        private long delayMs = 2_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    }
}
