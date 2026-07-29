package com.ulticode.modules.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Write helper for publishing cross-service events via the integration outbox (P6-OUTBOX-001).
 *
 * <p>Call within a business {@code @Transactional} method to atomically record the event
 * in the same DB transaction as the business operation. The {@link IntegrationOutboxDispatcher}
 * picks it up after commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationEventPublisher {

    private final IntegrationOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * Record an event in the integration outbox within the current transaction.
     *
     * @param owner       publishing Owner (Auth/Admin/App)
     * @param eventType   domain event type (e.g., "UserRegistered", "SubmissionJudged")
     * @param aggregateId root aggregate identifier
     * @param payload     event payload as a Map (serialized to JSON in the DB)
     * @return the generated event ID
     */
    @Transactional
    public String publish(String owner, String eventType, String aggregateId,
                          Map<String, Object> payload) {
        return publish(owner, eventType, aggregateId, 0L, null, null, payload);
    }

    /**
     * Full-arity publish with causation and trace metadata.
     *
     * @param owner            publishing Owner
     * @param eventType        domain event type
     * @param aggregateId      root aggregate identifier
     * @param aggregateVersion aggregate version for ordering
     * @param causationId      causation event ID (saga chaining), or null
     * @param traceId          OpenTelemetry trace ID, or null
     * @param payload          event payload as a Map
     * @return the generated event ID
     */
    @Transactional
    public String publish(String owner, String eventType, String aggregateId,
                          long aggregateVersion, String causationId, String traceId,
                          Map<String, Object> payload) {
        IntegrationOutboxRecord record = new IntegrationOutboxRecord();
        record.setOwner(owner);
        record.setEventType(eventType);
        record.setAggregateId(aggregateId);
        record.setAggregateVersion(aggregateVersion);
        record.setCausationId(causationId);
        record.setTraceId(traceId);
        record.setSchemaVersion(1);
        record.setPayload(payload);
        record.setState("PENDING");
        record.setAttempts(0);

        outboxMapper.insert(record);

        log.debug("Integration event recorded: type={}, aggregate={}, eventId={}",
                eventType, aggregateId, record.getEventId());
        return record.getEventId();
    }
}
