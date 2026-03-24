package com.ulticode.recommend.core.evaluator;

import com.ulticode.recommend.core.model.RecommendItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates recommendation quality using offline metrics.
 * <p>
 * Calculates various metrics including Precision@K, Recall@K, F1-Score,
 * NDCG@K, Coverage, and Diversity.
 * </p>
 */
public class OfflineEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OfflineEvaluator.class);

    /**
     * Evaluates a single recommendation result and returns all metrics.
     *
     * @param recommended the list of recommended item IDs in order
     * @param relevant    the set of relevant (ground truth) item IDs
     * @param k           the number of top recommendations to evaluate
     * @param items       the list of recommended items with tags (for diversity)
     * @param catalogSize the total number of items in the catalog (for coverage)
     * @return OfflineMetrics containing all calculated metrics
     */
    public OfflineMetrics evaluate(
            List<Long> recommended,
            Set<Long> relevant,
            int k,
            List<RecommendItem> items,
            int catalogSize) {

        if (recommended == null || recommended.isEmpty()) {
            return OfflineMetrics.empty();
        }

        if (relevant == null) {
            relevant = Collections.emptySet();
        }

        double precision = calculatePrecisionAtK(recommended, relevant, k);
        double recall = calculateRecallAtK(recommended, relevant, k);
        double f1Score = calculateF1Score(precision, recall);
        double ndcg = calculateNDCGAtK(recommended, relevant, k);

        Set<Long> uniqueRecommended = new HashSet<>(recommended);
        double coverage = calculateCoverage(uniqueRecommended, catalogSize);

        double diversity = 0.0;
        if (items != null && !items.isEmpty()) {
            diversity = calculateDiversity(items);
        }

        return OfflineMetrics.builder()
                .k(k)
                .precision(precision)
                .recall(recall)
                .f1Score(f1Score)
                .ndcg(ndcg)
                .coverage(coverage)
                .diversity(diversity)
                .build();
    }

    /**
     * Evaluates multiple recommendation results and aggregates the metrics.
     *
     * @param inputs the list of evaluation inputs
     * @return aggregated OfflineMetrics
     */
    public OfflineMetrics evaluateAggregate(List<EvaluationInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return OfflineMetrics.empty();
        }

        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalF1Score = 0.0;
        double totalNdcg = 0.0;
        double totalDiversity = 0.0;
        int count = 0;

        Set<Long> allUniqueRecommended = new HashSet<>();
        int maxCatalogSize = 0;

        for (EvaluationInput input : inputs) {
            if (input.getRecommended() == null || input.getRecommended().isEmpty()) {
                continue;
            }

            OfflineMetrics metrics = evaluate(
                    input.getRecommended(),
                    input.getRelevant(),
                    input.getK(),
                    input.getItems(),
                    input.getCatalogSize()
            );

            totalPrecision += metrics.getPrecision();
            totalRecall += metrics.getRecall();
            totalF1Score += metrics.getF1Score();
            totalNdcg += metrics.getNdcg();
            totalDiversity += metrics.getDiversity();
            count++;

            allUniqueRecommended.addAll(input.getRecommended());
            if (input.getCatalogSize() > maxCatalogSize) {
                maxCatalogSize = input.getCatalogSize();
            }
        }

        if (count == 0) {
            return OfflineMetrics.empty();
        }

        double avgCoverage = calculateCoverage(allUniqueRecommended, maxCatalogSize);

        return OfflineMetrics.builder()
                .k(inputs.get(0).getK())
                .precision(totalPrecision / count)
                .recall(totalRecall / count)
                .f1Score(totalF1Score / count)
                .ndcg(totalNdcg / count)
                .coverage(avgCoverage)
                .diversity(totalDiversity / count)
                .build();
    }

    /**
     * Calculates Precision@K - proportion of recommended items that are relevant.
     *
     * @param recommended the list of recommended item IDs
     * @param relevant    the set of relevant item IDs
     * @param k           the number of top items to consider
     * @return precision value between 0 and 1
     */
    public double calculatePrecisionAtK(List<Long> recommended, Set<Long> relevant, int k) {
        if (recommended == null || recommended.isEmpty() || relevant == null || relevant.isEmpty()) {
            return 0.0;
        }

        int effectiveK = Math.min(k, recommended.size());
        int relevantCount = 0;

        for (int i = 0; i < effectiveK; i++) {
            if (relevant.contains(recommended.get(i))) {
                relevantCount++;
            }
        }

        return (double) relevantCount / effectiveK;
    }

    /**
     * Calculates Recall@K - proportion of relevant items that are recommended.
     *
     * @param recommended the list of recommended item IDs
     * @param relevant    the set of relevant item IDs
     * @param k           the number of top items to consider
     * @return recall value between 0 and 1
     */
    public double calculateRecallAtK(List<Long> recommended, Set<Long> relevant, int k) {
        if (recommended == null || recommended.isEmpty() || relevant == null || relevant.isEmpty()) {
            return 0.0;
        }

        int effectiveK = Math.min(k, recommended.size());
        int relevantCount = 0;

        for (int i = 0; i < effectiveK; i++) {
            if (relevant.contains(recommended.get(i))) {
                relevantCount++;
            }
        }

        return (double) relevantCount / relevant.size();
    }

    /**
     * Calculates F1-Score - harmonic mean of precision and recall.
     *
     * @param precision the precision value
     * @param recall    the recall value
     * @return F1 score between 0 and 1
     */
    public double calculateF1Score(double precision, double recall) {
        if (precision + recall == 0) {
            return 0.0;
        }
        return 2.0 * (precision * recall) / (precision + recall);
    }

    /**
     * Calculates NDCG@K - Normalized Discounted Cumulative Gain.
     * <p>
     * Measures ranking quality with position awareness.
     * </p>
     *
     * @param recommended the list of recommended item IDs in order
     * @param relevant    the set of relevant item IDs
     * @param k           the number of top items to consider
     * @return NDCG value between 0 and 1
     */
    public double calculateNDCGAtK(List<Long> recommended, Set<Long> relevant, int k) {
        if (recommended == null || recommended.isEmpty() || relevant == null || relevant.isEmpty()) {
            return 0.0;
        }

        int effectiveK = Math.min(k, recommended.size());

        // Calculate DCG
        double dcg = calculateDCG(recommended, relevant, effectiveK);

        // Calculate IDCG (ideal DCG with perfect ranking)
        double idcg = calculateIDCG(relevant.size(), effectiveK);

        if (idcg == 0) {
            return 0.0;
        }

        return dcg / idcg;
    }

    /**
     * Calculates DCG (Discounted Cumulative Gain).
     */
    private double calculateDCG(List<Long> recommended, Set<Long> relevant, int k) {
        double dcg = 0.0;

        for (int i = 0; i < k; i++) {
            long itemId = recommended.get(i);
            double relevance = relevant.contains(itemId) ? 1.0 : 0.0;
            // Using log2(position + 1) for discount
            double discount = Math.log(i + 2) / Math.log(2);
            dcg += relevance / discount;
        }

        return dcg;
    }

    /**
     * Calculates IDCG (Ideal DCG) - maximum possible DCG.
     */
    private double calculateIDCG(int relevantCount, int k) {
        int effectiveRelevant = Math.min(relevantCount, k);
        double idcg = 0.0;

        for (int i = 0; i < effectiveRelevant; i++) {
            double discount = Math.log(i + 2) / Math.log(2);
            idcg += 1.0 / discount;
        }

        return idcg;
    }

    /**
     * Calculates Coverage - proportion of catalog items that can be recommended.
     *
     * @param uniqueRecommended the set of unique recommended item IDs
     * @param catalogSize       the total number of items in the catalog
     * @return coverage value between 0 and 1
     */
    public double calculateCoverage(Set<Long> uniqueRecommended, int catalogSize) {
        if (uniqueRecommended == null || uniqueRecommended.isEmpty() || catalogSize <= 0) {
            return 0.0;
        }

        return (double) uniqueRecommended.size() / catalogSize;
    }

    /**
     * Calculates Diversity - average pairwise dissimilarity between recommended items.
     * <p>
     * Uses Jaccard distance on tags: 1 - |intersection| / |union|
     * </p>
     *
     * @param items the list of recommended items with tags
     * @return diversity value between 0 and 1
     */
    public double calculateDiversity(List<RecommendItem> items) {
        if (items == null || items.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        int pairCount = 0;

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                Set<String> tags1 = items.get(i).getTags();
                Set<String> tags2 = items.get(j).getTags();

                double distance = calculateJaccardDistance(tags1, tags2);
                totalDistance += distance;
                pairCount++;
            }
        }

        if (pairCount == 0) {
            return 0.0;
        }

        return totalDistance / pairCount;
    }

    /**
     * Calculates Jaccard distance between two sets of tags.
     * <p>
     * Jaccard distance = 1 - Jaccard similarity = 1 - |intersection| / |union|
     * </p>
     *
     * @param tags1 first set of tags
     * @param tags2 second set of tags
     * @return Jaccard distance between 0 and 1
     */
    private double calculateJaccardDistance(Set<String> tags1, Set<String> tags2) {
        // Handle null or empty sets
        if ((tags1 == null || tags1.isEmpty()) && (tags2 == null || tags2.isEmpty())) {
            return 0.0; // Both empty are considered identical
        }
        if (tags1 == null || tags1.isEmpty() || tags2 == null || tags2.isEmpty()) {
            return 1.0; // One empty and one not are completely different
        }

        Set<String> intersection = new HashSet<>(tags1);
        intersection.retainAll(tags2);

        Set<String> union = new HashSet<>(tags1);
        union.addAll(tags2);

        if (union.isEmpty()) {
            return 0.0;
        }

        double similarity = (double) intersection.size() / union.size();
        return 1.0 - similarity;
    }
}
