package com.ulticode.common.time;

/**
 * Read-only seam for the two time sources the backend still reaches for
 * directly: wall-clock millis (for trace ids, ranking-flusher cutoffs,
 * health-probe latency) and monotonic nanos (for measuring elapsed
 * work in {@code SqlTimingInterceptor} and the sandbox executor).
 *
 * <p>Why a port, not direct {@code System.currentTimeMillis()} /
 * {@code System.nanoTime()}:
 * <ul>
 *   <li><b>Determinism</b> — tests asserting on "query took &gt; 50ms"
 *       previously had to {@code Thread.sleep(80)} in
 *       {@code SqlTimingInterceptorTest}, which is slow and flaky. With
 *       a {@link FakeTimeSource} the test advances the fake clock by
 *       exactly the elapsed nanoseconds the assertion needs.</li>
 *   <li><b>Separation of concerns</b> — the {@code Clock} bean
 *       ({@code common/config/ClockConfig}) already covers
 *       {@code LocalDateTime.now()} for business-time decisions. Wall
 *       millis and monotonic nanos are the two remaining JVM-time
 *       primitives; both are test-hostile when read from the static
 *       factory.</li>
 * </ul>
 *
 * <p><b>Seam justification &mdash; two adapters</b> (per the architecture
 * glossary, "one adapter means a hypothetical seam, two adapters means a
 * real one"):
 * <ul>
 *   <li>{@link SystemTimeSource} &mdash; production, delegates to
 *       {@code System.currentTimeMillis()} and {@code System.nanoTime()}.
 *       Auto-discovered {@code @Component}.</li>
 *   <li>{@link FakeTimeSource} &mdash; test, deterministic values plus a
 *       controllable {@code advance} / {@code advanceNanos} knob. Not a
 *       {@code @Component}; tests construct it directly or inject it
 *       via {@code @MockBean}.</li>
 * </ul>
 *
 * <p>Mirrors the {@code SystemProbe} / {@code UuidGenerator} /
 * {@code RateLimiter} deep-module shape.
 *
 * <p>Callers: monitoring inspector (latency), websocket ranking flusher
 * (throttle + cleanup cutoff), {@code SqlTimingInterceptor} (cost),
 * {@code InMemoryRateLimiter} (window expiry), contest pong response.
 * Static utilities that just stamp a {@code t-<millis>} trace id
 * ({@code TraceIdUtil}) are intentionally left to read
 * {@link #wallMillis()} through this port via
 * {@link TimeSourceHolder}, which {@code TimeConfig} populates at
 * startup &mdash; see that class for the static-side wiring.
 */
public interface TimeSource {

    /**
     * Wall-clock time in milliseconds since the epoch.
     *
     * <p>Equivalent to {@code System.currentTimeMillis()} on the
     * production adapter. Used for trace ids, ranking-flusher
     * cutoffs, and the {@code System.currentTimeMillis()} side of
     * latency measurement where the test may need to pin a specific
     * instant.
     *
     * @return milliseconds since the Unix epoch
     */
    long wallMillis();

    /**
     * Monotonic nanoseconds from a JVM-private high-resolution source.
     *
     * <p>Equivalent to {@code System.nanoTime()} on the production
     * adapter. Used for elapsed-time measurement where the test needs
     * to control how much time "passes" between two probes
     * ({@code SqlTimingInterceptorTest} was the motivating case).
     *
     * @return a monotonic nano-tick, only meaningful as a delta
     */
    long monotonicNanos();
}
