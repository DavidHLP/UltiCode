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
 * Every set-based mutation is bounded by {@link #MAX_MUTATION_BATCH} rows per
 * statement so a full-history replay or purge cannot turn into an unbounded
 * redelivery storm in one invocation; operators re-invoke until the returned
 * count drops below the bound.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReplayService {

    /** Upper bound of rows one replay/purge statement may touch. */
    static final int MAX_MUTATION_BATCH = 1000;

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
        UpdateWrapper<IntegrationOutboxRecord> update = outboxResetToPending();
        update.in("state", "DELIVERED", "DEAD");
        if (aggregateId != null) {
            update.eq("aggregate_id", aggregateId);
        }
        int count = outboxMapper.update(null, update);
        logBoundedBatch("Replay: reset outbox events to PENDING", count,
                aggregateId == null ? "all aggregates" : aggregateId);
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
        UpdateWrapper<ConsumerInboxRecord> update = inboxResetToPending();
        update.in("state", "PROCESSED", "DEAD")
                .eq("consumer", consumer);
        if (eventId != null) {
            update.eq("event_id", eventId);
        }
        int count = inboxMapper.update(null, update);
        logBoundedBatch("Replay: reset inbox events to PENDING", count,
                "consumer=" + consumer + ", eventId=" + eventId);
        return count;
    }

    // ── DLQ management ──

    /**
     * List DEAD outbox events (poison events that exhausted retries).
     */
    public List<IntegrationOutboxRecord> listDeadOutbox() {
        return outboxMapper.selectList(
                new LambdaQueryWrapper<IntegrationOutboxRecord>()
                        .eq(IntegrationOutboxRecord::getState, "DEAD")
                        .orderByAsc(IntegrationOutboxRecord::getCreatedAt)
                        .last("LIMIT " + MAX_MUTATION_BATCH));
    }

    /**
     * List DEAD inbox events for a consumer.
     */
    public List<ConsumerInboxRecord> listDeadInbox(String consumer) {
        return inboxMapper.selectList(
                new LambdaQueryWrapper<ConsumerInboxRecord>()
                        .eq(ConsumerInboxRecord::getState, "DEAD")
                        .eq(ConsumerInboxRecord::getConsumer, consumer)
                        .orderByAsc(ConsumerInboxRecord::getCreatedAt)
                        .last("LIMIT " + MAX_MUTATION_BATCH));
    }

    /**
     * Permanently delete DEAD outbox events (purge the DLQ).
     *
     * @return number of events deleted
     */
    public int clearDeadOutbox() {
        int count = outboxMapper.delete(
                new LambdaQueryWrapper<IntegrationOutboxRecord>()
                        .eq(IntegrationOutboxRecord::getState, "DEAD")
                        .last("LIMIT " + MAX_MUTATION_BATCH));
        logBoundedBatch("DLQ purge: deleted DEAD outbox events", count, "DEAD");
        return count;
    }

    /**
     * Re-route DEAD outbox events back to PENDING (retry from DLQ).
     * Resets attempts to 0 so they get full retry budget.
     *
     * @return number of events re-routed
     */
    public int rerouteDeadOutbox() {
        UpdateWrapper<IntegrationOutboxRecord> update = outboxResetToPending();
        update.eq("state", "DEAD");
        int count = outboxMapper.update(null, update);
        logBoundedBatch("DLQ re-route: reset DEAD outbox events to PENDING", count, "DEAD");
        return count;
    }

    /**
     * Shared outbox "reset to PENDING" column set: state, attempts, error,
     * dispatch-claim fields and next retry time. Uses string columns
     * deliberately: {@code UpdateWrapper.set} resolves lambda columns
     * eagerly and requires a registered TableInfo, which unit tests with
     * mocked mappers do not have.
     */
    private static UpdateWrapper<IntegrationOutboxRecord> outboxResetToPending() {
        return new UpdateWrapper<IntegrationOutboxRecord>()
                .set("state", "PENDING")
                .set("attempts", 0)
                .set("last_error", null)
                .set("stream_id", null)
                .set("claimed_at", null)
                .set("claim_owner", null)
                .set("delivered_at", null)
                .set("next_retry_at", LocalDateTime.now())
                .last("LIMIT " + MAX_MUTATION_BATCH);
    }

    /**
     * Shared inbox "reset to PENDING" column set: state, attempts, error,
     * lease fields and next retry time (string columns, see
     * {@link #outboxResetToPending()}).
     */
    private static UpdateWrapper<ConsumerInboxRecord> inboxResetToPending() {
        return new UpdateWrapper<ConsumerInboxRecord>()
                .set("state", "PENDING")
                .set("attempts", 0)
                .set("last_error", null)
                .set("lease_owner", null)
                .set("lease_expires_at", null)
                .set("processed_at", null)
                .set("next_retry_at", LocalDateTime.now())
                .last("LIMIT " + MAX_MUTATION_BATCH);
    }

    private static void logBoundedBatch(String action, int count, String scope) {
        log.info("{}: {} rows (scope={})", action, count, scope);
        if (count >= MAX_MUTATION_BATCH) {
            log.warn("{} reached the {}-row batch bound; more rows may remain, re-invoke to continue (scope={})",
                    action, MAX_MUTATION_BATCH, scope);
        }
    }
}
