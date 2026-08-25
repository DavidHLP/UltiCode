package com.ulticode.common.health;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review 2026-08-25 P0: readiness report aggregation used by the owner
 * readiness endpoints.
 */
@DisplayName("ReadinessChecks")
class ReadinessChecksTest {

    @Test
    @DisplayName("report is UP only when every component is up")
    void reportAggregatesAllComponents() {
        Map<String, Boolean> allUp = new LinkedHashMap<>();
        allUp.put("db", true);
        allUp.put("redis", true);
        Map<String, Object> body = ReadinessChecks.report(allUp);
        assertThat(body.get("status")).isEqualTo(ReadinessChecks.UP);
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertThat(components).containsEntry("db", "UP").containsEntry("redis", "UP");
    }

    @Test
    @DisplayName("report is DOWN when any component fails")
    void reportFlagsFailedComponent() {
        Map<String, Boolean> oneDown = new LinkedHashMap<>();
        oneDown.put("db", true);
        oneDown.put("redis", false);
        assertThat(ReadinessChecks.report(oneDown).get("status")).isEqualTo(ReadinessChecks.DOWN);
    }

    @Test
    @DisplayName("null datasource reports down instead of throwing")
    void nullDataSourceIsDown() {
        assertThat(ReadinessChecks.dataSourceUp(null)).isFalse();
    }
}
