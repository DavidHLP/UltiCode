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
 * <p>{@code tryClaim} first inserts a delivery row and then, when the natural
 * key already exists, atomically reclaims an eligible {@code FAILED} row.
 * The two-step claim avoids treating a duplicate-key update as a successful
 * lease on concurrent dispatchers. Every lease records a unique
 * {@code claim_owner}; terminal updates must present that owner so a stale
 * dispatcher cannot overwrite a newer attempt.
 *
 * <p>Marking DELIVERED / FAILED updates the state and clears the lease.
 * {@code updated_at} is bumped automatically by the column's
 * {@code ON UPDATE CURRENT_TIMESTAMP(3)} definition.
 */
@Mapper
public interface NotificationDeliveryLedgerMapper extends BaseMapper<NotificationDeliveryLedger> {

    /**
     * Atomically claim a delivery slot for {@code (intentId, channelId)}.
     *
     * <p>A fresh row is claimed by {@link #insertClaim}. If the natural key
     * already exists, {@link #reclaimClaim} uses a state/backoff predicate
     * under the database row lock. This makes concurrent reclaimers produce
     * one positive result at most.
     *
     * @return positive affected rows when this caller owns the slot, otherwise 0
     */
    default int tryClaim(String intentId,
                          String channelId,
                          String userId,
                          String intentType,
                          String claimOwner) {
        if (insertClaim(intentId, channelId, userId, intentType, claimOwner) > 0) {
            return 1;
        }
        return reclaimClaim(intentId, channelId, claimOwner);
    }

    /**
     * Insert a fresh CLAIMED row. With {@code useAffectedRows=true}, a
     * duplicate natural key whose no-op update changes nothing returns zero.
     */
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO notification_delivery_ledger
          (intent_id, channel_id, user_id, intent_type, delivery_state,
           claim_owner, claimed_at, delivered_at)
        VALUES
          (#{intentId}, #{channelId}, #{userId}, #{intentType}, 'CLAIMED',
           #{claimOwner}, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertClaim(@Param("intentId") String intentId,
                    @Param("channelId") String channelId,
                    @Param("userId") String userId,
                    @Param("intentType") String intentType,
                    @Param("claimOwner") String claimOwner);

    /**
     * Reclaim an eligible FAILED row, fenced by state and retry backoff.
     * The row lock makes only one concurrent dispatcher win.
     */
    @Update("""
        UPDATE notification_delivery_ledger
        SET delivery_state = 'CLAIMED',
            failure_reason = NULL,
            claimed_at = CURRENT_TIMESTAMP(3),
            claim_owner = #{claimOwner},
            delivered_at = CURRENT_TIMESTAMP(3)
        WHERE intent_id = #{intentId} AND channel_id = #{channelId}
          AND delivery_state = 'FAILED'
          AND reclaim_attempts < 5
          AND updated_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE)
        """)
    int reclaimClaim(@Param("intentId") String intentId,
                     @Param("channelId") String channelId,
                     @Param("claimOwner") String claimOwner);

    /**
     * Transition a CLAIMED row to {@code DELIVERED}, fenced to the current
     * lease owner. Returns 0 when the row was reclaimed or is terminal.
     */
    @Update("""
        UPDATE notification_delivery_ledger
        SET delivery_state = 'DELIVERED',
            failure_reason = NULL,
            claimed_at = NULL,
            claim_owner = NULL,
            delivered_at = CURRENT_TIMESTAMP(3)
        WHERE intent_id = #{intentId} AND channel_id = #{channelId}
          AND delivery_state = 'CLAIMED'
          AND claim_owner = #{claimOwner}
        """)
    int markDelivered(@Param("intentId") String intentId,
                      @Param("channelId") String channelId,
                      @Param("claimOwner") String claimOwner);

    /**
     * Transition a CLAIMED row to {@code FAILED} with a (truncated) reason,
     * fenced to the current lease owner. The bounded attempt counter makes
     * {@code reclaim_attempts >= 5} terminal.
     */
    @Update("""
        UPDATE notification_delivery_ledger
        SET delivery_state = 'FAILED',
            failure_reason = #{failureReason},
            reclaim_attempts = LEAST(reclaim_attempts + 1, 5),
            claimed_at = NULL,
            claim_owner = NULL
        WHERE intent_id = #{intentId} AND channel_id = #{channelId}
          AND delivery_state = 'CLAIMED'
          AND claim_owner = #{claimOwner}
        """)
    int markFailed(@Param("intentId") String intentId,
                   @Param("channelId") String channelId,
                   @Param("failureReason") String failureReason,
                   @Param("claimOwner") String claimOwner);

    /**
     * Transition a CLAIMED row to {@code SKIPPED} when the channel's
     * {@code supports(intent)} returned false, fenced to the current owner.
     */
    @Update("""
        UPDATE notification_delivery_ledger
        SET delivery_state = 'SKIPPED',
            claimed_at = NULL,
            claim_owner = NULL
        WHERE intent_id = #{intentId} AND channel_id = #{channelId}
          AND delivery_state = 'CLAIMED'
          AND claim_owner = #{claimOwner}
        """)
    int markSkipped(@Param("intentId") String intentId,
                    @Param("channelId") String channelId,
                    @Param("claimOwner") String claimOwner);

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
     * Oldest in-flight CLAIMED lease age in seconds, or {@code null} when no
     * CLAIMED row exists. Exposes the delivery worker's ledger lag so a stale
     * dispatcher (not yet reaped) is observable in ops dashboards.
     */
    @Select("""
        SELECT TIMESTAMPDIFF(SECOND, MIN(claimed_at), CURRENT_TIMESTAMP(3))
        FROM notification_delivery_ledger
        WHERE delivery_state = 'CLAIMED'
        """)
    Long oldestClaimedAgeSeconds();

    /**
     * Reap stale {@code CLAIMED} leases. The compare-and-set predicate on the
     * state and lease timestamp fences late channel completions; a stale owner
     * can no longer mark the reclaimed row terminal.
     *
     * <p>Both synchronous and durable notification claims are recovered here.
     * Durable inbox replay retries the resulting {@code FAILED} row after the
     * normal backoff, while the claim owner fence prevents a late dispatcher
     * from overwriting the replacement attempt. The grace period intentionally
     * accepts the documented at-least-once duplicate-send race.
     *
     * <p>Default grace is 10 minutes: long enough for slow SMTP responses,
     * short enough that a crashed dispatcher does not block the channel.
     */
    @Update("""
        UPDATE notification_delivery_ledger
        SET delivery_state = 'FAILED',
            failure_reason = CONCAT('CLAIMED > 10min; reaped at ', NOW()),
            reclaim_attempts = LEAST(reclaim_attempts + 1, 5),
            claimed_at = NULL,
            claim_owner = NULL
        WHERE delivery_state = 'CLAIMED'
          AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 10 MINUTE)
        """)
    int reapStaleClaimed();

}
