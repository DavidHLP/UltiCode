package com.ulticode.core;

import org.springframework.core.env.Environment;

import java.util.List;

/** Builds the unchanged child-context property list for an Owner module. */
final class CoreOwnerBootProperties {
    private final Environment environment;

    CoreOwnerBootProperties(Environment environment) {
        this.environment = environment;
    }

    List<String> forModule(CoreModuleDefinition module) {
        String prefix = module.environmentPrefix();
        boolean admin = "admin".equals(module.name());
        boolean search = "search".equals(module.name());
        String storageAccessKey = admin
                ? requiredProperty("RUSTFS_ADMIN_ACCESS_KEY")
                : requiredProperty("RUSTFS_APP_ACCESS_KEY", "APP_STORAGE_S3_ACCESS_KEY");
        String storageSecretKey = admin
                ? requiredProperty("RUSTFS_ADMIN_SECRET_KEY")
                : requiredProperty("RUSTFS_APP_SECRET_KEY", "APP_STORAGE_S3_SECRET_KEY");
        String redisUsername = property(
                prefix + "_REDIS_USERNAME", "ulticode-" + module.name());
        String redisPassword = requiredProperty(
                prefix + "_REDIS_PASSWORD", "REDIS_PASSWORD");
        List<String> properties = new java.util.ArrayList<>(List.of(
                "spring.application.name=ulticode-core-" + module.name(),
                "spring.main.web-application-type=none",
                "spring.main.banner-mode=off",
                "spring.main.lazy-initialization=" + property(
                        "spring.main.lazy-initialization", "false"),
                "ulticode.app.inbox.enabled=" + property(
                        "ulticode.app.inbox.enabled", admin ? "false" : "true"),
                "spring.main.allow-bean-definition-overriding=false",
                "spring.flyway.enabled=false",
                "app.storage.type=" + property(
                        "APP_STORAGE_TYPE", "s3"),
                "app.storage.s3.endpoint=" + requiredProperty(
                        "APP_STORAGE_S3_ENDPOINT"),
                "app.storage.s3.region=" + requiredProperty(
                        "APP_STORAGE_S3_REGION"),
                "app.storage.s3.bucket=" + requiredProperty(
                        "APP_STORAGE_S3_BUCKET"),
                "app.storage.s3.access-key=" + storageAccessKey,
                "app.storage.s3.secret-key=" + storageSecretKey,
                "app.storage.s3.tls-enabled=" + requiredProperty(
                        "APP_STORAGE_S3_TLS_ENABLED"),
                "app.storage.s3.ca-certificate-path=" + property(
                        "APP_STORAGE_S3_CA_CERTIFICATE", ""),
                "app.storage.s3.connect-timeout-ms=" + property(
                        "APP_STORAGE_S3_CONNECT_TIMEOUT_MS", "10000"),
                "app.storage.s3.request-timeout-ms=" + property(
                        "APP_STORAGE_S3_REQUEST_TIMEOUT_MS", "30000"),
                "app.storage.s3.upload-timeout-ms=" + property(
                        "APP_STORAGE_S3_UPLOAD_TIMEOUT_MS", "1800000"),
                "app.storage.s3.max-concurrent-requests=" + property(
                        "APP_STORAGE_S3_MAX_CONCURRENT_REQUESTS", "16"),
                "app.storage.startup-probe.enabled=" + property(
                        "APP_STORAGE_STARTUP_PROBE_ENABLED", "true"),
                "app.storage.startup-probe.attempts=" + property(
                        "APP_STORAGE_STARTUP_PROBE_ATTEMPTS", "30"),
                "app.storage.startup-probe.delay-ms=" + property(
                        "APP_STORAGE_STARTUP_PROBE_DELAY_MS", "2000"),
                "spring.data.redis.host=" + requiredProperty(
                        prefix + "_REDIS_HOST", "REDIS_HOST"),
                "spring.data.redis.port=" + property(
                        prefix + "_REDIS_PORT", property("REDIS_PORT", "6379")),
                "spring.data.redis.username=" + redisUsername,
                "spring.data.redis.password=" + redisPassword,
                "REDIS_USERNAME=" + redisUsername,
                "REDIS_PASSWORD=" + redisPassword,
                "spring.data.redis.database=" + property(
                        prefix + "_REDIS_DB", property("REDIS_DB", "0")),
                "spring.data.redis.ssl.enabled=" + property(
                        prefix + "_REDIS_SSL_ENABLED", "false"),
                "security.internal-delegation.private-key="
                        + (admin ? property("INTERNAL_DELEGATION_PRIVATE_KEY", "") : ""),
                "security.internal-delegation.public-key="
                        + (admin ? "" : property("INTERNAL_DELEGATION_PUBLIC_KEY", "")),
                "security.internal-delegation.key-id="
                        + property("INTERNAL_DELEGATION_KEY_ID", ""),
                "security.internal-delegation.bootstrap-private-key="
                        + (admin ? property("BOOTSTRAP_DELEGATION_PRIVATE_KEY", "") : ""),
                "security.internal-delegation.bootstrap-public-key="
                        + (admin ? "" : property("BOOTSTRAP_DELEGATION_PUBLIC_KEY", "")),
                "security.internal-delegation.bootstrap-key-id="
                        + property("BOOTSTRAP_DELEGATION_KEY_ID", ""),
                "security.internal-delegation.issuer="
                        + property("INTERNAL_DELEGATION_ISSUER", "backend-admin"),
                "security.internal-delegation.audience=backend-" + module.name(),
                "security.internal-delegation.ttl-seconds="
                        + property("INTERNAL_DELEGATION_TTL_SECONDS", "30"),
                CoreLocalContractAssembly.LOCAL_CONTRACTS_ENABLED_PROPERTY + "=" + admin,
                "dubbo.enabled=false",
                "dubbo.registry.address=N/A",
                "dubbo.protocol.port=-1",
                "dubbo.application.register-mode=none"
        ));
        String autoConfigurationExcludes = property("spring.autoconfigure.exclude", "");
        if (!autoConfigurationExcludes.isBlank()) {
            properties.add("spring.autoconfigure.exclude=" + autoConfigurationExcludes);
        }
        if (!search) {
            properties.add("spring.datasource.url=" + requiredProperty(
                    "core.datasource." + module.name() + ".url", prefix + "_DB_URL"));
            properties.add("spring.datasource.username=" + requiredProperty(
                    "core.datasource." + module.name() + ".username",
                    prefix + "_DB_USER"));
            properties.add("spring.datasource.password=" + requiredProperty(
                    "core.datasource." + module.name() + ".password",
                    prefix + "_DB_PASSWORD"));
        }
        return properties;
    }

    private String property(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null ? fallback : value;
    }

    private String requiredProperty(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException(
                "Core Owner Module startup requires property: " + String.join(" | ", keys));
    }
}
