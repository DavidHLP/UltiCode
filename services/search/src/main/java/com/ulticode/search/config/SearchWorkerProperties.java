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

    /** Stable per-replica consumer name. */
    private String consumerName = "search-worker-1";

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
}
