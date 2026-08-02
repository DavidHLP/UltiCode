package com.ulticode.modules.event.replay;

import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.event.inbox.ConsumerInboxRecord;
import com.ulticode.modules.event.outbox.IntegrationOutboxMapper;
import com.ulticode.modules.event.outbox.IntegrationOutboxRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        LambdaQueryWrapper<IntegrationOutboxRecord> wrapper = new LambdaQueryWrapper<IntegrationOutboxRecord>()
                .in(IntegrationOutboxRecord::getState, "DELIVERED", "DEAD");
        if (aggregateId != null) {
            wrapper.eq(IntegrationOutboxRecord::getAggregateId, aggregateId);
        }
        List<IntegrationOutboxRecord> records = outboxMapper.selectList(wrapper);

        int count = 0;
        for (IntegrationOutboxRecord record : records) {
            record.setState("PENDING");
            record.setAttempts(0);
            record.setLastError(null);
            record.setNextRetryAt(java.time.LocalDateTime.now());
            outboxMapper.updateById(record);
            count++;
        }
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
        LambdaQueryWrapper<ConsumerInboxRecord> wrapper = new LambdaQueryWrapper<ConsumerInboxRecord>()
                .in(ConsumerInboxRecord::getState, "PROCESSED", "DEAD")
                .eq(ConsumerInboxRecord::getConsumer, consumer);
        if (eventId != null) {
            wrapper.eq(ConsumerInboxRecord::getEventId, eventId);
        }
        List<ConsumerInboxRecord> records = inboxMapper.selectList(wrapper);

        int count = 0;
        for (ConsumerInboxRecord record : records) {
            record.setState("PENDING");
            record.setAttempts(0);
            record.setLastError(null);
            record.setLeaseOwner(null);
            record.setLeaseExpiresAt(null);
            record.setNextRetryAt(java.time.LocalDateTime.now());
            inboxMapper.updateById(record);
            count++;
        }
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
        List<IntegrationOutboxRecord> dead = listDeadOutbox();
        for (IntegrationOutboxRecord record : dead) {
            outboxMapper.deleteById(record.getEventId());
        }
        log.info("DLQ purge: deleted {} DEAD outbox events", dead.size());
        return dead.size();
    }

    /**
     * Re-route DEAD outbox events back to PENDING (retry from DLQ).
     * Resets attempts to 0 so they get full retry budget.
     *
     * @return number of events re-routed
     */
    public int rerouteDeadOutbox() {
        List<IntegrationOutboxRecord> dead = listDeadOutbox();
        int count = 0;
        for (IntegrationOutboxRecord record : dead) {
            record.setState("PENDING");
            record.setAttempts(0);
            record.setLastError(null);
            record.setNextRetryAt(java.time.LocalDateTime.now());
            outboxMapper.updateById(record);
            count++;
        }
        log.info("DLQ re-route: reset {} DEAD outbox events to PENDING", count);
        return count;
    }
}
