package com.ulticode.recommend.core.evaluator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds offline evaluation metrics for recommendation quality assessment.
 * <p>
 * Contains metrics for measuring precision, recall, ranking quality,
 * catalog coverage, and recommendation diversity.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineMetrics {

    private int k;
    private double precision;
    private double recall;
    private double f1Score;
    private double ndcg;
    private double coverage;
    private double diversity;

    /**
     * Creates an empty metrics object with all values set to 0.
     *
     * @return an empty metrics object
     */
    public static OfflineMetrics empty() {
        return OfflineMetrics.builder()
                .k(0)
                .precision(0.0)
                .recall(0.0)
                .f1Score(0.0)
                .ndcg(0.0)
                .coverage(0.0)
                .diversity(0.0)
                .build();
    }

    @Override
    public String toString() {
        return String.format(
                "OfflineMetrics{k=%d, precision=%.4f, recall=%.4f, f1Score=%.4f, " +
                "ndcg=%.4f, coverage=%.4f, diversity=%.4f}",
                k, precision, recall, f1Score, ndcg, coverage, diversity
        );
    }
}
