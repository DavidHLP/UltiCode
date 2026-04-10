// console/src/stores/recommendation.ts
import { defineStore } from "pinia";
import { recommendationApi } from "@/api/recommendation";
import type { RecommendItem } from "@/types/recommendation";

/**
 * In-memory TTL cache. Persists across route changes within the session.
 */
const cache = new Map<string, { data: RecommendItem[]; timestamp: number }>();

const TTL_DAILY = 30 * 60 * 1000; // 30 min
const TTL_WEAK = 10 * 60 * 1000; // 10 min
const TTL_CHALLENGE = 15 * 60 * 1000; // 15 min
const TTL_SIMILAR = 0; // No cache — per-problem

function getCached(key: string, ttl: number): RecommendItem[] | null {
  const entry = cache.get(key);
  if (!entry) return null;
  if (ttl > 0 && Date.now() - entry.timestamp < ttl) {
    return entry.data;
  }
  return null; // expired or no TTL
}

function setCache(key: string, data: RecommendItem[]): void {
  cache.set(key, { data, timestamp: Date.now() });
}

/**
 * Invalidate all recommendation caches.
 * Call after user solves a problem or changes preferences.
 */
export function invalidateRecommendationCache(): void {
  cache.clear();
}

export const useRecommendationStore = defineStore("recommendation", {
  state: () => ({
    daily: [] as RecommendItem[],
    weakPoints: [] as RecommendItem[],
    challenge: [] as RecommendItem[],
    similar: [] as RecommendItem[],
    loading: false,
    error: null as string | null,
  }),

  actions: {
    _handleError(e: unknown, defaultMessage: string) {
      if (e instanceof Error) {
        this.error = e.message;
      } else if (typeof e === "string") {
        this.error = e;
      } else {
        this.error = defaultMessage;
      }
      console.error("Recommendation store error:", e);
    },

    async loadDaily(size = 10, includeSolved = false) {
      // Check cache first
      const cached = getCached("daily", TTL_DAILY);
      if (cached) {
        this.daily = cached;
        return;
      }

      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getDaily(size, includeSolved);
        this.daily = result?.items || [];
        setCache("daily", this.daily);
      } catch (e) {
        this._handleError(e, "Failed to load daily recommendations");
        this.daily = [];
      } finally {
        this.loading = false;
      }
    },

    async loadWeakPoints(size = 10, tags?: string[]) {
      // Cache key includes selected tags for accurate invalidation
      const cacheKey = `weak-points:${tags?.join(",") ?? ""}`;
      const cached = getCached(cacheKey, TTL_WEAK);
      if (cached) {
        this.weakPoints = cached;
        return;
      }

      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getWeakPoints(size, tags);
        this.weakPoints = result?.items || [];
        setCache(cacheKey, this.weakPoints);
      } catch (e) {
        this._handleError(e, "Failed to load weak point recommendations");
        this.weakPoints = [];
      } finally {
        this.loading = false;
      }
    },

    async loadChallenge(size = 5) {
      const cached = getCached("challenge", TTL_CHALLENGE);
      if (cached) {
        this.challenge = cached;
        return;
      }

      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getChallenge(size);
        this.challenge = result?.items || [];
        setCache("challenge", this.challenge);
      } catch (e) {
        this._handleError(e, "Failed to load challenge recommendations");
        this.challenge = [];
      } finally {
        this.loading = false;
      }
    },

    async loadSimilar(problemId: number, size = 5) {
      // No TTL cache for similar — always fetch fresh
      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getSimilar(problemId, size);
        this.similar = result?.items || [];
      } catch (e) {
        this._handleError(e, "Failed to load similar problems");
        this.similar = [];
      } finally {
        this.loading = false;
      }
    },

    clearError() {
      this.error = null;
    },
  },
});
