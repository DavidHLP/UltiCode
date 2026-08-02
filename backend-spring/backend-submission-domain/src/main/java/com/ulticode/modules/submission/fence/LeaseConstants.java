package com.ulticode.modules.submission.fence;

/**
 * Lease / heartbeat tuning constants for the ADR-003 M3b JUDGING lease mechanism.
 *
 * <p>These are JVM-level operational constants, not user-tunable configuration.
 * The lease TTL governs how quickly a crashed worker's JUDGING row is recovered
 * by {@link com.ulticode.modules.submission.reaper.JudgingLeaseReaper}; the
 * heartbeat interval is TTL/3 so a worker gets ~2 renew attempts before the
 * reaper would notice a lapse.
 *
 * <p>Tuning trade-off (ADR-003 §3.3): shorter TTL -> faster recovery but more
 * heartbeat DB writes; longer TTL -> fewer writes but slower recovery. 60s/20s
 * is the documented default; surface {@code judge.lease.miss_renew} and
 * {@code judge.lease.expired} to decide whether to change them.
 */
public final class LeaseConstants {

    private LeaseConstants() {
        // Constants only; no instances.
    }

    /**
     * Lease TTL: how long a JUDGING lease stays valid without a heartbeat.
     * Set on {@code acquireLease} and refreshed by {@code renewLease} as
     * {@code NOW() + INTERVAL 60 SECOND}.
     */
    public static final long LEASE_TTL_SECONDS = 60L;

    /**
     * Heartbeat interval: how often a worker renews its lease while judging.
     * TTL / 3 = ~20s, giving a 2-attempt margin before the reaper (5s sweep)
     * could observe an expiry.
     */
    public static final long HEARTBEAT_INTERVAL_SECONDS = 20L;

    /**
     * Heartbeat interval expressed as milliseconds for the
     * {@code ScheduledExecutorService} schedule.
     */
    public static final long HEARTBEAT_INTERVAL_MS = HEARTBEAT_INTERVAL_SECONDS * 1000L;

    /**
     * Reaper batch size: max expired JUDGING rows recovered per sweep. Bounds
     * the per-sweep transaction size and the number of re-enqueues per tick.
     */
    public static final int REAPER_BATCH_SIZE = 20;
}
