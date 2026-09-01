package com.ulticode.common.event;

import java.util.Map;
import java.util.Set;

/**
 * Shared field names for the durable integration-event envelope.
 *
 * <p>Owner-specific contracts add event values and payload fields, but they
 * must not invent a second routing envelope.</p>
 *
 * <p>Lives in backend-common (not app-api) so the leaf provider
 * backend-auth can reference it without forming a forbidden App-contract
 * dependency (AuthSingleHopArchTest, §6.5).</p>
 */
public final class IntegrationEventEnvelopeContract {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    public static final String INTEGRATION_STREAM_KEY = "stream:integration";
    public static final String APP_AUDIT_STREAM_KEY = "stream:app-audit";
    public static final String AUTH_AUDIT_STREAM_KEY = "stream:auth-audit";

    public static final String EVENT_ID = "eventId";
    public static final String OWNER = "owner";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String AGGREGATE_ID = "aggregateId";
    public static final String AGGREGATE_VERSION = "aggregateVersion";
    public static final String CAUSATION_ID = "causationId";
    public static final String TRACE_ID = "traceId";
    public static final String PAYLOAD = "payload";

    public static final Set<String> FIELDS = Set.of(
            EVENT_ID, OWNER, EVENT_TYPE, SCHEMA_VERSION,
            AGGREGATE_ID, AGGREGATE_VERSION, CAUSATION_ID, TRACE_ID, PAYLOAD);

    private IntegrationEventEnvelopeContract() {
    }

    /**
     * Validate the transport envelope before a consumer acknowledges it.
     *
     * <p>Schema versions are additive only: consumers accept the supported
     * range and leave malformed or future events in the broker PEL.</p>
     *
     * @param fields Redis Stream fields
     * @throws IllegalArgumentException when the envelope is incompatible
     */
    public static void requireCompatibleEnvelope(Map<String, String> fields) {
        if (fields == null) {
            throw new IllegalArgumentException("Integration event envelope is missing");
        }
        for (String field : FIELDS) {
            if (CAUSATION_ID.equals(field) || TRACE_ID.equals(field)) {
                continue;
            }
            String value = fields.get(field);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing integration event field: " + field);
            }
        }
        int schemaVersion;
        try {
            schemaVersion = Integer.parseInt(fields.get(SCHEMA_VERSION));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integration event schema version", exception);
        }
        if (schemaVersion < MIN_SUPPORTED_SCHEMA_VERSION
                || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported integration event schema version: " + schemaVersion);
        }
        try {
            if (Long.parseLong(fields.get(AGGREGATE_VERSION)) < 0L) {
                throw new IllegalArgumentException("Integration event aggregate version is negative");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integration event aggregate version", exception);
        }
    }

}
