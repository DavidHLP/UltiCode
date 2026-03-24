package com.ulticode.recommend.core;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main recommendation engine that orchestrates the recommendation pipeline.
 *
 * <p>Pipeline flow:
 * <pre>
 * User Request -> Feature Extraction -> Multi-path Recall -> Merge & Deduplicate
 *              -> Rank -> Re-rank -> Filter Solved -> Limit Size -> Return Results
 * </pre>
 *
 * <p>The engine coordinates:
 * <ul>
 *   <li><b>Recall Phase</b>: Multiple strategies generate candidate items (sorted by priority ascending)</li>
 *   <li><b>Rank Phase</b>: Single strategy scores and sorts candidates</li>
 *   <li><b>Re-rank Phase</b>: Multiple strategies adjust ranking (sorted by priority descending)</li>
 * </ul>
 */
public class RecommendEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendEngine.class);

    private final List<RecallStrategy> recallStrategies;
    private final RankStrategy rankStrategy;
    private final List<ReRankStrategy> reRankStrategies;

    /**
     * Creates a RecommendEngine with default strategies.
     *
     * <p>Default strategies:
     * <ul>
     *   <li>Recall: HotRecallStrategy, ContentRecallStrategy, CFRecallStrategy, ColdStartStrategy</li>
     *   <li>Rank: RuleRankStrategy</li>
     *   <li>Re-rank: DiversityReRankStrategy, FreshnessReRankStrategy</li>
     * </ul>
     */
    public RecommendEngine() {
        this.recallStrategies = createDefaultRecallStrategies();
        this.rankStrategy = createDefaultRankStrategy();
        this.reRankStrategies = createDefaultReRankStrategies();
    }

    /**
     * Creates a RecommendEngine with custom strategies.
     *
     * @param recallStrategies list of recall strategies (will be sorted by priority ascending)
     * @param rankStrategy the rank strategy
     * @param reRankStrategies list of re-rank strategies (will be sorted by priority descending)
     */
    public RecommendEngine(
            List<RecallStrategy> recallStrategies,
            RankStrategy rankStrategy,
            List<ReRankStrategy> reRankStrategies
    ) {
        this.recallStrategies = sortRecallStrategies(recallStrategies);
        this.rankStrategy = rankStrategy;
        this.reRankStrategies = sortReRankStrategies(reRankStrategies);
    }

    /**
     * Executes the recommendation pipeline.
     *
     * @param context the recommendation context
     * @param profile the user profile
     * @return list of recommended items, limited to context.size
     */
    public List<RecommendItem> recommend(RecommendContext context, UserProfile profile) {
        log.info("Starting recommendation pipeline for user: {}",
                context != null ? context.getUserId() : "unknown");

        // Handle null context
        RecommendContext safeContext = context != null ? context : createDefaultContext();

        // Phase 1: Multi-path Recall
        List<RecommendItem> candidates = executeRecallPhase(safeContext, profile);
        log.debug("Recall phase completed: {} candidates", candidates.size());

        if (candidates.isEmpty()) {
            log.info("No candidates from recall phase, returning empty list");
            return List.of();
        }

        // Phase 2: Merge and Deduplicate
        candidates = deduplicateByProblemId(candidates);
        log.debug("After deduplication: {} candidates", candidates.size());

        // Phase 3: Rank
        List<RecommendItem> rankedItems = executeRankPhase(candidates, safeContext, profile);
        log.debug("Rank phase completed: {} items", rankedItems.size());

        // Phase 4: Re-rank
        List<RecommendItem> reRankedItems = executeReRankPhase(rankedItems, safeContext, profile);
        log.debug("Re-rank phase completed: {} items", reRankedItems.size());

        // Phase 5: Filter solved problems (unless includeSolved is true)
        List<RecommendItem> filtered = filterSolvedProblems(reRankedItems, safeContext, profile);
        log.debug("After filtering solved: {} items", filtered.size());

        // Phase 6: Limit to requested size
        int requestedSize = safeContext.getSize();
        List<RecommendItem> limited = limitSize(filtered, requestedSize);
        log.info("Pipeline completed: returning {} items", limited.size());

        return limited;
    }

    /**
     * Gets the recall strategies (sorted by priority ascending).
     *
     * @return list of recall strategies
     */
    public List<RecallStrategy> getRecallStrategies() {
        return new ArrayList<>(recallStrategies);
    }

    /**
     * Gets the rank strategy.
     *
     * @return the rank strategy
     */
    public RankStrategy getRankStrategy() {
        return rankStrategy;
    }

    /**
     * Gets the re-rank strategies (sorted by priority descending).
     *
     * @return list of re-rank strategies
     */
    public List<ReRankStrategy> getReRankStrategies() {
        return new ArrayList<>(reRankStrategies);
    }

    // ==================== Private Methods ====================

    /**
     * Executes all recall strategies and merges results.
     */
    private List<RecommendItem> executeRecallPhase(RecommendContext context, UserProfile profile) {
        List<RecommendItem> allCandidates = new ArrayList<>();

        for (RecallStrategy strategy : recallStrategies) {
            try {
                log.debug("Executing recall strategy: {} (priority: {})",
                        strategy.getName(), strategy.getPriority());
                List<RecommendItem> items = strategy.recall(context, profile);
                if (items != null && !items.isEmpty()) {
                    allCandidates.addAll(items);
                    log.debug("Strategy {} returned {} items", strategy.getName(), items.size());
                }
            } catch (Exception e) {
                log.warn("Recall strategy {} failed: {}", strategy.getName(), e.getMessage());
            }
        }

        return allCandidates;
    }

    /**
     * Removes duplicate items by problemId, keeping the first occurrence.
     */
    private List<RecommendItem> deduplicateByProblemId(List<RecommendItem> items) {
        Set<Long> seenIds = new HashSet<>();
        List<RecommendItem> deduplicated = new ArrayList<>();

        for (RecommendItem item : items) {
            if (item.getProblemId() != null && !seenIds.contains(item.getProblemId())) {
                seenIds.add(item.getProblemId());
                deduplicated.add(item);
            }
        }

        return deduplicated;
    }

    /**
     * Applies the rank strategy to sort items by score.
     */
    private List<RecommendItem> executeRankPhase(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    ) {
        if (rankStrategy == null) {
            log.warn("No rank strategy configured, returning items as-is");
            return items;
        }

        log.debug("Executing rank strategy: {}", rankStrategy.getName());
        List<RecommendItem> ranked = rankStrategy.rank(items, context, profile);
        return ranked != null ? ranked : List.of();
    }

    /**
     * Applies all re-rank strategies in priority order (higher first).
     */
    private List<RecommendItem> executeReRankPhase(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    ) {
        List<RecommendItem> current = items;

        for (ReRankStrategy strategy : reRankStrategies) {
            try {
                log.debug("Executing re-rank strategy: {} (priority: {})",
                        strategy.getName(), strategy.getPriority());
                List<RecommendItem> reranked = strategy.rerank(current, context, profile);
                current = reranked != null ? reranked : current;
            } catch (Exception e) {
                log.warn("Re-rank strategy {} failed: {}", strategy.getName(), e.getMessage());
            }
        }

        return current;
    }

    /**
     * Filters out problems the user has already solved.
     */
    private List<RecommendItem> filterSolvedProblems(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    ) {
        // If includeSolved is true, don't filter
        if (context.isIncludeSolved()) {
            return items;
        }

        Set<Long> solvedProblems = profile != null && profile.getSolvedProblems() != null
                ? profile.getSolvedProblems()
                : Set.of();

        if (solvedProblems.isEmpty()) {
            return items;
        }

        return items.stream()
                .filter(item -> !solvedProblems.contains(item.getProblemId()))
                .collect(Collectors.toList());
    }

    /**
     * Limits the result list to the requested size.
     */
    private List<RecommendItem> limitSize(List<RecommendItem> items, int size) {
        if (size <= 0 || items.size() <= size) {
            return items;
        }
        return items.subList(0, size);
    }

    /**
     * Sorts recall strategies by priority in ascending order (lower priority first).
     */
    private List<RecallStrategy> sortRecallStrategies(List<RecallStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            return List.of();
        }
        return strategies.stream()
                .sorted(Comparator.comparingInt(RecallStrategy::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Sorts re-rank strategies by priority in descending order (higher priority first).
     */
    private List<ReRankStrategy> sortReRankStrategies(List<ReRankStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            return List.of();
        }
        return strategies.stream()
                .sorted(Comparator.comparingInt(ReRankStrategy::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Creates a default context when null is provided.
     */
    private RecommendContext createDefaultContext() {
        return RecommendContext.builder()
                .size(10)
                .scenario(RecommendContext.Scenario.DAILY)
                .build();
    }

    // ==================== Default Strategy Factories ====================

    /**
     * Creates default recall strategies.
     *
     * <p>Note: These require data to be injected in production.
     * For testing purposes, they may return empty results without data.
     */
    private List<RecallStrategy> createDefaultRecallStrategies() {
        // Note: These strategies require data sources to be injected
        // In production, this would be done via dependency injection
        // For now, we create them with empty data sources
        List<RecommendItem> emptyProblems = List.of();
        Map<String, Set<Long>> emptyUserMatrix = Map.of();

        List<RecallStrategy> strategies = new ArrayList<>();

        strategies.add(new ColdStartStrategy(emptyProblems));
        strategies.add(new HotRecallStrategy(emptyProblems));
        strategies.add(new ContentRecallStrategy(emptyProblems));
        strategies.add(new CFRecallStrategy(emptyUserMatrix, emptyProblems));

        return sortRecallStrategies(strategies);
    }

    /**
     * Creates the default rank strategy.
     */
    private RankStrategy createDefaultRankStrategy() {
        return new RuleRankStrategy();
    }

    /**
     * Creates default re-rank strategies.
     */
    private List<ReRankStrategy> createDefaultReRankStrategies() {
        List<ReRankStrategy> strategies = new ArrayList<>();
        strategies.add(new DiversityReRankStrategy());
        strategies.add(new FreshnessReRankStrategy());
        return sortReRankStrategies(strategies);
    }
}
