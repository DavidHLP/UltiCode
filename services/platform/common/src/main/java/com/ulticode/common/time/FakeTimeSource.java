package com.ulticode.common.time;

import java.util.concurrent.atomic.AtomicLong;
import java.io.Serializable;

/**
 * In-test {@link TimeSource} &mdash; deterministic, controllable clock.
 * Not a {@code @Component}.
 *
 * <p>Two knobs:
 * <ul>
 *   <li>{@link #advance(long)} &mdash; advance both wall and monotonic
 *       by the given number of milliseconds.</li>
 *   <li>{@link #advanceNanos(long)} &mdash; advance the monotonic clock
 *       by the given number of nanoseconds (used by elapsed-time
 *       tests).</li>
 * </ul>
 *
 * <p>Construction patterns:
 * <pre>{@code
 *   // Pinned start (wall=1000, monotonic=0)
 *   TimeSource ts = new FakeTimeSource(1000L, 0L);
 *
 *   // Default start (wall=0, monotonic=0)
 *   TimeSource ts = new FakeTimeSource();
 * }</pre>
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code DefaultMonitoringInspectorTest} &mdash; assert latency
 *       values flow through the seam.</li>
 *   <li>{@code SqlTimingInterceptorTest} &mdash; replace
 *       {@code Thread.sleep(80)} with a deterministic clock advance.</li>
 *   <li>{@code WebSocketContestRankingFlusherTest} &mdash; pin the
 *       throttle / cleanup cutoff timestamps.</li>
 * </ul>
 */
public class FakeTimeSource implements Serializable, TimeSource {

    private final AtomicLong wall;
    private final AtomicLong monotonic;

    /** Pin both clocks to {@code 0}. */
    public FakeTimeSource() {
        this(0L, 0L);
    }

    /** Pin wall and monotonic to the given starting values. */
    public FakeTimeSource(long initialWallMillis, long initialMonotonicNanos) {
        this.wall = new AtomicLong(initialWallMillis);
        this.monotonic = new AtomicLong(initialMonotonicNanos);
    }

    @Override
    public long wallMillis() {
        return wall.get();
    }

    @Override
    public long monotonicNanos() {
        return monotonic.get();
    }

    /**
     * Advance the wall clock by the given number of milliseconds.
     * Returns the new wall-millis value for fluent test assertions.
     */
    public long advance(long millis) {
        return wall.addAndGet(millis);
    }

    /**
     * Advance the monotonic clock by the given number of nanoseconds.
     * Returns the new monotonic-nanos value for fluent test assertions.
     */
    public long advanceNanos(long nanos) {
        return monotonic.addAndGet(nanos);
    }

    /**
     * Pin the wall clock to a specific epoch-millis value. Useful for
     * test setup that needs "the request arrived at exactly 12:00:00".
     */
    public void pinWall(long millis) {
        wall.set(millis);
    }

    /**
     * Pin the monotonic clock to a specific nano value. Useful for
     * "the work started at exactly monotonic = 0".
     */
    public void pinMonotonic(long nanos) {
        monotonic.set(nanos);
    }
}
