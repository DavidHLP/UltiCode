package com.ulticode.auth.search;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * Durable outbox row for {@code SearchDocumentChanged} user-document events
 * (SEARCH-001 slice-b).
 *
 * <p>Written by {@link SearchDocumentChangedAuthPublisher} inside the same
 * transaction as the {@code users} write; claimed and XADDed to
 * {@code stream:integration} by {@link SearchDocumentChangedOutboxDispatcher}
 * (at-least-once, bounded retry). {@code payload} is the complete event
 * payload ({@code index}/{@code operation}/{@code document}/{@code occurredAt});
 * {@code document=null} encodes a DELETE tombstone.
 */
@Data
public class SearchDocumentChangedOutboxRecord {

    private String id;
    private String owner;
    private String aggregateId;
    private Long aggregateVersion;
    private String eventType;
    private Integer schemaVersion;
    private Map<String, Object> payload;
    private String state;
    private Integer attempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
    private String claimOwner;
    private LocalDateTime deliveredAt;
    private LocalDateTime nextRetryAt;
}
