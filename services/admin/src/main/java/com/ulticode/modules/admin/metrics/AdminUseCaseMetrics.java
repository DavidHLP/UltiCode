package com.ulticode.modules.admin.metrics;

import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.time.TimeSource;
import com.ulticode.common.time.TimeSourceHolder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Records the bounded dependency shape of an Admin use case.
 *
 * <p>Logical calls and serial rounds are observations, not labels. Keeping them
 * as summary values avoids turning a changing call count into an unbounded
 * series while still exposing the maximum/count needed to compare a request
 * with the Admin budget manifest. Only the finite use-case, owner, degradation,
 * and freshness vocabularies become labels.
 *
 * <p>Metric collection is best effort. A broken registry, meter filter, or
 * timing source must never change the result of the request being measured.
 */
@Component
public final class AdminUseCaseMetrics {

    public static final String PREFIX = "admin.use_case";
    public static final String LOGICAL_CALLS = PREFIX + ".logical_calls";
    public static final String SERIAL_ROUNDS = PREFIX + ".serial_rounds";
    public static final String DURATION = PREFIX + ".duration";
    /** Alias used by budget terminology; duration is the measured wall time. */
    public static final String WALL_TIME = DURATION;
    public static final String DEGRADATION = PREFIX + ".degradation";
    public static final String FRESHNESS = PREFIX + ".freshness";
    public static final String AGGREGATE_OWNER = "all";
    public static final String UNKNOWN_USE_CASE = "UNKNOWN";
    /**
     * The manifest IDs are the complete label vocabulary. Unknown input is
     * collapsed so a caller cannot turn an account or request identifier into
     * a new time series.
     */
    private static final Pattern USE_CASE_PATTERN =
            Pattern.compile("[IWBS]-[A-Z0-9]+(?:-[A-Z0-9]+)*");
    private static final Set<String> USE_CASES = Set.of(
            "I-DASH-STATS",
            "I-DASH-CHART-OWNER",
            "I-DASH-CHART-USERS",
            "I-USER-LIST",
            "I-USER-DETAIL",
            "I-WS-AUTH",
            "I-CONTEST-LIST",
            "I-CONTEST-DETAIL",
            "I-CONTEST-RANKINGS",
            "I-CONTEST-ANNOUNCEMENTS",
            "I-FORUM-LIST",
            "I-FORUM-DETAIL",
            "I-FORUM-COMMUNITIES",
            "I-NOTIFY-LIST",
            "I-SOLUTION-LIST",
            "I-SOLUTION-DETAIL",
            "I-SUBMISSION-LIST",
            "I-SUBMISSION-DETAIL",
            "I-SUBMISSION-STATS",
            "I-SUBMISSION-FILTERS",
            "I-PROBLEM-READ",
            "I-PROBLEM-SUBMISSIONS",
            "I-TESTCASE-READ",
            "I-PROBLEM-LIST-LIST",
            "I-PROBLEM-LIST-DETAIL",
            "I-COMMENT-TYPED",
            "I-COMMENT-ALL",
            "I-TAG-READ",
            "I-ANALYTICS-OVERVIEW",
            "I-ANALYTICS-ACTIVITY",
            "I-ANALYTICS-PROBLEM",
            "I-ANALYTICS-CONTEST",
            "I-ANALYTICS-REVENUE",
            "I-ANALYTICS-PERFORMANCE",
            "I-AUDIT",
            "I-SETTINGS",
            "W-ONE-SHOT",
            "W-USER-CREATE",
            "W-USER-UPDATE",
            "W-USER-DELETE-RESET",
            "W-USER-PERMISSION",
            "W-PROFILE",
            "W-CONTEST-READBACK",
            "W-CONTEST-ONE",
            "W-PROBLEM-CREATE",
            "W-PROBLEM-UPDATE-STATE",
            "W-PROBLEM-DELETE",
            "W-NOTIFY-CREATE",
            "W-NOTIFY-UPDATE",
            "W-NOTIFY-DELETE",
            "W-SOLUTION-READBACK",
            "W-SOLUTION-DELETE",
            "W-CONTENT-CUTOVER",
            "W-PROBLIST-CREATE",
            "W-PROBLIST-PREFLIGHT",
            "W-TAG-FORUM",
            "W-TAG-PROBLEM",
            "W-TESTCASE-ONE",
            "W-TESTCASE-UPDATE",
            "B-USER-BAN",
            "B-USER-DELETE",
            "B-FORUM-TOGGLE",
            "B-FORUM-DELETE",
            "B-COMMENT-DELETE",
            "B-COMMENT-UNFLAG",
            "B-SOLUTION-SIMPLE",
            "B-SOLUTION-UNFLAG",
            "B-PROBLEM-PUBLISH",
            "B-PROBLEM-DELETE",
            "B-PROBLEM-RESTORE",
            "B-PROBLEM-EDIT",
            "B-PROBLEM-MODERATE",
            "B-PROBLEM-IMPORT",
            "B-TESTCASE-APPEND",
            "B-TESTCASE-REPLACE",
            "B-TESTCASE-REORDER",
            "B-REJUDGE",
            "B-PROBLIST-REPLACE",
            "B-PROBLEM-EXPORT",
            "S-BOOTSTRAP-ADMIN",
            "S-DEV-BOOTSTRAP",
            "S-RECON-FULL",
            "S-RECON-INCREMENTAL",
            "S-RECON-LEASE-BUSY");

    private final MeterRegistry meterRegistry;
    private final TimeSource timeSource;

    public enum Owner {
        APP,
        AUTH,
        SUBMISSION,
        NOTIFICATION,
        ADMIN
    }

    /** Freshness contract codes from the Admin budget manifest. */
    public enum Freshness {
        REQ,
        NOW,
        WM,
        CRON,
        WRB,
        LOCAL,
        UNKNOWN
    }

    @Autowired
    public AdminUseCaseMetrics(MeterRegistry meterRegistry) {
        this(meterRegistry, TimeSourceHolder.get());
    }

    public AdminUseCaseMetrics(MeterRegistry meterRegistry, TimeSource timeSource) {
        this.meterRegistry = meterRegistry;
        this.timeSource = timeSource;
    }

    /**
     * Measure one use-case invocation while preserving the action's result or
     * exception. The default classifier treats a successful return as healthy.
     */
    public <T> T observe(
            String useCase,
            Map<Owner, Integer> logicalCallsByOwner,
            int serialRounds,
            Freshness freshness,
            Supplier<T> action) {
        return observe(useCase, logicalCallsByOwner, serialRounds, freshness,
                ignored -> DegradationStatus.OK, action);
    }

    /**
     * Measure one use-case invocation and classify a successful result's
     * degradation state without changing the business result.
     */
    public <T> T observe(
            String useCase,
            Map<Owner, Integer> logicalCallsByOwner,
            int serialRounds,
            Freshness freshness,
            Function<T, DegradationStatus> degradationOf,
            Supplier<T> action) {
        long started = monotonicNanos();
        DegradationStatus degradation = DegradationStatus.OK;
        try {
            T result = action.get();
            if (degradationOf != null) {
                DegradationStatus classified = degradationOf.apply(result);
                degradation = classified == null ? DegradationStatus.OK : classified;
            }
            return result;
        } catch (RuntimeException | Error failure) {
            degradation = DegradationStatus.UNAVAILABLE;
            throw failure;
        } finally {
            record(useCase, logicalCallsByOwner, serialRounds,
                    elapsedNanos(started), degradation, freshness);
        }
    }

    /** Record a completed use-case observation with a monotonic wall-time delta. */
    public void record(
            String useCase,
            Map<Owner, Integer> logicalCallsByOwner,
            int serialRounds,
            long wallTimeNanos,
            DegradationStatus degradation,
            Freshness freshness) {
        if (meterRegistry == null) {
            return;
        }
        try {
            String boundedUseCase = boundedUseCase(useCase);
            recordLogicalCalls(boundedUseCase, logicalCallsByOwner);
            recordSummary(SERIAL_ROUNDS, boundedUseCase, AGGREGATE_OWNER,
                    Math.max(0, serialRounds));
            recordDuration(boundedUseCase, Math.max(0L, wallTimeNanos));
            recordCounter(DEGRADATION, boundedUseCase, AGGREGATE_OWNER,
                    "degradation", boundedDegradation(degradation));
            recordCounter(FRESHNESS, boundedUseCase, AGGREGATE_OWNER,
                    "freshness", boundedFreshness(freshness));
        } catch (RuntimeException ignored) {
            // Observability is deliberately non-critical to the business path.
        }
    }

    /** Convenience overload for callers that already hold a Duration. */
    public void record(
            String useCase,
            Map<Owner, Integer> logicalCallsByOwner,
            int serialRounds,
            Duration wallTime,
            DegradationStatus degradation,
            Freshness freshness) {
        long nanos;
        try {
            nanos = wallTime == null ? 0L : wallTime.toNanos();
        } catch (RuntimeException ignored) {
            nanos = 0L;
        }
        record(useCase, logicalCallsByOwner, serialRounds, nanos, degradation, freshness);
    }

    private void recordLogicalCalls(
            String useCase, Map<Owner, Integer> logicalCallsByOwner) {
        if (logicalCallsByOwner == null) {
            return;
        }
        for (Owner owner : Owner.values()) {
            Integer calls = logicalCallsByOwner.get(owner);
            if (calls != null && calls > 0) {
                recordSummary(LOGICAL_CALLS, useCase, owner.name(), calls);
            }
        }
    }

    private void recordSummary(String name, String useCase, String owner, double value) {
        try {
            DistributionSummary.builder(name)
                    .description("Admin use-case dependency budget observation")
                    .tag("use_case", useCase)
                    .tag("owner", owner)
                    .register(meterRegistry)
                    .record(value);
        } catch (RuntimeException ignored) {
            // A registry failure must not fail the measured operation.
        }
    }

    private void recordDuration(String useCase, long wallTimeNanos) {
        try {
            Timer.builder(DURATION)
                    .description("Admin use-case wall-clock duration")
                    .tag("use_case", useCase)
                    .tag("owner", AGGREGATE_OWNER)
                    .register(meterRegistry)
                    .record(wallTimeNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // A registry failure must not fail the measured operation.
        }
    }

    private void recordCounter(
            String name, String useCase, String owner, String valueTag, String value) {
        try {
            Counter.builder(name)
                    .description("Admin use-case outcome classification")
                    .tag("use_case", useCase)
                    .tag("owner", owner)
                    .tag(valueTag, value)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException ignored) {
            // A registry failure must not fail the measured operation.
        }
    }

    private long monotonicNanos() {
        try {
            return timeSource == null ? 0L : timeSource.monotonicNanos();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private long elapsedNanos(long started) {
        try {
            return Math.max(0L, monotonicNanos() - started);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String boundedUseCase(String value) {
        return value != null && USE_CASES.contains(value)
                && USE_CASE_PATTERN.matcher(value).matches()
                ? value : UNKNOWN_USE_CASE;
    }

    private static String boundedDegradation(DegradationStatus value) {
        return value == null ? DegradationStatus.OK.name() : value.name();
    }

    private static String boundedFreshness(Freshness value) {
        return value == null ? Freshness.UNKNOWN.name() : value.name();
    }
}
