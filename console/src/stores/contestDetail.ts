import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type {
  ContestDetail,
  ContestProblemSummary,
  ParticipationStatus,
} from "@/types/contest";
import {
  fetchContestDetail,
  fetchParticipationStatus,
  registerForContest as apiRegister,
  unregisterFromContest as apiUnregister,
  getContestProblems,
} from "@/api/contest";
import { useContestBrowseStore } from "@/stores/contestBrowse";

/**
 * Contest detail store — single-contest view + registration lifecycle.
 *
 * Owns:
 *   - currentContest            (loaded by loadContestDetail)
 *   - contestProblems           (keyed by contestId; cached so the
 *                                problem page can read it without a
 *                                parallel fetch — see R10.6.1 in
 *                                views/contest/detailed/ContestDetailView.vue)
 *   - userParticipation         (keyed by contestId)
 *   - isRegistered              (computed over userParticipation)
 *   - loading                   (for detail-scoped operations)
 *   - error                     (last detail-scoped error)
 *
 * Cross-store writes: `registerForContest` / `unregisterFromContest`
 * bump `registeredCount` on the matching upcoming contest in the
 * browse store. This mirrors the original god-store's behaviour
 * exactly and keeps the card counts on ContestBrowseView / HomeView
 * in sync without a refetch.
 */
export const useContestDetailStore = defineStore("contestDetail", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  const currentContest = ref<ContestDetail | null>(null);

  // Contest problems keyed by contestId. Hoisted from the local ref that
  // used to live in ContestDetailView so the problem page can read it
  // without re-fetching or hand-wiring a parallel store. The problem page
  // uses this to compute prev/next within a contest and to guard "this
  // problem is part of the contest" without a new endpoint.
  const contestProblems = ref<Map<string, ContestProblemSummary[]>>(
    new Map(),
  );

  const userParticipation = ref<Map<string, ParticipationStatus>>(new Map());

  const loading = ref(false);
  const error = ref<string | null>(null);

  // =========================================================================
  // GETTERS
  // =========================================================================

  const isRegistered = computed(() => (contestId: string) => {
    const participation = userParticipation.value.get(contestId);
    return (
      participation?.status === "REGISTERED" ||
      participation?.status === "STARTED"
    );
  });

  // =========================================================================
  // ACTIONS — CONTEST DETAIL
  // =========================================================================

  async function loadContestDetail(contestId: string) {
    loading.value = true;
    error.value = null;
    try {
      currentContest.value = await fetchContestDetail(contestId);
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load contest details";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Load (or return cached) problem list for a contest, keyed by `contestId`.
   * The result is stored in `contestProblems` so the problem page can
   * compute contest-scoped prev/next and the "is this problem in this
   * contest" guard without a new API endpoint.
   *
   * The key used is the same DB-id form that the rest of the store uses
   * (`userParticipation` keyed by `contestId`). Callers should pass the
   * DB id, not the URL slug — see `R10.6.1` notes in ContestDetailView.
   */
  async function loadProblems(contestId: string): Promise<ContestProblemSummary[]> {
    const cached = contestProblems.value.get(contestId);
    if (cached) return cached;
    const list = await getContestProblems(contestId);
    contestProblems.value.set(contestId, list);
    return list;
  }

  // =========================================================================
  // ACTIONS — PARTICIPATION
  // =========================================================================

  async function registerForContest(contestId: string) {
    error.value = null;
    try {
      await apiRegister(contestId);
      const status = await fetchParticipationStatus(contestId);
      userParticipation.value.set(contestId, status);

      const browse = useContestBrowseStore();
      browse.upcomingContests = browse.upcomingContests.map((c) =>
        c.id === contestId
          ? { ...c, registeredCount: (c.registeredCount || 0) + 1 }
          : c,
      );
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to register for contest";
      throw err;
    }
  }

  async function unregisterFromContest(contestId: string) {
    error.value = null;
    try {
      await apiUnregister(contestId);
      const status = await fetchParticipationStatus(contestId);
      userParticipation.value.set(contestId, status);

      const browse = useContestBrowseStore();
      browse.upcomingContests = browse.upcomingContests.map((c) =>
        c.id === contestId
          ? { ...c, registeredCount: Math.max(0, (c.registeredCount || 0) - 1) }
          : c,
      );
    } catch (err) {
      error.value =
        err instanceof Error
          ? err.message
          : "Failed to unregister from contest";
      throw err;
    }
  }

  async function loadParticipationStatus(contestId: string) {
    try {
      const status = await fetchParticipationStatus(contestId);
      userParticipation.value.set(contestId, status);
    } catch {
      userParticipation.value.set(contestId, {
        contestId,
        title: "",
        status: "",
        registeredAt: "",
        startedAt: "",
        completedAt: "",
        startTime: "",
        endTime: "",
        ranking: 0,
        score: 0,
        problemsSolved: 0,
        totalProblems: 0,
        hasStarted: false,
        isActive: false,
        isCompleted: false,
        canParticipate: false,
      });
    }
  }

  function clearError() {
    error.value = null;
  }

  return {
    // State
    currentContest,
    contestProblems,
    userParticipation,
    loading,
    error,

    // Getters
    isRegistered,

    // Actions
    loadContestDetail,
    loadProblems,
    registerForContest,
    unregisterFromContest,
    loadParticipationStatus,
    clearError,
  };
});
