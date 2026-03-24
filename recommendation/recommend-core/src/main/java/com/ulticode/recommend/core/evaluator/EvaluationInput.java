package com.ulticode.recommend.core.evaluator;

import com.ulticode.recommend.core.model.RecommendItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Input data container for offline evaluation.
 * <p>
 * Contains the recommended items, ground truth (relevant items),
 * and configuration needed for metrics calculation.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationInput {

    private List<Long> recommended;
    private Set<Long> relevant;
    private int k;
    private List<RecommendItem> items;
    private int catalogSize;
}
