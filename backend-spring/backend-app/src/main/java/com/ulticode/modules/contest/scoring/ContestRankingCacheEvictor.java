package com.ulticode.modules.contest.scoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Single owner of {@code contestRanking} cache invalidation.
 *
 * <p>Shared by the adjudication module (a verdict changes aggregates and so
 * the ranking) and the lifecycle module (a cascade delete changes the rows a
 * ranking is built from). Centralizing the eviction keeps the cache-shape
 * knowledge — key templates, the {@code clear()} vs per-key trade-off, and
 * the per-contest eviction roadmap — in one place instead of leaking it
 * across every writer.
 *
 * <p>The cache key is NOT contest-keyed today
 * ({@code 'getGlobalRanking:{limit}'} / {@code 'globalPaginated:{page}:{limit}'}),
 * so {@link Cache#clear()} is the only safe option: partial eviction would
 * leave stale entries. The cost is acceptable at the current scale because
 * the ranking cache TTL is short. Per-contest eviction needs a key-template
 * change first (R9.1 / ADR-007 §8); until then this global clear is the
 * correct, NFR-P1-safe fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestRankingCacheEvictor {

    /** Must match {@code @Cacheable("contestRanking")} in ContestServiceImpl. */
    private static final String RANKING_CACHE = "contestRanking";

    private final CacheManager cacheManager;

    /**
     * Clear the whole {@code contestRanking} cache. Safe to call when the
     * cache is absent or the manager throws (degrades to a no-op).
     */
    public void evictRankingCache() {
        Cache cache;
        try {
            cache = cacheManager.getCache(RANKING_CACHE);
        } catch (Exception e) {
            log.debug("Ranking cache not available: {}", e.getMessage());
            return;
        }
        if (cache != null) {
            cache.clear();
        }
    }
}
