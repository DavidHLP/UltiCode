// console/src/stores/recommendation.ts
import { defineStore } from "pinia";
import { recommendationApi } from "@/api/recommendation";
import type { RecommendItem } from "@/types/recommendation";

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
      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getDaily(size, includeSolved);
        this.daily = result?.items || [];
      } catch (e) {
        this._handleError(e, "Failed to load daily recommendations");
        this.daily = [];
      } finally {
        this.loading = false;
      }
    },

    async loadWeakPoints(size = 10, tags?: string[]) {
      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getWeakPoints(size, tags);
        this.weakPoints = result?.items || [];
      } catch (e) {
        this._handleError(e, "Failed to load weak point recommendations");
        this.weakPoints = [];
      } finally {
        this.loading = false;
      }
    },

    async loadChallenge(size = 5) {
      this.loading = true;
      this.error = null;
      try {
        const result = await recommendationApi.getChallenge(size);
        this.challenge = result?.items || [];
      } catch (e) {
        this._handleError(e, "Failed to load challenge recommendations");
        this.challenge = [];
      } finally {
        this.loading = false;
      }
    },

    async loadSimilar(problemId: number, size = 5) {
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
