// console/src/stores/contest/rankingStore.ts
import { defineStore } from "pinia";
import { createValueRequest } from "@ulticode/request-state";
import { ref, computed } from "vue";
import type { RankingEntry } from "@/types/contest";
import { getRanking } from "@/api/contest";

export const useRankingStore = defineStore("ranking", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  /** Ranking entries for the current contest */
  const rankings = ref<RankingEntry[]>([]);

  /** Loading state */
  const rankingRequest = createValueRequest({
    errorMessage: "Failed to load ranking",
    rethrow: true,
  });
  const loading = rankingRequest.loading;

  /** Error message */
  const error = rankingRequest.error;

  /** Whether the ranking is frozen (during final minutes of contest) */
  const frozen = ref(false);

  // =========================================================================
  // GETTERS
  // =========================================================================

  /** Get top 10 ranking entries */
  const top10 = computed(() => {
    return rankings.value.slice(0, 10);
  });

  /** Check if the ranking display is frozen */
  const isFrozen = computed(() => {
    return frozen.value;
  });

  // =========================================================================
  // ACTIONS
  // =========================================================================

  /**
   * Fetch ranking for a contest
   */
  async function fetchRanking(
    slug: string,
    options?: { page?: number; limit?: number; includeVirtual?: boolean },
  ): Promise<void> {
    try {
      const result = await rankingRequest.run(() => getRanking(slug, options));
      if (result) rankings.value = result.items;
    } catch (err) {
      throw err;
    }
  }

  /**
   * Clear ranking data
   */
  function clearRanking(): void {
    rankingRequest.cancel();
    rankingRequest.error.value = null;
    rankings.value = [];
    frozen.value = false;
  }

  /**
   * Set frozen state (typically called from WebSocket events)
   */
  function setFrozen(value: boolean): void {
    frozen.value = value;
  }

  /**
   * Update rankings from real-time data (WebSocket)
   */
  function updateRankings(newRankings: RankingEntry[]): void {
    rankings.value = newRankings;
  }

  /**
   * Clear error state
   */
  function clearError(): void {
    rankingRequest.error.value = null;
  }

  /**
   * Reset store to initial state
   */
  function $reset(): void {
    rankingRequest.cancel();
    rankingRequest.error.value = null;
    rankings.value = [];
    frozen.value = false;
  }

  return {
    // State
    rankings,
    loading,
    error,
    frozen,

    // Getters
    top10,
    isFrozen,

    // Actions
    fetchRanking,
    clearRanking,
    setFrozen,
    updateRankings,
    clearError,
    $reset,
  };
});
