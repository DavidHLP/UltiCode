import { defineStore } from "pinia";
import { ref } from "vue";
import type { ContestListItem, GlobalRankingEntry, UserContestHistory } from "@/types/contest";
import {
  fetchGlobalRankings,
  fetchUserContestHistory,
  fetchUserContests as apiFetchUserContests,
} from "@/api/contest";

/**
 * Contest ranking store — global rankings + per-user contest lists.
 *
 * Owns:
 *   - globalRankings          (REST snapshot of `/contest/rankings/global`)
 *   - loadingRankings         (flag for global-rankings load)
 *   - registeredContests      (user's "registered" list — pre-contest)
 *   - participatedContests    (user's "participated" list — contest history)
 *   - virtualContests         (user's "virtual" list — virtual replays)
 *   - contestHistory          (user's contest history with rank/score)
 *   - error                   (last ranking/user-list error)
 *
 * The "user contests" trio (registered/participated/virtual) and the
 * dedicated `contestHistory` list both came from the original god
 * store's `loadUserContests` + `loadContestHistory` actions. They
 * share error/loading semantics, so they live together here.
 */
export const useContestRankingStore = defineStore("contestRanking", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  const globalRankings = ref<GlobalRankingEntry[]>([]);
  const loadingRankings = ref(false);

  const registeredContests = ref<ContestListItem[]>([]);
  const participatedContests = ref<ContestListItem[]>([]);
  const virtualContests = ref<ContestListItem[]>([]);
  const contestHistory = ref<UserContestHistory[]>([]);

  const error = ref<string | null>(null);

  // =========================================================================
  // ACTIONS — GLOBAL RANKINGS
  // =========================================================================

  async function loadGlobalRankings(options?: {
    page?: number;
    limit?: number;
    country?: string;
  }) {
    loadingRankings.value = true;
    error.value = null;
    try {
      const result = await fetchGlobalRankings({
        page: options?.page ?? 1,
        limit: options?.limit ?? 10,
        country: options?.country,
      });
      globalRankings.value = result.items;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load rankings";
      throw err;
    } finally {
      loadingRankings.value = false;
    }
  }

  // =========================================================================
  // ACTIONS — USER CONTESTS
  // =========================================================================

  async function loadUserContests(
    type?: "registered" | "participated" | "virtual",
  ) {
    error.value = null;
    try {
      if (type) {
        const result = await apiFetchUserContests(type);
        if (type === "registered") registeredContests.value = result;
        if (type === "participated") participatedContests.value = result;
        if (type === "virtual") virtualContests.value = result;
      } else {
        const [registered, participated, virtual] = await Promise.all([
          apiFetchUserContests("registered"),
          apiFetchUserContests("participated"),
          apiFetchUserContests("virtual"),
        ]);
        registeredContests.value = registered;
        participatedContests.value = participated;
        virtualContests.value = virtual;
      }
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load user contests";
      throw err;
    }
  }

  async function loadContestHistory() {
    error.value = null;
    try {
      contestHistory.value = await fetchUserContestHistory();
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load contest history";
      throw err;
    }
  }

  function clearError() {
    error.value = null;
  }

  return {
    // State
    globalRankings,
    loadingRankings,
    registeredContests,
    participatedContests,
    virtualContests,
    contestHistory,
    error,

    // Actions
    loadGlobalRankings,
    loadUserContests,
    loadContestHistory,
    clearError,
  };
});
