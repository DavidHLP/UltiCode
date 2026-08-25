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

        /**
         * Public base URL returned to clients; defaults to {endpoint}/{bucket}.
         * Set it when a CDN or separate public host fronts the bucket.
         */
        private String publicBaseUrl;

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

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
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
            if (s3.bucket == null || s3.bucket.isBlank()) {
                throw new IllegalStateException("app.storage.s3.bucket is required when app.storage.type=s3.");
            }
            if (s3.accessKey == null || s3.accessKey.isBlank()
                    || s3.secretKey == null || s3.secretKey.isBlank()) {
                throw new IllegalStateException(
                        "app.storage.s3.access-key/secret-key are required when app.storage.type=s3.");
            }
        }
    }
}
