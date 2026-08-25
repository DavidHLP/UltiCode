package com.ulticode.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Stream-consumer configuration for the search worker.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "search.worker")
public class SearchWorkerProperties {

    /** Whether the worker is enabled. Off by default so unit-test contexts boot without Redis/MeiliSearch. */
    private boolean enabled;

    /** Redis Stream the App/Auth outbox dispatchers publish to. */
    private String streamKey = "stream:integration";

    /** Consumer group name; each replica uses the same group (competing consumers). */
    private String group = "search-worker";

    /**
     * Per-replica consumer name. Blank by default: {@link #resolvedConsumerName()}
     * then derives an instance-unique name ({@code <group>-<hostname>}), so
     * horizontally scaled replicas never share one PEL identity. Set it only
     * for a deliberately fixed single-instance deployment.
     */
    private String consumerName = "";

    /** Max records per poll. */
    private int batchSize = 50;

    /** Max delivery attempts before an entry is dead-lettered. */
    private int maxAttempts = 5;

    /** DLQ stream key for entries that exhausted retries. */
    private String dlqKey = "search:stream:dlq";

    /** Redis hash key prefix of the per-index document-version ledger (key: {@code prefix}:{index}). */
    private String versionKeyPrefix = "search:doc-version";

    /** Poll interval in milliseconds. */
    private long intervalMs = 2000;

    /**
     * Review 2026-08-25 FINAL P1 (worker scale-out contract): the effective
     * consumer identity. An explicit {@code consumer-name} always wins;
     * otherwise the name is derived per instance from the hostname (unique
     * per container replica, stable across restarts of the same container).
     */
    public String resolvedConsumerName() {
        if (consumerName != null && !consumerName.isBlank()) {
            return consumerName;
        }
        return group + "-" + instanceSuffix();
    }

    private static String instanceSuffix() {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (java.net.UnknownHostException ignored) {
            // fall through to a random suffix
        }
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
