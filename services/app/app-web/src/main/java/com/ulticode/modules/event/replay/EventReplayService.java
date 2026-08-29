package com.ulticode.modules.event.replay;

import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.ConsumerInboxRecord;
import com.ulticode.modules.event.outbox.IntegrationOutboxMapper;
import com.ulticode.modules.event.outbox.IntegrationOutboxRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Replay and DLQ management tooling (P6-REPLAY-001).
 *
 * <p>Provides operations to:
 * <ul>
 *   <li>{@link #replayOutbox} — reset DELIVERED/DEAD outbox rows to PENDING for re-dispatch.</li>
 *   <li>{@link #replayInbox} — reset PROCESSED/DEAD inbox rows to PENDING for a consumer.</li>
 *   <li>{@link #listDeadOutbox} — list DEAD outbox events (poison events).</li>
 *   <li>{@link #listDeadInbox} — list DEAD inbox events for a consumer.</li>
 *   <li>{@link #clearDeadOutbox} — permanently delete DEAD outbox events (purge DLQ).</li>
 *   <li>{@link #rerouteDeadOutbox} — reset DEAD outbox events to PENDING (re-route from DLQ).</li>
 * </ul>
 *
 * <p>These methods are invoked by the admin endpoint or CLI, not the hot path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReplayService {

    private final IntegrationOutboxMapper outboxMapper;
    private final ConsumerInboxMapper inboxMapper;

    // ── Outbox replay ──

    /**
     * Replay outbox events by resetting them to PENDING for re-dispatch.
     *
     * @param aggregateId if non-null, only replay events for this aggregate; null = all
     * @return number of events reset to PENDING
     */
    public int replayOutbox(String aggregateId) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<IntegrationOutboxRecord> update = new UpdateWrapper<IntegrationOutboxRecord>()
                .in("state", "DELIVERED", "DEAD")
                .set("state", "PENDING")
                .set("attempts", 0)
                .set("last_error", null)
                .set("stream_id", null)
                .set("claimed_at", null)
                .set("claim_owner", null)
                .set("delivered_at", null)
                .set("next_retry_at", now);
        if (aggregateId != null) {
            update.eq("aggregate_id", aggregateId);
        }
        int count = outboxMapper.update(null, update);
        log.info("Replay: reset {} outbox events to PENDING (aggregateId={})", count, aggregateId);
        return count;
    }

    // ── Inbox replay ──

    /**
     * Replay inbox events for a specific consumer by resetting them to PENDING.
     *
     * @param consumer consumer name
     * @param eventId  if non-null, only replay this specific event; null = all
     * @return number of events reset to PENDING
     */
    public int replayInbox(String consumer, String eventId) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<ConsumerInboxRecord> update = new UpdateWrapper<ConsumerInboxRecord>()
                .in("state", "PROCESSED", "DEAD")
                .eq("consumer", consumer)
                .set("state", "PENDING")
                .set("attempts", 0)
                .set("last_error", null)
                .set("lease_owner", null)
                .set("lease_expires_at", null)
                .set("processed_at", null)
                .set("next_retry_at", now);
        if (eventId != null) {
            update.eq("event_id", eventId);
        }
        int count = inboxMapper.update(null, update);
        log.info("Replay: reset {} inbox events to PENDING (consumer={}, eventId={})",
                count, consumer, eventId);
        return count;
    }

    // ── DLQ management ──

    /**
     * List all DEAD outbox events (poison events that exhausted retries).
     */
    public List<IntegrationOutboxRecord> listDeadOutbox() {
        return outboxMapper.selectList(
                new LambdaQueryWrapper<IntegrationOutboxRecord>()
                        .eq(IntegrationOutboxRecord::getState, "DEAD")
                        .orderByAsc(IntegrationOutboxRecord::getCreatedAt));
    }

    /**
     * List all DEAD inbox events for a consumer.
     */
    public List<ConsumerInboxRecord> listDeadInbox(String consumer) {
        return inboxMapper.selectList(
                new LambdaQueryWrapper<ConsumerInboxRecord>()
                        .eq(ConsumerInboxRecord::getState, "DEAD")
                        .eq(ConsumerInboxRecord::getConsumer, consumer)
                        .orderByAsc(ConsumerInboxRecord::getCreatedAt));
    }

    /**
     * Permanently delete DEAD outbox events (purge the DLQ).
     *
     * @return number of events deleted
     */
    public int clearDeadOutbox() {
        int count = outboxMapper.delete(
                new LambdaQueryWrapper<IntegrationOutboxRecord>()
                        .eq(IntegrationOutboxRecord::getState, "DEAD"));
        log.info("DLQ purge: deleted {} DEAD outbox events", count);
        return count;
    }

    /**
     * Re-route DEAD outbox events back to PENDING (retry from DLQ).
     * Resets attempts to 0 so they get full retry budget.
     *
     * @return number of events re-routed
     */
    public int rerouteDeadOutbox() {
        int count = outboxMapper.update(
                null,
                new UpdateWrapper<IntegrationOutboxRecord>()
                        .eq("state", "DEAD")
                        .set("state", "PENDING")
                        .set("attempts", 0)
                        .set("last_error", null)
                        .set("stream_id", null)
                        .set("claimed_at", null)
                        .set("claim_owner", null)
                        .set("delivered_at", null)
                        .set("next_retry_at", LocalDateTime.now()));
        log.info("DLQ re-route: reset {} DEAD outbox events to PENDING", count);
        return count;
    }
}
