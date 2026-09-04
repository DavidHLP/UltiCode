package com.ulticode.app.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binary-storage configuration ({@code app.storage.*}).
 *
 * <p>{@code type=local} (default) keeps the legacy behavior: objects are
 * written under {@code local.root-dir} and addressed through
 * {@code local.public-url-prefix}. {@code type=s3} pushes objects to an
 * S3-compatible object store (AWS S3, MinIO, ...); credentials come from
 * environment variables, never from source control.
 */
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    public static final String TYPE_LOCAL = "local";
    public static final String TYPE_S3 = "s3";

    private String type = TYPE_LOCAL;
    private final Local local = new Local();
    private final S3 s3 = new S3();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Local getLocal() {
        return local;
    }

    public S3 getS3() {
        return s3;
    }

    public boolean isS3() {
        return TYPE_S3.equals(type);
    }

    public static class Local {

        /** Root directory; relative paths resolve against the working directory. */
        private String rootDir = "uploads";

        /** URL prefix returned to clients; must stay aligned with how files are served. */
        private String publicUrlPrefix = "/uploads";

        public String getRootDir() {
            return rootDir;
        }

        public void setRootDir(String rootDir) {
            this.rootDir = rootDir;
        }

        public String getPublicUrlPrefix() {
            return publicUrlPrefix;
        }

        public void setPublicUrlPrefix(String publicUrlPrefix) {
            this.publicUrlPrefix = publicUrlPrefix;
        }
    }

        public static class S3 {

            /** Base endpoint; path-style addressing is used ({endpoint}/{bucket}/{key}). */
            private String endpoint;

            private String region = "us-east-1";

            private String bucket;

            private String accessKey;

            private String secretKey;

            /** Require an HTTPS endpoint for managed/public object storage. */
            private boolean tlsEnabled;

        /**
         * Public base URL returned to clients; defaults to {endpoint}/{bucket}.
         * Set it when a CDN or separate public host fronts the bucket.
         */
        private String publicBaseUrl;

        private int connectTimeoutMs = 10_000;

        private int requestTimeoutMs = 30_000;

        private int maxConcurrentRequests = 16;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
        public boolean isTlsEnabled() {
            return tlsEnabled;
        }

        public void setTlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        public int getMaxConcurrentRequests() {
            return maxConcurrentRequests;
        }

        public void setMaxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
        }
    }

    @PostConstruct
    void validate() {
        if (!TYPE_LOCAL.equals(type) && !TYPE_S3.equals(type)) {
            throw new IllegalStateException(
                    "Invalid app.storage.type='" + type + "'; expected 'local' or 's3'.");
        }
        if (isS3()) {
            if (s3.endpoint == null || s3.endpoint.isBlank()) {
                throw new IllegalStateException("app.storage.s3.endpoint is required when app.storage.type=s3.");
            }
            String endpoint = s3.endpoint.trim();
            java.net.URI endpointUri;
            try {
                endpointUri = java.net.URI.create(endpoint);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("app.storage.s3.endpoint must be a valid URI.", exception);
            }
            String scheme = endpointUri.getScheme();
            boolean https = "https".equalsIgnoreCase(scheme);
            boolean loopbackHttp = "http".equalsIgnoreCase(scheme)
                    && isLoopbackHost(endpointUri.getHost());
            if (s3.tlsEnabled && !https) {
                throw new IllegalStateException(
                        "app.storage.s3.endpoint must use https when tls-enabled=true.");
            }
            if (!https && !loopbackHttp) {
                throw new IllegalStateException(
                        "app.storage.s3.endpoint must use https unless it is loopback HTTP for local development.");
            }
            if (s3.bucket == null || s3.bucket.isBlank()) {
                throw new IllegalStateException("app.storage.s3.bucket is required when app.storage.type=s3.");
            }
            if (s3.accessKey == null || s3.accessKey.isBlank()
                    || s3.secretKey == null || s3.secretKey.isBlank()) {
                throw new IllegalStateException(
                        "app.storage.s3.access-key/secret-key are required when app.storage.type=s3.");
            }
            if (s3.connectTimeoutMs < 100 || s3.connectTimeoutMs > 30_000
                    || s3.requestTimeoutMs < 100 || s3.requestTimeoutMs > 120_000) {
                throw new IllegalStateException("S3 connect/request timeout is outside the supported range.");
            }
            if (s3.maxConcurrentRequests < 1 || s3.maxConcurrentRequests > 128) {
                throw new IllegalStateException("S3 max concurrent requests must be between 1 and 128.");
            }
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized);
    }
}
