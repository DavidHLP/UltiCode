package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.migration.JudgeStreamLegacyMigration;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeStreamKeys;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
        // Deliberately no setCodec: Redisson 4.3.1 defaults to Kryo5Codec.
        return Redisson.create(config);
    }

    @Test
    @DisplayName("enqueue -> poll -> ack round-trips through Lua + Streams with the default client codec")
    void enqueuePollAckRoundTrips() {
        String streamKey = "judge:it:" + UUID.randomUUID();
        RedissonClient client = defaultCodecClient();
        try {
            RedissonStreamsJudgeQueueAdapter adapter = adapter(client, streamKey, 5);
            adapter.ensureGroup();
            String submissionId = "sub-" + UUID.randomUUID();
            adapter.enqueue(envelope(submissionId));
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
            stream.claim("it-workers", "claimer-1", 0, TimeUnit.MILLISECONDS, id);
            stream.claim("it-workers", "claimer-2", 0, TimeUnit.MILLISECONDS, id);

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
                "it-workers",
                consumerName,
                1_000L,
                maxAttempts,
                null);
    }

    private JudgeJobEnvelope envelope(String submissionId) {
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
                2L,
                "attempt-1");
    }
}
