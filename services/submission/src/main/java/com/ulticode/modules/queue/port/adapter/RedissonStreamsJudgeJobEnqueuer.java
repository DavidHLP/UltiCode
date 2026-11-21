package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.port.JudgeJobEnqueuer;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeStreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class RedissonStreamsJudgeJobEnqueuer implements JudgeJobEnqueuer {

    private static final long DEDUP_TTL_SECONDS = JudgeStreamKeys.JUDGE_STREAM_VISIBILITY_TIMEOUT_MS * 5L / 1000L;

    // Keep the dedup marker and stream entry atomic so a failed XADD leaves the outbox retryable.
    private static final String ATOMIC_ENQUEUE_SCRIPT = """
            local claimed = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
            if not claimed then
                return 0
            end
            local added = redis.pcall('XADD', KEYS[2], '*', 'payload', ARGV[3])
            if type(added) == 'table' and added['err'] then
                redis.call('DEL', KEYS[1])
                return -1
            end
            return 1
            """;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Override
    public void enqueue(JudgeJobEnvelope envelope) {
        String dedupKey = JudgeStreamKeys.JUDGE_DISPATCH_SEEN_PREFIX
                + envelope.submissionId() + ":" + envelope.generation();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JudgeJobEnvelope", e);
        }

        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                ATOMIC_ENQUEUE_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(dedupKey, JudgeStreamKeys.JUDGE_STREAM_KEY),
                "1",
                Long.toString(DEDUP_TTL_SECONDS),
                payload);
        if (result != null && result == 0L) {
            log.debug("Streams enqueue dedup: skipping repeat for {} gen {}",
                    envelope.submissionId(), envelope.generation());
            return;
        }
        if (result == null || result < 0L) {
            throw new IllegalStateException("Failed to atomically enqueue JudgeJobEnvelope");
        }
    }
}
