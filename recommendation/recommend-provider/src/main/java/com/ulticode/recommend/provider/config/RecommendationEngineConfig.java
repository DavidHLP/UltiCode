package com.ulticode.recommend.provider.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ulticode.recommend.core.RecommendEngine;
import com.ulticode.recommend.core.rank.RankStrategy;
import com.ulticode.recommend.core.rank.RuleRankStrategy;
import com.ulticode.recommend.core.recall.CFRecallStrategy;
import com.ulticode.recommend.core.recall.ColdStartStrategy;
import com.ulticode.recommend.core.recall.ContentRecallStrategy;
import com.ulticode.recommend.core.recall.HotRecallStrategy;
import com.ulticode.recommend.core.recall.RecallStrategy;
import com.ulticode.recommend.core.rerank.DiversityReRankStrategy;
import com.ulticode.recommend.core.rerank.FreshnessReRankStrategy;
import com.ulticode.recommend.core.rerank.ReRankStrategy;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.provider.store.RedisRecommendationStore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the recommendation engine and its strategies.
 *
 * <p>Wires all strategy beans via constructor injection, keeping recommend-core
 * as a framework-agnostic library. Recall strategies load data from Redis
 * (populated by Spark offline jobs), falling back to empty data sources
 * when Redis has no pre-computed data.
 */
@Configuration
public class RecommendationEngineConfig {

    // ==================== Engine ====================

    @Bean
    public RecommendEngine recommendEngine(
            List<RecallStrategy> recallStrategies,
            RankStrategy rankStrategy,
            List<ReRankStrategy> reRankStrategies) {
        return new RecommendEngine(recallStrategies, rankStrategy, reRankStrategies);
    }

    // ==================== Rank Strategy ====================

    @Bean
    public RankStrategy rankStrategy() {
        return new RuleRankStrategy();
    }

    // ==================== Re-rank Strategies ====================

    @Bean
    public ReRankStrategy diversityReRankStrategy() {
        return new DiversityReRankStrategy();
    }

    @Bean
    public ReRankStrategy freshnessReRankStrategy() {
        return new FreshnessReRankStrategy();
    }

    // ==================== Recall Strategies ====================
    // Data loaded from Redis (populated by Spark offline jobs).
    // Falls back to empty data when Redis has no pre-computed data.

    @Bean
    public RecallStrategy coldStartStrategy(RedisRecommendationStore store) {
        return new ColdStartStrategy(store.loadAvailableProblems());
    }

    @Bean
    public RecallStrategy hotRecallStrategy(RedisRecommendationStore store) {
        return new HotRecallStrategy(store.loadAvailableProblems());
    }

    @Bean
    public RecallStrategy contentRecallStrategy(RedisRecommendationStore store) {
        return new ContentRecallStrategy(store.loadAvailableProblems());
    }

    @Bean
    public RecallStrategy cfRecallStrategy(RedisRecommendationStore store) {
        return new CFRecallStrategy(store.loadUserProblemMatrix(), store.loadAvailableProblems());
    }
}
