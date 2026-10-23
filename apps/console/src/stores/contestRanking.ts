import { defineStore } from "pinia";
import { createValueRequest } from "@ulticode/request-state";
import { computed, ref } from "vue";
import type {
  ContestListItem,
  GlobalRankingEntry,
  UserContestHistory,
} from "@/types/contest";
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
  const rankingsRequest = createValueRequest({
    errorMessage: "Failed to load rankings",
    rethrow: true,
  });
  const loadingRankings = rankingsRequest.loading;
  const moreRankingsRequest = createValueRequest({
    errorMessage: "Failed to load rankings",
    rethrow: true,
  });
  const loadingMoreRankings = moreRankingsRequest.loading;
  const loadMoreRankingsError = moreRankingsRequest.error;
  const hasMoreRankings = ref(false);
  let rankingPage = 1;
  let rankingQuery: { limit: number; country?: string } = { limit: 10 };

  const registeredContests = ref<ContestListItem[]>([]);
  const participatedContests = ref<ContestListItem[]>([]);
  const virtualContests = ref<ContestListItem[]>([]);
  const contestHistory = ref<UserContestHistory[]>([]);

  const operationError = ref<string | null>(null);
  const error = computed(
    () => operationError.value ?? rankingsRequest.error.value,
  );

  // =========================================================================
  // ACTIONS — GLOBAL RANKINGS
  // =========================================================================

  async function loadGlobalRankings(options?: {
    page?: number;
    limit?: number;
    country?: string;
  }) {
    operationError.value = null;
    moreRankingsRequest.cancel();
    moreRankingsRequest.error.value = null;
    hasMoreRankings.value = false;
    const query = { limit: options?.limit ?? 10, country: options?.country };
    const result = await rankingsRequest.run(() =>
      fetchGlobalRankings({
        ...query,
        page: options?.page ?? 1,
      }),
    );
    if (result) {
      globalRankings.value = result.items;
      rankingQuery = { ...query, limit: result.pageSize };
      rankingPage = result.page;
      hasMoreRankings.value =
        result.items.length > 0 && result.page < result.totalPages;
    }
  }

  async function loadMoreGlobalRankings() {
    if (
      loadingRankings.value ||
      loadingMoreRankings.value ||
      !hasMoreRankings.value
    )
      return;
    const result = await moreRankingsRequest.run(() =>
      fetchGlobalRankings({
        ...rankingQuery,
        page: rankingPage + 1,
      }),
    );
    if (result) {
      globalRankings.value = [...globalRankings.value, ...result.items];
      rankingPage = result.page;
      hasMoreRankings.value =
        result.items.length > 0 && result.page < result.totalPages;
    }
  }

  // =========================================================================
  // ACTIONS — USER CONTESTS
  // =========================================================================

  async function loadUserContests(
    type?: "registered" | "participated" | "virtual",
  ) {
    operationError.value = null;
    rankingsRequest.error.value = null;
    try {
      if (type) {
        const result = await apiFetchUserContests(type);
        if (type === "registered") registeredContests.value = result.items;
        if (type === "participated") participatedContests.value = result.items;
        if (type === "virtual") virtualContests.value = result.items;
      } else {
        const [registered, participated, virtual] = await Promise.all([
          apiFetchUserContests("registered"),
          apiFetchUserContests("participated"),
          apiFetchUserContests("virtual"),
        ]);
        registeredContests.value = registered.items;
        participatedContests.value = participated.items;
        virtualContests.value = virtual.items;
      }
    } catch (err) {
      operationError.value =
        err instanceof Error ? err.message : "Failed to load user contests";
      throw err;
    }
  }

  async function loadContestHistory() {
    operationError.value = null;
    rankingsRequest.error.value = null;
    try {
      contestHistory.value = await fetchUserContestHistory();
    } catch (err) {
      operationError.value =
        err instanceof Error ? err.message : "Failed to load contest history";
      throw err;
    }
  }

  function clearError() {
    operationError.value = null;
    rankingsRequest.error.value = null;
  }

  return {
    // State
    globalRankings,
    loadingRankings,
    loadingMoreRankings,
    loadMoreRankingsError,
    hasMoreRankings,
    registeredContests,
    participatedContests,
    virtualContests,
    contestHistory,
    error,

    // Actions
    loadGlobalRankings,
    loadMoreGlobalRankings,
    loadUserContests,
    loadContestHistory,
    clearError,
  };
});
