package com.ulticode.app.api.event;

import java.util.Set;

/**
 * Shared field names for the durable integration-event envelope.
 *
 * <p>Owner-specific contracts add event values and payload fields, but they
 * must not invent a second routing envelope.</p>
 */
public final class IntegrationEventEnvelopeContract {

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
}
