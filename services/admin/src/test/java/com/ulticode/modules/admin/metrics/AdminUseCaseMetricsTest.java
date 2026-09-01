package com.ulticode.modules.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.time.FakeTimeSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdminUseCaseMetricsTest {

    @Test
    void recordsFanoutRoundsDurationDegradationAndFreshnessWithoutIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminUseCaseMetrics metrics = new AdminUseCaseMetrics(
                registry, new FakeTimeSource(0L, 0L));

        metrics.record(
                "I-DASH-STATS",
                Map.of(
                        AdminUseCaseMetrics.Owner.APP, 1,
                        AdminUseCaseMetrics.Owner.AUTH, 1,
                        AdminUseCaseMetrics.Owner.SUBMISSION, 1),
                1,
                Duration.ofMillis(42),
                DegradationStatus.OK,
                AdminUseCaseMetrics.Freshness.NOW);

        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-DASH-STATS", "owner", "APP")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-DASH-STATS", "owner", "AUTH")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.SERIAL_ROUNDS)
                .tags("use_case", "I-DASH-STATS", "owner", "all")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.DURATION)
                .tags("use_case", "I-DASH-STATS", "owner", "all")
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(42D);
        assertThat(registry.find(AdminUseCaseMetrics.DEGRADATION)
                .tags("use_case", "I-DASH-STATS", "owner", "all", "degradation", "OK")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.FRESHNESS)
                .tags("use_case", "I-DASH-STATS", "owner", "all", "freshness", "NOW")
                .counter().count()).isEqualTo(1D);

        Set<String> tagValues = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue())
                .collect(Collectors.toSet());
        assertThat(tagValues).doesNotContain("account-123", "user-456");
    }

    @Test
    void boundsUnknownUseCasesAndNeverEmitsAccountOrUserLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminUseCaseMetrics metrics = new AdminUseCaseMetrics(
                registry, new FakeTimeSource());

        metrics.record(
                "account-123",
                Map.of(AdminUseCaseMetrics.Owner.AUTH, 1),
                1,
                1L,
                DegradationStatus.PARTIAL,
                AdminUseCaseMetrics.Freshness.REQ);

        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", AdminUseCaseMetrics.UNKNOWN_USE_CASE, "owner", "AUTH")
                .summary().count()).isEqualTo(1L);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue()).doesNotContain("account-123", "user-456")));
    }

    @Test
    void observePreservesActionResultAndException() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FakeTimeSource time = new FakeTimeSource();
        AdminUseCaseMetrics metrics = new AdminUseCaseMetrics(registry, time);

        String result = metrics.observe(
                "I-ANALYTICS-REVENUE",
                Map.of(AdminUseCaseMetrics.Owner.APP, 1),
                1,
                AdminUseCaseMetrics.Freshness.REQ,
                () -> "ok");
        assertThat(result).isEqualTo("ok");

        IllegalStateException failure = new IllegalStateException("owner offline");
        assertThatThrownBy(() -> metrics.observe(
                "I-ANALYTICS-REVENUE",
                Map.of(AdminUseCaseMetrics.Owner.APP, 1),
                1,
                AdminUseCaseMetrics.Freshness.REQ,
                () -> { throw failure; }))
                .isSameAs(failure);
        assertThat(registry.find(AdminUseCaseMetrics.DEGRADATION)
                .tags("use_case", "I-ANALYTICS-REVENUE", "owner", "all",
                        "degradation", "UNAVAILABLE")
                .counter().count()).isEqualTo(1D);
    }

    @Test
    void registryFailuresDoNotEscapeTheBusinessPath() {
        MeterRegistry brokenRegistry = mock(MeterRegistry.class);
        when(brokenRegistry.counter(anyString(), any(String[].class)))
                .thenThrow(new IllegalStateException("metrics unavailable"));
        AdminUseCaseMetrics metrics = new AdminUseCaseMetrics(
                brokenRegistry, new FakeTimeSource());

        assertThatCode(() -> metrics.record(
                "I-DASH-STATS",
                Map.of(AdminUseCaseMetrics.Owner.APP, 1),
                1,
                1L,
                DegradationStatus.OK,
                AdminUseCaseMetrics.Freshness.NOW)).doesNotThrowAnyException();
        assertThat(metrics.observe(
                "I-DASH-STATS",
                Map.of(AdminUseCaseMetrics.Owner.APP, 1),
                1,
                AdminUseCaseMetrics.Freshness.NOW,
                () -> "business-result")).isEqualTo("business-result");
    }
}
