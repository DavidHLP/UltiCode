package com.ulticode.modules.submission.dto;

import java.util.List;
import java.util.Map;

/**
 * Immutable value object returned by
 * {@code SubmissionServiceImpl.computePerformanceStats} describing how the
 * current submission's runtime and memory compare against the per-user best
 * values of other users who solved the same problem in the same language.
 *
 * <p>All four fields are independently nullable. The "percentile" is a
 * "better than X%" metric: 100.0 means the current submission is faster /
 * uses less memory than every other user, 0.0 means it is the slowest /
 * hungriest of all. Distribution bins follow the same shape used by
 * {@code runtimeDistBinsMs} / {@code memoryDistBinsMb} elsewhere in the
 * VO layer.
 *
 * @param runtimePercentile   "better than X% of users" for runtime, or null
 *                            if not computed (e.g. invalid runtime)
 * @param runtimeDistBinsMs   distribution bins in milliseconds, or null if
 *                            not computed
 * @param memoryPercentile    "better than X% of users" for memory, or null
 *                            if memory was not provided
 * @param memoryDistBinsMb    distribution bins in MB, or null if memory was
 *                            not provided
 */
public record PerformanceStats(
        Double runtimePercentile,
        List<Map<String, Number>> runtimeDistBinsMs,
        Double memoryPercentile,
        List<Map<String, Number>> memoryDistBinsMb
) {

    /**
     * Empty stats instance. Returned when no peer data is available or
     * the supplied runtime/memory are invalid; callers can pass it through
     * to VOs without further null checks.
     */
    public static final PerformanceStats EMPTY = new PerformanceStats(
            null, List.of(), null, List.of());
}
