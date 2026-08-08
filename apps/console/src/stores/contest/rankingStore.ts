// console/src/stores/contest/rankingStore.ts
import { defineStore } from "pinia";
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
  const loading = ref(false);

  /** Error message */
  const error = ref<string | null>(null);

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
    loading.value = true;
    error.value = null;
    try {
      const result = await getRanking(slug, options);
      rankings.value = result.items;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load ranking";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Clear ranking data
   */
  function clearRanking(): void {
    rankings.value = [];
    frozen.value = false;
    error.value = null;
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
    error.value = null;
  }

  /**
   * Reset store to initial state
   */
  function $reset(): void {
    rankings.value = [];
    loading.value = false;
    error.value = null;
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
