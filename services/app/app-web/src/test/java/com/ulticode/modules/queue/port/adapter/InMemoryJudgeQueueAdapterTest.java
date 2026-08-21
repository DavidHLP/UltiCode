package com.ulticode.modules.queue.port.adapter;

import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemoryJudgeQueueAdapter} (ADR-003 M3c-2 / M3c-3a
 * adapter contract; M3c-3b acceptance).
 *
 * <p>Validates the four port contracts the production Redisson Streams
 * adapter must also satisfy (the F12 pre-condition: if a reaper can
 * XCLAIM and re-deliver a lost entry, the InMemory semantics must be
 * equivalent):
 * <ul>
 *   <li>enqueue is idempotent on {@code (submissionId, generation)}.</li>
 *   <li>poll is non-destructive: the entry moves to a pending-acks map.</li>
 *   <li>ack removes from the pending-acks map.</li>
 *   <li>nack leaves the entry in pending-acks (PEL mirror).</li>
 * </ul>
 *
 * <p>Plus a F12-style reclaim path: poll → no ack (worker "died") → a
 * later poll (reaper XCLAIM analogue) sees the entry as still pending.
 *
 * <p>No Testcontainers — the InMemory adapter is a pure JVM implementation
 * so the contract check is microsecond-fast and side-effect free.
 */
@DisplayName("InMemoryJudgeQueueAdapter (M3c-2/M3c-3a port contract)")
class InMemoryJudgeQueueAdapterTest {

    private InMemoryJudgeQueueAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryJudgeQueueAdapter();
    }

    private JudgeJobEnvelope envelope(String submissionId, long generation) {
        return new JudgeJobEnvelope(
                2,
                UUID.randomUUID().toString(),
                submissionId,
                "problem-1",
                "user-1",
                "java",
                "class Main { }",
                2000,
                256 * 1024,
                generation,
                UUID.randomUUID().toString());
    }

    @Nested
    @DisplayName("enqueue idempotency")
    class EnqueueIdempotency {

        @Test
        void repeatEnqueueForSameGenerationIsNoop() {
            JudgeJobEnvelope env = envelope("sub-A", 1L);
            adapter.enqueue(env);
            adapter.enqueue(env);
            adapter.enqueue(env);
            // Only one entry was actually queued.
            Optional<JudgeJobHandle> first = adapter.poll(0L);
            assertThat(first).isPresent();
            Optional<JudgeJobHandle> second = adapter.poll(0L);
            assertThat(second).isEmpty();
        }

        @Test
        void differentGenerationsBothSucceed() {
            adapter.enqueue(envelope("sub-A", 1L));
            adapter.enqueue(envelope("sub-A", 2L));
            // Two entries (different generations -> different dedup keys).
            assertThat(adapter.poll(0L)).isPresent();
            assertThat(adapter.poll(0L)).isPresent();
            assertThat(adapter.poll(0L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("poll + ack")
    class PollAndAck {

        @Test
        void pollMovesEntryToPendingAcks() {
            adapter.enqueue(envelope("sub-A", 1L));
            assertThat(adapter.pendingAckCount()).isZero();
            adapter.poll(0L);
            assertThat(adapter.pendingAckCount()).isEqualTo(1);
        }

        @Test
        void ackRemovesFromPendingAcks() {
            adapter.enqueue(envelope("sub-A", 1L));
            JudgeJobHandle handle = adapter.poll(0L).orElseThrow();
            adapter.ack(handle);
            assertThat(adapter.pendingAckCount()).isZero();
        }

        @Test
        void pollThenPollSameEntryLeavesInPending() {
            adapter.enqueue(envelope("sub-A", 1L));
            adapter.poll(0L);
            // Re-poll returns empty: the entry is in PEL, not in the ready queue.
            assertThat(adapter.poll(0L)).isEmpty();
            assertThat(adapter.pendingAckCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("nack / F12 reclaim path")
    class NackAndF12Reclaim {

        @Test
        void nackLeavesEntryInPendingAcks() {
            adapter.enqueue(envelope("sub-A", 1L));
            JudgeJobHandle handle = adapter.poll(0L).orElseThrow();
            adapter.nack(handle, "test nack");
            // Entry still pending — mirrors the broker's PEL retention.
            assertThat(adapter.pendingAckCount()).isEqualTo(1);
        }

        /**
         * F12 fault-injection analogue (ADR-003 §2.6 F6). A worker:
         *   1. polls an entry from the broker
         *   2. "dies" before acking
         *   3. the reaper later XCLAIMs the entry and re-delivers it
         * The InMemory contract must support this same lifecycle: after
         * poll-without-ack, the entry remains visible to a reaper-style
         * recovery caller.
         *
         * <p>This is the InMemory equivalent of the F12 acceptance
         * criterion ("XCLAIM 转过来的 entry 真被 worker 处理, 不挂在
         * PEL 里"). The Redisson Streams adapter (M3c-2) implements the
         * same contract against the real broker; the production canary
         * gate (M3c-3b follow-up) drives the real Streams fault
         * injection.
         */
        @Test
        void f12ReclaimLifecycle() {
            adapter.enqueue(envelope("sub-A", 1L));
            // 1. Worker A polls, then "crashes" — no ack.
            JudgeJobHandle workerAHandle = adapter.poll(0L).orElseThrow();
            assertThat(adapter.pendingAckCount()).isEqualTo(1);

            // 2. A second poll sees nothing new (PEL semantics).
            assertThat(adapter.poll(0L)).isEmpty();

            // 3. Reaper-style recovery: in production this is the
            //    visibility-timeout-driven XCLAIM re-delivery. The
            //    InMemory contract has no XCLAIM; the equivalent is
            //    "the entry is still in pendingAcks and can be processed
            //    by a recovery caller." We assert that invariant here.
            //    The reaper (M3c-2) then acks on behalf of the lost
            //    worker, freeing the entry.
            assertThat(adapter.pendingAckCount()).isEqualTo(1);
            adapter.ack(workerAHandle);
            assertThat(adapter.pendingAckCount()).isZero();
        }
    }

    @Nested
    @DisplayName("timeout semantics")
    class TimeoutSemantics {

        @Test
        void pollWithTimeoutReturnsEmptyOnNoEntry() throws Exception {
            long started = System.nanoTime();
            Optional<JudgeJobHandle> result = adapter.poll(100L);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertThat(result).isEmpty();
            // Allow generous slack for thread scheduling.
            assertThat(elapsedMs).isLessThan(TimeUnit.SECONDS.toMillis(2));
        }

        @Test
        void pollWithTimeoutReturnsImmediatelyWhenEntryAvailable() throws Exception {
            adapter.enqueue(envelope("sub-A", 1L));
            CompletableFuture<Optional<JudgeJobHandle>> future = CompletableFuture.supplyAsync(
                    () -> adapter.poll(5_000L));
            // Give the consumer a moment to start its blocking poll.
            Thread.sleep(20L);
            assertThat(future.get(2, TimeUnit.SECONDS)).isPresent();
        }
    }
}
