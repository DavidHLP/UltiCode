package com.ulticode.admin.adapter.in.web;

import com.ulticode.common.health.ReadinessChecks;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Readiness probe for the backend-admin owner.
 *
 * <p>Review 2026-08-25 P0: production Compose gates {@code service_healthy}
 * on this endpoint. Unlike the static liveness endpoint, it verifies the
 * runtime dependencies the owner needs to serve traffic &mdash; the owner
 * database and Redis &mdash; and returns 503 when either is unavailable so
 * dependents do not start against a broken instance.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminReadinessController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public AdminReadinessController(DataSource dataSource, StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Boolean> components = new LinkedHashMap<>();
        components.put("db", ReadinessChecks.dataSourceUp(dataSource));
        components.put("redis", redisUp());
        boolean allUp = components.values().stream().allMatch(Boolean::booleanValue);
        return ResponseEntity
                .status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ReadinessChecks.report(components));
    }

    private boolean redisUp() {
        try {
            String pong = redisTemplate.execute(
                    (RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
