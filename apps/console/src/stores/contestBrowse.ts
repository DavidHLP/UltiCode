import { defineStore } from "pinia";
import { ref } from "vue";
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
 *   - loading flag for list-level operations
 *   - error message for the last browse operation
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

  const loadingContests = ref(false);
  const error = ref<string | null>(null);

  // =========================================================================
  // ACTIONS
  // =========================================================================

  async function loadContests() {
    loadingContests.value = true;
    error.value = null;
    try {
      const [upcoming, running] = await Promise.all([
        fetchUpcomingContests(),
        fetchRunningContests(),
      ]);
      upcomingContests.value = upcoming.items;
      runningContests.value = running.items;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load contests";
      throw err;
    } finally {
      loadingContests.value = false;
    }
  }

  async function loadPastContests(page: number = 1, pageSize: number = 10) {
    loadingContests.value = true;
    error.value = null;
    try {
      const result = await fetchPastContests(page, pageSize);
      pastContests.value = result.items;
      pastContestsTotal.value = result.total;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load past contests";
      throw err;
    } finally {
      loadingContests.value = false;
    }
  }

  function clearError() {
    error.value = null;
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
