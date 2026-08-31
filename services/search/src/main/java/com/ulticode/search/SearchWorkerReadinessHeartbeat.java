package com.ulticode.search;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.common.resilience.DependencyGuard;
import com.ulticode.search.config.SearchWorkerProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Review 2026-08-25 P0/P1: readiness heartbeat for the search worker.
 *
 * <p>The worker process exposes no HTTP surface, so production Compose cannot
 * probe it directly. Instead this heartbeat proves both hard dependencies on
 * every cycle &mdash; Redis connectivity (stream source) and MeiliSearch
 * availability (sole write target) &mdash; and refreshes a timestamped marker
 * file only while both answer. The container healthcheck treats the marker as
 * stale after two minutes, so a dead dependency takes the replica out of the
 * {@code service_healthy} gate instead of faking health.</p>
 *
 * <p>Deliberately decoupled from {@link SearchDocumentIndexWorker#consume()}:
 * indexing failures are retried through the PEL and would not otherwise fail
 * the probe, but an unreachable MeiliSearch must.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.worker.enabled", havingValue = "true")
public class SearchWorkerReadinessHeartbeat {

    private final StringRedisTemplate redisTemplate;
    private final Client meiliSearchClient;
    private final Path readyFile;
    private final DrainGate drainGate = new DrainGate();
    private final DependencyGuard meiliSearchGuard =
            new DependencyGuard(1, 5, Duration.ofSeconds(30));

    public SearchWorkerReadinessHeartbeat(
            StringRedisTemplate redisTemplate,
            Client meiliSearchClient,
            @Value("${search.worker.ready-file:}") String readyFile) {
        this.redisTemplate = redisTemplate;
        this.meiliSearchClient = meiliSearchClient;
        this.readyFile = readyFile == null || readyFile.isBlank() ? null : Path.of(readyFile);
    }

    /** Re-proves dependencies and refreshes the marker; skips the write on any failure. */
    @Scheduled(scheduler = "searchHeartbeatScheduler",
            fixedDelayString = "${search.worker.heartbeat-interval-ms:10000}",
               initialDelayString = "${search.worker.heartbeat-initial-delay-ms:5000}")
    public void beat() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            if (!dependenciesUp() || readyFile == null) {
                return;
            }
            if (drainGate.isDraining()) {
                return;
            }
            try {
                Files.writeString(readyFile, String.valueOf(System.currentTimeMillis()),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                log.warn("Failed to refresh readiness marker {}: {}", readyFile, e.getMessage());
            }
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
        if (readyFile != null) {
            try {
                Files.deleteIfExists(readyFile);
            } catch (IOException e) {
                log.debug("Failed to remove readiness marker {}: {}", readyFile, e.getMessage());
            }
        }
    }

    private boolean dependenciesUp() {
        try {
            String pong = redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping());
            if (!"PONG".equalsIgnoreCase(pong)) {
                return false;
            }
        } catch (RuntimeException e) {
            log.debug("Redis ping failed: {}", e.getMessage());
            return false;
        }
        DependencyGuard.Permit permit;
        try {
            permit = meiliSearchGuard.acquire();
        } catch (DependencyGuard.RejectedException rejected) {
            log.debug("MeiliSearch health check rejected: {}", rejected.getMessage());
            return false;
        }
        try (permit) {
            try {
                meiliSearchClient.health();
                permit.success();
                return true;
            } catch (MeilisearchException unavailable) {
                permit.failure();
                log.debug("MeiliSearch health check failed: {}", unavailable.getMessage());
                return false;
            } catch (RuntimeException failure) {
                permit.ignore();
                log.debug("MeiliSearch health check failed: {}", failure.getMessage());
                return false;
            }
        }
    }
}
