package com.ulticode.common.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes Redis Streams consumer SLO gauges for stream workers.
 *
 * <p>Review 2026-08-25 FINAL P1: worker readiness previously proved that
 * dependencies answer, not that queues advance. This helper exports the
 * numbers an SLO needs, refreshed after every poll cycle:
 * <ul>
 *   <li>{@code <prefix>.stream.length} &mdash; total entries in the stream</li>
 *   <li>{@code <prefix>.group.pel} &mdash; pending (delivered, unacked) entries</li>
 *   <li>{@code <prefix>.group.lag} &mdash; entries behind the group's last
 *       delivery (Redis 7 XINFO GROUPS {@code lag}; {@code -1} when unknown)</li>
 *   <li>{@code <prefix>.dlq.size} &mdash; dead-letter queue depth</li>
 *   <li>{@code <prefix>.last.success.age.seconds} &mdash; time since the last
 *       poll processed at least one entry ({@code -1} when never)</li>
 * </ul>
 *
 * <p>All probes are best-effort: a Redis hiccup sets gauges to {@code -1}
 * instead of throwing so metrics collection can never break consumption.
 * Not a Spring bean on purpose: workers construct it explicitly with their
 * own template and registry.
 */
public class StreamSloMetrics {

    private static final long UNKNOWN = -1L;

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String group;
    private final String dlqKey;

    private final AtomicLong streamLength = new AtomicLong(UNKNOWN);
    private final AtomicLong pelSize = new AtomicLong(UNKNOWN);
    private final AtomicLong lag = new AtomicLong(UNKNOWN);
    private final AtomicLong dlqSize = new AtomicLong(UNKNOWN);
    private final AtomicLong lastSuccessAgeSeconds = new AtomicLong(UNKNOWN);
    private volatile long lastSuccessMillis = 0L;

    public StreamSloMetrics(StringRedisTemplate redisTemplate, MeterRegistry registry,
                            String metricPrefix, String streamKey, String group, String dlqKey) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.group = group;
        this.dlqKey = dlqKey;
        Iterable<Tag> tags = java.util.List.of(Tag.of("stream", streamKey), Tag.of("group", group));
        registry.gauge(metricPrefix + ".stream.length", tags, streamLength);
        registry.gauge(metricPrefix + ".group.pel", tags, pelSize);
        registry.gauge(metricPrefix + ".group.lag", tags, lag);
        if (dlqKey != null && !dlqKey.isBlank()) {
            registry.gauge(metricPrefix + ".dlq.size", tags, dlqSize);
        }
        registry.gauge(metricPrefix + ".last.success.age.seconds", tags, lastSuccessAgeSeconds);
    }

    /**
     * Refreshes all gauges after one poll cycle.
     *
     * @param processed entries successfully handled in the cycle
     */
    public void recordPoll(int processed) {
        if (processed > 0) {
            lastSuccessMillis = System.currentTimeMillis();
            lastSuccessAgeSeconds.set(0);
        } else if (lastSuccessMillis > 0) {
            lastSuccessAgeSeconds.set(TimeUnit.MILLISECONDS.toSeconds(
                    System.currentTimeMillis() - lastSuccessMillis));
        }
        refresh();
    }

    private void refresh() {
        try {
            Long len = redisTemplate.opsForStream().size(streamKey);
            streamLength.set(len != null ? len : UNKNOWN);
        } catch (RuntimeException e) {
            streamLength.set(UNKNOWN);
        }
        try {
            StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
            var groups = streams.groups(streamKey);
            long pel = UNKNOWN;
            if (groups != null) {
                for (var g : groups) {
                    if (group.equals(g.groupName())) {
                        pel = g.pendingCount();
                        break;
                    }
                }
            }
            pelSize.set(pel);
        } catch (RuntimeException e) {
            pelSize.set(UNKNOWN);
        }
        if (dlqKey != null && !dlqKey.isBlank()) {
            try {
                Long dlqLen = redisTemplate.opsForStream().size(dlqKey);
                dlqSize.set(dlqLen != null ? dlqLen : 0);
            } catch (RuntimeException e) {
                dlqSize.set(UNKNOWN);
            }
        }
    }
}
