package com.ulticode.modules.notification.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.notification.ledger.DeliveryState;
import com.ulticode.modules.notification.ledger.entity.NotificationDeliveryLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper for {@link NotificationDeliveryLedger} (ADR-004 M4a).
 *
 * <p>The {@code tryClaim} query uses {@code INSERT ... ON DUPLICATE KEY UPDATE}
 * to atomically claim a new row or a bounded-retry {@code FAILED} row. MySQL
 * returns a positive affected-row count for either claim and zero when an
 * existing {@code CLAIMED}, {@code DELIVERED}, or {@code SKIPPED} row remains
 * owned by another attempt or is terminal.
 *
 * <p>Marking DELIVERED / FAILED updates only the state and (optionally)
 * failure_reason; {@code updated_at} is bumped automatically by the column's
 * {@code ON UPDATE CURRENT_TIMESTAMP(3)} definition.
 */
@Mapper
public interface NotificationDeliveryLedgerMapper extends BaseMapper<NotificationDeliveryLedger> {

    /**
     * Atomically claim a delivery slot for {@code (intentId, channelId)}.
     * <p>Returns:
     * <ul>
     *   <li>a positive count — new row or retryable FAILED row claimed by this
     *       caller; it should call {@link #markDelivered} or {@link #markFailed}.</li>
     *   <li>{@code 0} — an existing CLAIMED/DELIVERED/SKIPPED row remains
     *       idempotently protected, or the retry budget is exhausted.</li>
     * </ul>
     *
     * @param intentId   {@link com.ulticode.modules.notification.intent.NotificationIntent#intentId()}
     * @param channelId  {@code "in_app"} / {@code "email"} / {@code "websocket"}
     * @param userId     recipient (denormalized for ops queries)
     * @param intentType record class simpleName (e.g. {@code "SubmissionCompletedIntent"})
     * @return positive affected rows when this caller owns the slot, otherwise 0
     */
    @org.apache.ibatis.annotations.Insert("INSERT INTO notification_delivery_ledger "
            + "(intent_id, channel_id, user_id, intent_type, delivery_state, delivered_at) "
            + "VALUES (#{intentId}, #{channelId}, #{userId}, #{intentType}, 'CLAIMED', CURRENT_TIMESTAMP(3)) "
            + "ON DUPLICATE KEY UPDATE "
            + "delivered_at = IF(delivery_state = 'FAILED' AND reclaim_attempts < 5, "
            + "CURRENT_TIMESTAMP(3), delivered_at), "
            + "delivery_state = IF(delivery_state = 'FAILED' AND reclaim_attempts < 5, "
            + "'CLAIMED', delivery_state)")
    int tryClaim(@Param("intentId") String intentId,
                 @Param("channelId") String channelId,
                 @Param("userId") String userId,
                 @Param("intentType") String intentType);

    /**
     * Transition a CLAIMED row to {@code DELIVERED}. Idempotent: returns 0 if
     * the row is missing or already in a terminal state.
     *
     * @param intentId  intent id
     * @param channelId channel id
     * @return affected rows (1 on success)
     */
    @Update("UPDATE notification_delivery_ledger "
            + "SET delivery_state = 'DELIVERED', failure_reason = NULL "
            + "WHERE intent_id = #{intentId} AND channel_id = #{channelId} "
            + "  AND delivery_state = 'CLAIMED'")
    int markDelivered(@Param("intentId") String intentId,
                      @Param("channelId") String channelId);

    /**
     * Transition a CLAIMED row to {@code FAILED} with a (truncated) reason.
     * Idempotent: returns 0 if the row is missing or already in a terminal
     * state.
     *
     * @param intentId      intent id
     * @param channelId     channel id
     * @param failureReason truncated error message (caller pre-truncates to 500 chars)
     * @return affected rows (1 on success)
     */
    @Update("UPDATE notification_delivery_ledger "
            + "SET delivery_state = 'FAILED', failure_reason = #{failureReason}, "
            + "    reclaim_attempts = LEAST(reclaim_attempts + 1, 5) "
            + "WHERE intent_id = #{intentId} AND channel_id = #{channelId} "
            + "  AND delivery_state = 'CLAIMED'")
    int markFailed(@Param("intentId") String intentId,
                   @Param("channelId") String channelId,
                   @Param("failureReason") String failureReason);

    /**
     * Transition a CLAIMED row to {@code SKIPPED} when the channel's
     * {@code supports(intent)} returned false. This is a "we chose not to
     * deliver" signal — distinct from FAILED ("we tried and failed") and
     * DELIVERED ("we tried and succeeded"). Idempotent.
     *
     * @param intentId  intent id
     * @param channelId channel id
     * @return affected rows (1 on success)
     */
    @Update("UPDATE notification_delivery_ledger "
            + "SET delivery_state = 'SKIPPED' "
            + "WHERE intent_id = #{intentId} AND channel_id = #{channelId} "
            + "  AND delivery_state = 'CLAIMED'")
    int markSkipped(@Param("intentId") String intentId,
                    @Param("channelId") String channelId);

    /**
     * Lookup a single row by its natural key. Used by tests and ops queries;
     * the dispatcher does not call this on the success path.
     */
    @Select("SELECT * FROM notification_delivery_ledger "
            + "WHERE intent_id = #{intentId} AND channel_id = #{channelId}")
    NotificationDeliveryLedger findByIntentAndChannel(@Param("intentId") String intentId,
                                                      @Param("channelId") String channelId);

    /**
     * Count rows in a given delivery state. Used by ops dashboards and the
     * ADR-004 §4 contract test.
     */
    @Select("SELECT COUNT(*) FROM notification_delivery_ledger WHERE delivery_state = #{state}")
    long countByState(@Param("state") DeliveryState state);

    /**
     * Reap stuck {@code CLAIMED} rows — i.e. dispatcher reserved a slot
     * but the JVM was killed (pm2 reload, OOM, pod eviction) before the
     * channel could transition the row to {@code DELIVERED} / {@code FAILED}.
     * Without this reaper, an existing CLAIMED row would make a retry return
     * 0 and permanently block that channel until the process was restarted.
     *
     * <p>Default grace is 10 minutes: long enough for slow SMTP responses
     * (the Email channel is the slowest), short enough that a stuck row
     * is usually fixed within one reaper cycle.
     *
     * <p>Returns the number of rows reaped, surfaced via the
     * {@code notification.ledger.reaper.reaped} counter.
     */
    @Update("UPDATE notification_delivery_ledger "
            + "SET delivery_state = 'FAILED', "
            + "    failure_reason = CONCAT('CLAIMED > 10min; reaped at ', NOW()), "
            + "    reclaim_attempts = LEAST(reclaim_attempts + 1, 5) "
            + "WHERE delivery_state = 'CLAIMED' "
            + "  AND delivered_at < (NOW() - INTERVAL 10 MINUTE)")
    int reapStaleClaimed();

    /**
     * Mark FAILED legacy rows eligible for the next synchronous retry
     * (P6-INBOX-001).
     *
     * <p>This method must not transition rows to {@code CLAIMED}: only the
     * caller that executes {@link #tryClaim} owns that lease. Durable
     * submission rows are excluded by both the current wire type
     * ({@code SUBMISSION}) and the historical class simple name
     * ({@code SubmissionCompletedIntent}); their owning inbox consumer
     * performs the retry through {@link #tryClaim}.
     */
    @Update("""
        UPDATE notification_delivery_ledger
        SET failure_reason = NULL
        WHERE delivery_state = 'FAILED'
          AND intent_type NOT IN ('SUBMISSION', 'SubmissionCompletedIntent')
          AND reclaim_attempts < 5
          AND updated_at < (NOW() - INTERVAL 5 MINUTE)
        """)
    int reclaimFailedLegacy();
}
