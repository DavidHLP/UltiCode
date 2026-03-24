package com.ulticode.recommend.api.dto;

import com.ulticode.recommend.api.enums.RecommendScenario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Result DTO containing recommendation items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * List of recommended items.
     */
    private List<RecommendItem> items;

    /**
     * Total count of available recommendations (may be more than items.size()).
     */
    private int totalCount;

    /**
     * The scenario used for generating recommendations.
     */
    private RecommendScenario scenario;

    /**
     * Timestamp when recommendations were generated.
     */
    private LocalDateTime generatedAt;
}
