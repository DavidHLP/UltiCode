package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.migration.JudgeStreamLegacyMigration;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.processor.DefaultJudgeAttemptExecutor;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeStreamKeys;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.Config;
import org.redisson.Redisson;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Real-Redis smoke test for the Streams adapter (CR P1-1/P1-4/P1-3).
 *
 * <p>Runs only when {@code REDIS_HOST} is set (local dev + CI backend-test
 * both provide it). Uses a client with the DEFAULT config codec (Kryo5),
 * exactly like the Spring Boot starter wiring, to prove that the adapter's
 * explicit {@link StringCodec} is what makes Lua XADD fields and script
 * ARGV round-trip against a real broker.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
@DisplayName("Redis Streams adapter against a real broker")
class JudgeStreamRedisIntegrationTest {

    private static final String GROUP = "it-workers";

    private static String redisAddress() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        String port = System.getenv().getOrDefault("REDIS_PORT", "26379");
        return "redis://" + host + ":" + port;
    }

    private static RedissonClient defaultCodecClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress());
        String username = System.getenv("REDIS_USERNAME");
        if (username != null && !username.isBlank()) {
            config.useSingleServer().setUsername(username);
        }
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }
        // Deliberately no setCodec: Redisson defaults to Kryo5Codec.
        return Redisson.create(config);
    }

    @Test
    @DisplayName("concurrent Judge startup creates one group on an empty stream without writing entries")
    void concurrentJudgeStartupCreatesGroupWithoutAddingEntries() throws Exception {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = adapter(client, streamKey, 5, "it-consumer-1");
            var second = adapter(client, streamKey, 5, "it-consumer-2");
            var start = new CyclicBarrier(2);
            var firstStartup = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                first.ensureGroup();
                return null;
            });
            var secondStartup = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                second.ensureGroup();
                return null;
            });
            firstStartup.get(5, TimeUnit.SECONDS);
            secondStartup.get(5, TimeUnit.SECONDS);

            var stream = client.<String, String>getStream(streamKey, StringCodec.INSTANCE);
            assertThat(stream.listGroups()).hasSize(1);
            assertThat(stream.getInfo().getEntriesAdded()).isZero();

            var job = envelope("sub-" + UUID.randomUUID());
            first.enqueue(job);
            assertThat(first.poll(0)).hasValueSatisfying(handle -> assertThat(handle.envelope()).isEqualTo(job));
            second.ensureGroup();
            assertThat(second.poll(0)).isEmpty();
            assertThat(second.pendingDepth()).isEqualTo(1L);
        } finally {
            workers.shutdownNow();
            client.shutdown();
        }
    }

    @Test
    @DisplayName("Judge startup reads and acknowledges a job queued before its consumer group existed")
    void enqueuePollAckRoundTrips() {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            RedissonStreamsJudgeQueueAdapter adapter = adapter(client, streamKey, 5);
            String submissionId = "sub-" + UUID.randomUUID();
            adapter.enqueue(envelope(submissionId));
            adapter.ensureGroup();
            Optional<JudgeJobHandle> handle = adapter.poll(2_000);

            assertThat(handle).as("poll must decode the Lua-XADDed entry").isPresent();
            assertThat(handle.get().envelope().submissionId()).isEqualTo(submissionId);
            assertThat(handle.get().envelope().generation()).isEqualTo(2L);
            adapter.ack(handle.get());
            assertThat(adapter.pendingDepth()).isZero();
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("group loss replays older entries and the Judge fence acknowledges superseded work without a verdict")
    void groupLossReplaysBacklogThroughGenerationFence() {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            var adapter = adapter(client, streamKey, 5);
            var stale = envelope("sub-" + UUID.randomUUID());
            var current = envelope(stale.submissionId(), 3L);
            adapter.ensureGroup();
            adapter.enqueue(stale);
            var firstDelivery = adapter.poll(0).orElseThrow();
            adapter.ack(firstDelivery);
            adapter.enqueue(current);
            client.getStream(streamKey, StringCodec.INSTANCE).removeGroup(GROUP);

            var replayed = adapter.poll(0).orElseThrow();
            assertThat(replayed).isEqualTo(firstDelivery);
            assertThat(adapter.pendingDepth()).isEqualTo(1L);

            var fence = mock(SubmissionFencePort.class);
            when(fence.currentGeneration(stale.submissionId())).thenReturn(current.generation());
            var verdicts = mock(SubmissionVerdictWritePort.class);
            var pipeline = mock(JudgeExecutionPipeline.class);
            var executor = new DefaultJudgeAttemptExecutor(verdicts, pipeline, fence, null, null, null);
            var job = new JudgeJob();
            job.setSubmissionId(stale.submissionId());
            executor.runAttempt(job, adapter, replayed);

            verify(fence).currentGeneration(stale.submissionId());
            verifyNoMoreInteractions(fence);
            verifyNoInteractions(pipeline, verdicts);
            assertThat(adapter.pendingDepth()).isZero();
            assertThat(adapter.poll(0)).hasValueSatisfying(handle -> assertThat(handle.envelope()).isEqualTo(current));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("a replacement consumer reclaims an unacked entry after worker loss")
    void replacementConsumerReclaimsUnackedEntry() {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            RedissonStreamsJudgeQueueAdapter first = adapter(client, streamKey, 5, "it-consumer-1");
            first.ensureGroup();
            first.enqueue(envelope("sub-" + UUID.randomUUID()));
            assertThat(first.poll(2_000)).isPresent();
            assertThat(first.pendingDepth()).isEqualTo(1);

            RedissonStreamsJudgeQueueAdapter replacement =
                    adapter(client, streamKey, 5, "it-consumer-2");
            Optional<JudgeJobHandle> reclaimed = replacement.claimIdle(0L);

            assertThat(reclaimed).as("replacement must reclaim the crashed worker's PEL entry").isPresent();
            replacement.ack(reclaimed.get());
            assertThat(replacement.pendingDepth()).isZero();
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("malformed stream payload is acknowledged as poison without business delivery")
    void malformedPayloadIsPoisonAcked() {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            RedissonStreamsJudgeQueueAdapter adapter = adapter(client, streamKey, 5);
            adapter.ensureGroup();
            RStream<String, String> stream = client.getStream(streamKey, StringCodec.INSTANCE);
            stream.add(org.redisson.api.stream.StreamAddArgs.entry("payload", "{not-json"));

            assertThat(adapter.poll(2_000)).isEmpty();
            assertThat(adapter.pendingDepth()).isZero();
        } finally {
            client.shutdown();
        }
    }


    @Test
    @DisplayName("dedup marker short-circuits a repeated enqueue without a second XADD")
    void dedupSkipsSecondEnqueue() {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            RedissonStreamsJudgeQueueAdapter adapter = adapter(client, streamKey, 5);
            adapter.ensureGroup();
            String submissionId = "sub-" + UUID.randomUUID();
            adapter.enqueue(envelope(submissionId));
            adapter.enqueue(envelope(submissionId));

            RStream<String, String> stream = client.getStream(streamKey, StringCodec.INSTANCE);
            assertThat(stream.size()).isEqualTo(1);
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("an entry at the delivery budget is dead-lettered without an extra XCLAIM")
    void exhaustedEntryGoesToDlqWithoutClaim() throws Exception {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            RedissonStreamsJudgeQueueAdapter adapter = adapter(client, streamKey, 3);
            adapter.ensureGroup();
            adapter.enqueue(envelope("sub-" + UUID.randomUUID()));

            // Read into the PEL once, then XCLAIM twice more: broker
            // delivery count reaches 3 == maxDeliveryAttempts.
            Optional<JudgeJobHandle> first = adapter.poll(2_000);
            assertThat(first).isPresent();
            StreamMessageId id = (StreamMessageId) first.get().ackToken();
            RStream<String, String> stream = client.getStream(streamKey, StringCodec.INSTANCE);
            stream.claim(GROUP, "claimer-1", 0, TimeUnit.MILLISECONDS, id);
            stream.claim(GROUP, "claimer-2", 0, TimeUnit.MILLISECONDS, id);

            Optional<JudgeJobHandle> reclaimed = adapter.claimIdle(0L);

            assertThat(reclaimed).as("budget-exhausted entry must not be returned for processing").isEmpty();
            assertThat(adapter.pendingDepth()).isZero();
            RStream<String, String> dlq = client.getStream(
                    JudgeStreamKeys.JUDGE_STREAM_DLQ_KEY, StringCodec.INSTANCE);
            assertThat(dlq.isExists()).isTrue();
            Map<StreamMessageId, Map<String, String>> entries = dlq.range(10,
                    StreamMessageId.MIN, StreamMessageId.MAX);
            assertThat(entries).isNotEmpty();
            assertThat(entries.values().iterator().next().get("reason"))
                    .isEqualTo("max-delivery-attempts");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("legacy judge:stream entries are drained into the current stream and the old key removed")
    void legacyStreamIsMigrated() {
        String legacyKey = JudgeStreamKeys.LEGACY_JUDGE_STREAM_KEY;
        String currentKey = JudgeStreamKeys.JUDGE_STREAM_KEY;
        RedissonClient client = defaultCodecClient();
        try {
            // Clean slate for the fixed production keys used by the migration.
            client.getKeys().delete(legacyKey, currentKey,
                    JudgeStreamKeys.JUDGE_STREAM_MIGRATION_LOCK_KEY);
            // Old producer wrote with the default Kryo5 codec (pre-extraction App).
            RStream<String, String> legacy = client.getStream(legacyKey, new Kryo5Codec());
            legacy.add(org.redisson.api.stream.StreamAddArgs.entry("payload", "{\"v\":\"old-job\"}"));
            legacy.add(org.redisson.api.stream.StreamAddArgs.entry("payload", "{\"v\":\"old-job-2\"}"));

            new JudgeStreamLegacyMigration(client).migrateLegacyStream();

            RStream<String, String> current = client.getStream(currentKey, StringCodec.INSTANCE);
            assertThat(current.size()).isEqualTo(2);
            assertThat(client.getKeys().countExists(legacyKey)).isZero();
            Map<StreamMessageId, Map<String, String>> drained = current.range(10,
                    StreamMessageId.MIN, StreamMessageId.MAX);
            assertThat(drained.values().iterator().next().get("payload")).contains("old-job");
        } finally {
            client.getKeys().delete(legacyKey, currentKey,
                    JudgeStreamKeys.JUDGE_STREAM_MIGRATION_LOCK_KEY);
            client.shutdown();
        }
    }

    private RedissonStreamsJudgeQueueAdapter adapter(RedissonClient client, String streamKey, int maxAttempts) {
        return adapter(client, streamKey, maxAttempts, "it-consumer");
    }

    private RedissonStreamsJudgeQueueAdapter adapter(
            RedissonClient client, String streamKey, int maxAttempts, String consumerName) {
        return new RedissonStreamsJudgeQueueAdapter(
                client,
                // Lenient like the judge runtime ObjectMapper
                // (FAIL_ON_UNKNOWN_PROPERTIES off) so the serialized
                // isFenceAware getter does not poison every v2 entry.
                new ObjectMapper().configure(
                        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                streamKey,
                GROUP,
                consumerName,
                1_000L,
                maxAttempts,
                null);
    }

    private JudgeJobEnvelope envelope(String submissionId) {
        return envelope(submissionId, 2L);
    }

    private JudgeJobEnvelope envelope(String submissionId, long generation) {
        return new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_2,
                "job-1",
                submissionId,
                "problem-1",
                "user-1",
                "java",
                "class Main {}",
                2_000,
                256 * 1024,
                generation,
                "attempt-" + generation);
    }
}
