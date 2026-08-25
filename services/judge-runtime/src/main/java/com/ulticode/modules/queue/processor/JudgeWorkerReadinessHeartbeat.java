package com.ulticode.modules.queue.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Review 2026-08-25 P0: readiness heartbeat for the judge runtime.
 *
 * <p>The judge process has no HTTP surface, so its container probe combines
 * shell-level sandbox capability checks (docker socket + sandbox image) with
 * this marker: a lightweight scheduled ping proves the Redis Streams
 * dependency stays reachable and refreshes a timestamped marker file only
 * while it does. A stale marker makes the replica fail the Compose
 * {@code service_healthy} gate instead of faking health.</p>
 *
 * <p>Deliberately decoupled from the job-processing loops: a long-running
 * judging job blocks the poll thread and must not be reported as unready.</p>
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.runtime.role:api}' == 'judge'")
public class JudgeWorkerReadinessHeartbeat {

    private final StringRedisTemplate redisTemplate;

    private final Path readyFile;

    public JudgeWorkerReadinessHeartbeat(
            StringRedisTemplate redisTemplate,
            @Value("${judge.ready-file:}") String readyFile) {
        this.redisTemplate = redisTemplate;
        this.readyFile = readyFile == null || readyFile.isBlank() ? null : Path.of(readyFile);
    }

    /** Pings Redis and refreshes the marker; skips the write while Redis is down. */
    @Scheduled(fixedDelayString = "${judge.heartbeat-interval-ms:10000}",
               initialDelayString = "${judge.heartbeat-initial-delay-ms:5000}")
    public void beat() {
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) c -> c.ping());
            if (!"PONG".equalsIgnoreCase(pong)) {
                return;
            }
        } catch (RuntimeException e) {
            log.debug("Redis ping failed: {}", e.getMessage());
            return;
        }
        if (readyFile == null) {
            return;
        }
        try {
            Files.writeString(readyFile, String.valueOf(System.currentTimeMillis()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.warn("Failed to refresh readiness marker {}: {}", readyFile, e.getMessage());
        }
    }
}
