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
     * Per-replica consumer name. When blank (the default; no
     * {@code SEARCH_WORKER_CONSUMER_NAME} override), a deterministic value is
     * derived once at startup from the hostname so competing replicas in one
     * group get distinct identities and PEL ownership stays attributable.
     * Set the env var for a stable, operator-controlled identity.
     */
    private String consumerName = "";

    /** Resolved-once effective consumer name (see {@link #effectiveConsumerName()}). */
    private String resolvedConsumerName;

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
     * Effective consumer name used for XREADGROUP/XCLAIM. Blank configured
     * values resolve once to {@code <group>-<hostname>} (sanitized), falling
     * back to a random suffix when the hostname is unavailable, so multiple
     * replicas never share one static identity while an explicit env override
     * keeps full determinism.
     */
    public synchronized String effectiveConsumerName() {
        if (resolvedConsumerName == null) {
            String configured = consumerName == null ? "" : consumerName.trim();
            resolvedConsumerName = configured.isEmpty()
                    ? defaultUniqueConsumerName(group)
                    : configured;
        }
        return resolvedConsumerName;
    }

    private static String defaultUniqueConsumerName(String group) {
        String prefix = group == null || group.isBlank() ? "search-worker" : group.trim();
        String host = hostname();
        if (!host.isEmpty()) {
            return prefix + "-" + host;
        }
        // Hostname unavailable (rare): fall back to a per-process random suffix.
        return prefix + "-" + java.util.UUID.randomUUID();
    }

    private static String hostname() {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            if (host == null) {
                return "";
            }
            // Consumer names must stay Redis-token friendly.
            return host.replaceAll("[^A-Za-z0-9._-]", "-");
        } catch (RuntimeException | java.net.UnknownHostException e) {
            return "";
        }
    }
}
