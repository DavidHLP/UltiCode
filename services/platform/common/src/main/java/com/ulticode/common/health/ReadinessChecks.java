package com.ulticode.common.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Shared readiness-probe helpers for owner services.
 *
 * <p>Review 2026-08-25 P0: the static-text {@code /health} controllers cannot
 * prove that a service can actually serve traffic, yet production Compose uses
 * them as {@code service_healthy} gates. Each owner now exposes an additional
 * readiness endpoint built from these helpers: it verifies the real runtime
 * dependencies (owner database, Redis) and answers 503 when any required
 * component is down, while the original endpoint stays as the process-only
 * liveness probe.</p>
 */
public final class ReadinessChecks {

    /** Aggregate status when every required component answered successfully. */
    public static final String UP = "UP";

    /** Aggregate status when at least one required component failed. */
    public static final String DOWN = "DOWN";

    private ReadinessChecks() {
    }

    /**
     * Proves the datasource hands out usable connections within a bounded
     * timeout. Never throws: a failure is reported as {@code false} so probes
     * degrade to 503 instead of 500.
     *
     * @param dataSource the owner-service primary datasource
     * @return {@code true} when {@link Connection#isValid(int)} succeeds
     */
    public static boolean dataSourceUp(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Builds the probe response body: {@code {"status": .., "components": {..}}}.
     *
     * @param components component name to up/down flag, in report order
     * @return response map with aggregate {@code status}
     */
    public static Map<String, Object> report(Map<String, Boolean> components) {
        boolean allUp = components.values().stream().allMatch(Boolean::booleanValue);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allUp ? UP : DOWN);
        Map<String, Object> detail = new LinkedHashMap<>();
        components.forEach((name, up) -> detail.put(name, Boolean.TRUE.equals(up) ? UP : DOWN));
        body.put("components", detail);
        return body;
    }
}
