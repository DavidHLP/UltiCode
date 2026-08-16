package com.ulticode.auth.search;

import com.ulticode.common.event.SearchDocumentChangedEventContract;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEARCH-001 slice-b publish seam for the Auth-owned {@code users} row.
 *
 * <p>Writes a complete, index-safe user document into
 * {@code search_document_changed_outbox} inside the same transaction as the
 * {@code users} write. {@link SearchDocumentChangedOutboxDispatcher} XADDs the
 * row to {@code stream:integration} after commit. The document carries only
 * identity/display fields ({@code id}/{@code username}, plus optional
 * {@code name}/{@code avatar} when known); never credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDocumentChangedAuthPublisher {

    private static final String USERS_INDEX = SearchDocumentChangedEventContract.USERS_INDEX;

    private final SearchDocumentChangedOutboxMapper outboxMapper;
    private final Clock clock;

    /**
     * Publish an UPSERT or DELETE for a user document.
     *
     * @param aggregateId the user id (document id)
     * @param username    display username ({@code null} for DELETE)
     * @param name        optional display name (App-owned profile enrichment)
     * @param avatar      optional avatar URL
     * @param upsert      {@code true} for create/update, {@code false} for delete tombstone
     */
    @Transactional
    public void publishUser(String aggregateId, String username, String name, String avatar,
                            boolean upsert) {
        if (aggregateId == null || aggregateId.isBlank()) {
            return;
        }
        Map<String, Object> document = null;
        if (upsert) {
            document = new LinkedHashMap<>();
            document.put("id", aggregateId);
            document.put("username", username);
            if (name != null) {
                document.put("name", name);
            }
            if (avatar != null) {
                document.put("avatar", avatar);
            }
            SearchDocumentChangedEventContract.requireSafeDocument(document);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(SearchDocumentChangedEventContract.INDEX, USERS_INDEX);
        payload.put(SearchDocumentChangedEventContract.OPERATION,
                upsert ? SearchDocumentChangedEventContract.UPSERT
                       : SearchDocumentChangedEventContract.DELETE);
        if (document != null) {
            payload.put(SearchDocumentChangedEventContract.DOCUMENT, document);
        }
        LocalDateTime occurredAt = LocalDateTime.now(clock);
        payload.put(SearchDocumentChangedEventContract.OCCURRED_AT, occurredAt.toString());

        SearchDocumentChangedOutboxRecord record = new SearchDocumentChangedOutboxRecord();
        record.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
        record.setOwner(SearchDocumentChangedEventContract.AUTH_PUBLISHER);
        record.setAggregateId(aggregateId);
        record.setAggregateVersion(clock.instant().toEpochMilli());
        record.setEventType(SearchDocumentChangedEventContract.EVENT_TYPE);
        record.setSchemaVersion(SearchDocumentChangedEventContract.SCHEMA_VERSION);
        record.setPayload(payload);
        record.setCreatedAt(occurredAt);
        outboxMapper.insert(record);
        log.debug("Queued {} user event for {}", payload.get(SearchDocumentChangedEventContract.OPERATION),
                aggregateId);
    }
}
