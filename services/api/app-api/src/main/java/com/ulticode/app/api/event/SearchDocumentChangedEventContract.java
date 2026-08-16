package com.ulticode.app.api.event;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Wire contract for durable search index changes.
 *
 * <p>Source owners publish a complete, safe document snapshot. The Search
 * worker never reads an App/Auth business table to fill in missing fields.
 *
 * <p><strong>Wiring status:</strong> the contract is frozen; no
 * App/Auth publisher and no {@code backend-search} consumer exist yet.
 * Consumers must not assume this event is live until the search worker
 * module and the owner outbox publishers land.
 */
public final class SearchDocumentChangedEventContract {

    public static final int SCHEMA_VERSION = 1;
    public static final String EVENT_TYPE = "SearchDocumentChanged";

    public static final String APP_PUBLISHER = "App";
    public static final String AUTH_PUBLISHER = "Auth";

    public static final Set<String> ENVELOPE_FIELDS = IntegrationEventEnvelopeContract.FIELDS;
    public static final String EVENT_ID = IntegrationEventEnvelopeContract.EVENT_ID;
    public static final String OWNER_FIELD = IntegrationEventEnvelopeContract.OWNER;
    public static final String EVENT_TYPE_FIELD = IntegrationEventEnvelopeContract.EVENT_TYPE;
    public static final String SCHEMA_VERSION_FIELD = IntegrationEventEnvelopeContract.SCHEMA_VERSION;
    public static final String AGGREGATE_ID = IntegrationEventEnvelopeContract.AGGREGATE_ID;
    public static final String AGGREGATE_VERSION = IntegrationEventEnvelopeContract.AGGREGATE_VERSION;
    public static final String CAUSATION_ID = IntegrationEventEnvelopeContract.CAUSATION_ID;
    public static final String TRACE_ID = IntegrationEventEnvelopeContract.TRACE_ID;
    public static final String PAYLOAD = IntegrationEventEnvelopeContract.PAYLOAD;
    public static final String INDEX = "index";
    public static final String OPERATION = "operation";
    public static final String DOCUMENT = "document";
    public static final String OCCURRED_AT = "occurredAt";

    public static final String PROBLEMS_INDEX = "problems";
    public static final String USERS_INDEX = "users";
    public static final String POSTS_INDEX = "posts";
    public static final String SOLUTIONS_INDEX = "solutions";

    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";

    public static final Set<String> SUPPORTED_PUBLISHERS = Set.of(APP_PUBLISHER, AUTH_PUBLISHER);
    public static final Set<String> SUPPORTED_INDEXES = Set.of(
            PROBLEMS_INDEX, USERS_INDEX, POSTS_INDEX, SOLUTIONS_INDEX);
    public static final Set<String> SUPPORTED_OPERATIONS = Set.of(UPSERT, DELETE);
    public static final Set<String> FORBIDDEN_DOCUMENT_FIELDS = Set.of(
            "code", "sourceCode", "testCases", "hiddenTestCases",
            "accessToken", "refreshToken", "cookie", "password", "token");

    private SearchDocumentChangedEventContract() {
    }

    /** Validate a key collection (legacy flat-key entry point). */
    public static void requireSafeDocumentKeys(Collection<?> keys) {
        if (keys == null) {
            return;
        }
        for (Object key : keys) {
            if (key instanceof String k) {
                rejectForbiddenKey(k);
            }
        }
    }

    /**
     * Validate a document tree: the root must be a JSON object (map) and
     * every nested object key must be a String, recursively. Rejects scalar
     * or list roots and non-String map keys, which Jackson would silently
     * stringify into unexpected field names.
     */
    public static void requireSafeDocument(Object document) {
        if (!(document instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("search document must be a JSON object");
        }
        validateDocumentNode(map);
    }

    private static void validateDocumentNode(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(
                            "search document object keys must be strings: " + entry.getKey());
                }
                rejectForbiddenKey(key);
                validateDocumentNode(entry.getValue());
            }
        } else if (node instanceof Iterable<?> values) {
            for (Object value : values) {
                validateDocumentNode(value);
            }
        }
    }

    private static void rejectForbiddenKey(String key) {
        if (FORBIDDEN_DOCUMENT_FIELDS.contains(key)) {
            throw new IllegalArgumentException("forbidden search document field: " + key);
        }
    }
}
