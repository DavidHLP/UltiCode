import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { createValueRequest } from "@ulticode/request-state";
import type { ContestListItem } from "@/types/contest";
import {
  fetchUpcomingContests,
  fetchRunningContests,
  fetchPastContests,
} from "@/api/contest";

/**
 * Contest browse store — list views and pagination.
 *
 * Owns:
 *   - upcoming / running / past contest list state
 *   - pastContests pagination total
 *   - error message for ongoing list-level operations
 *
 * Sibling stores: see ./contestDetail.ts (single-contest) and
 * ./contestRanking.ts (rankings + user contests).
 */
export const useContestBrowseStore = defineStore("contestBrowse", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  const upcomingContests = ref<ContestListItem[]>([]);
  const runningContests = ref<ContestListItem[]>([]);
  const pastContests = ref<ContestListItem[]>([]);
  const pastContestsTotal = ref(0);

  const contestsRequest = createValueRequest({
    errorMessage: "Failed to load contests",
    rethrow: true,
  });
  const pastContestsRequest = createValueRequest({
    errorMessage: "Failed to load contests",
    rethrow: true,
  });
  const loadingContests = computed(
    () => contestsRequest.loading.value || pastContestsRequest.loading.value,
  );
  const error = ref<string | null>(null);

  // =========================================================================
  // ACTIONS
  // =========================================================================

  async function loadContests() {
    error.value = null;
    contestsRequest.error.value = null;
    pastContestsRequest.error.value = null;
    try {
      const lists = await contestsRequest.run(() => Promise.all([
        fetchUpcomingContests(),
        fetchRunningContests(),
      ]));
      if (!lists) return;
      const [upcoming, running] = lists;
      upcomingContests.value = upcoming.items;
      runningContests.value = running.items;
    } catch (err) {
      error.value = contestsRequest.error.value;
      throw err;
    }
  }

  async function loadPastContests(page: number = 1, pageSize: number = 10) {
    error.value = null;
    contestsRequest.error.value = null;
    pastContestsRequest.error.value = null;
    try {
      const result = await pastContestsRequest.run(() => fetchPastContests(page, pageSize));
      if (!result) return;
      pastContests.value = result.items;
      pastContestsTotal.value = result.total;
    } catch (err) {
      error.value = pastContestsRequest.error.value;
      throw err;
    }
  }

  function clearError() {
    error.value = null;
    contestsRequest.error.value = null;
    pastContestsRequest.error.value = null;
  }

  return {
    // State
    upcomingContests,
    runningContests,
    pastContests,
    pastContestsTotal,
    loadingContests,
    error,

    // Actions
    loadContests,
    loadPastContests,
    clearError,
  };
});
