package com.ulticode.modules.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Health snapshot of one queue, returned by the queue inspector's
 * read for monitoring.
 *
 * <p>Carries:
 * <ul>
 *   <li>{@link #waitingDepth} &mdash; the operationally meaningful
 *       depth of the queue. For the Redisson {@code RQueue} backend
 *       this is {@code RQueue.size()}; for the Redis Streams
 *       {@code judge:stream} backend (active when
 *       {@code app.features.judge-queue.use-port=true}) this is the
 *       consumer-group pending total (XPENDING), so monitoring sees
 *       one shape regardless of backend.</li>
 *   <li>{@link #probeStatus} &mdash; whether the probe reached the
 *       broker ({@link ProbeStatus#OK}) or failed
 *       ({@link ProbeStatus#PROBE_FAILED}). A failed probe MUST be
 *       surfaced as unhealthy; the depth fields are informational
 *       only when the probe failed.</li>
 *   <li>{@link #failedCount} / {@link #completedCount} &mdash;
 *       historical aggregates. Currently always zero: deriving them
 *       requires a bounded SCAN over the {@code queue:job:*}
 *       namespace filtered by job status, which is deferred to keep
 *       the probe cheap and non-invasive on the Redis storm rules.
 *       Follow-up: introduce a bounded {@code SCAN} cursor over
 *       {@code queue:job:*} with a hard upper bound on keys touched
 *       per probe and short-circuit on cursor exhaustion.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueHealthSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Queue name (matches a {@code QueueConstants} queue-name constant).
     */
    private String queueName;

    /**
     * Number of jobs the broker reports as not-yet-committed. Semantics
     * depend on backend: {@code RQueue.size()} for the legacy path,
     * XPENDING total for the Streams path.
     */
    @Builder.Default
    private long waitingDepth = 0L;

    /**
     * Number of jobs known to have failed. Currently always zero;
     * see class javadoc for the deferred SCAN-based derivation.
     */
    @Builder.Default
    private long failedCount = 0L;

    /**
     * Number of jobs known to have completed. Currently always zero;
     * see class javadoc for the deferred SCAN-based derivation.
     */
    @Builder.Default
    private long completedCount = 0L;

    /**
     * Outcome of the probe that produced this snapshot. Never
     * {@code null} once the inspector returns.
     */
    @Builder.Default
    private ProbeStatus probeStatus = ProbeStatus.OK;
}
