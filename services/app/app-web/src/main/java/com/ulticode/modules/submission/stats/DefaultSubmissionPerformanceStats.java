package com.ulticode.modules.submission.stats;

import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.UserBestStats;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default adapter of {@link SubmissionPerformanceStats}.
 *
 * <p>Holds the per-user best aggregation query ({@code SubmissionMapper}) and
 * the pure percentile / distribution-bin math previously inlined in
 * {@code SubmissionServiceImpl}. The fenced-verdict write path and the legacy
 * {@code updateSubmissionResult} path both call {@link #compute} and then
 * decide independently how to persist the result (CAS-folded vs. unfenced
 * {@code updateById}) — this module does not touch persistence.
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class DefaultSubmissionPerformanceStats implements SubmissionPerformanceStats {

    /**
     * Number of buckets used when the distinct-value count exceeds the
     * exact-mode threshold (see {@link #buildDistributionBins}). 12 is the
     * chosen "small but readable" default for runtime/memory histograms.
     */
    private static final int DEFAULT_DISTRIBUTION_BIN_COUNT = 12;

    private final SubmissionMapper submissionMapper;

    @Override
    public PerformanceStats compute(Submission current, int runtime, Double memory) {
        // Per-user best stats aggregated in SQL. Bounded by distinct-user
        // count, not by total accepted submissions (see SubmissionMapper
        // #findBestStatsByProblemAndLanguage).
        List<UserBestStats> peerBest = submissionMapper.findBestStatsByProblemAndLanguage(
                current.getProblemId(), current.getLanguage());
        if (peerBest == null) {
            peerBest = List.of();
        }

        List<Double> peerRuntimes = new ArrayList<>();
        List<Double> peerMemories = new ArrayList<>();
        for (UserBestStats stats : peerBest) {
            // Skip the current user — "better than X% of OTHER users" is
            // the intended comparison axis, matching the previous in-memory
            // implementation.
            if (Objects.equals(stats.userId(), current.getUserId())) {
                continue;
            }
            if (stats.bestRuntimeMs() != null && stats.bestRuntimeMs() >= 0) {
                peerRuntimes.add(stats.bestRuntimeMs().doubleValue());
            }
            if (stats.bestMemoryMb() != null && stats.bestMemoryMb() >= 0) {
                peerMemories.add(stats.bestMemoryMb());
            }
        }

        Double runtimePercentile = null;
        List<Map<String, Number>> runtimeBins = List.of();
        if (runtime >= 0) {
            List<Double> runtimes = new ArrayList<>(peerRuntimes);
            runtimes.add((double) runtime);
            runtimePercentile = calculateBetterThanPercentile(runtimes, runtime);
            runtimeBins = buildDistributionBins(runtimes);
        }

        Double memoryPercentile = null;
        List<Map<String, Number>> memoryBins = List.of();
        if (memory != null && memory >= 0) {
            List<Double> memories = new ArrayList<>(peerMemories);
            memories.add(memory);
            memoryPercentile = calculateBetterThanPercentile(memories, memory);
            memoryBins = buildDistributionBins(memories);
        }

        return new PerformanceStats(runtimePercentile, runtimeBins, memoryPercentile, memoryBins);
    }

    private double calculateBetterThanPercentile(List<Double> values, double currentValue) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long slowerCount = values.stream()
                .filter(value -> value > currentValue)
                .count();
        return Math.round((slowerCount * 1000.0) / values.size()) / 10.0;
    }

    private List<Map<String, Number>> buildDistributionBins(List<Double> values) {
        if (values.isEmpty()) {
            return List.of();
        }

        Map<Double, Long> exactCounts = values.stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(
                        LinkedHashMap::new,
                        (counts, value) -> counts.merge(value, 1L, Long::sum),
                        LinkedHashMap::putAll);

        if (exactCounts.size() <= DEFAULT_DISTRIBUTION_BIN_COUNT) {
            return exactCounts.entrySet().stream()
                    .map(entry -> Map.<String, Number>of(
                            "bin", formatDistributionBin(entry.getKey()),
                            "count", entry.getValue()))
                    .toList();
        }

        double min = values.stream().min(Comparator.naturalOrder()).orElse(0.0);
        double max = values.stream().max(Comparator.naturalOrder()).orElse(min);
        if (Double.compare(min, max) == 0) {
            return List.of(Map.<String, Number>of(
                    "bin", formatDistributionBin(min),
                    "count", values.size()));
        }

        int bucketCount = DEFAULT_DISTRIBUTION_BIN_COUNT;
        double bucketSize = (max - min) / bucketCount;
        long[] counts = new long[bucketCount];
        for (Double value : values) {
            int index = (int) Math.floor((value - min) / bucketSize);
            counts[Math.min(index, bucketCount - 1)]++;
        }

        List<Map<String, Number>> bins = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            bins.add(Map.<String, Number>of(
                    "bin", formatDistributionBin(min + (bucketSize * i)),
                    "count", counts[i]));
        }
        return bins;
    }

    /**
     * Round a bin label to one decimal place. Always returns a
     * {@code double} so JSON consumers (frontend) see a single stable type
     * for the {@code bin} field regardless of whether the underlying
     * value happens to be an integer.
     */
    private double formatDistributionBin(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
