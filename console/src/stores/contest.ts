import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  ContestListItem,
  ContestDetail,
  ParticipationStatus,
  VirtualContestSession,
  GlobalRankingEntry,
  UserContestHistory,
  RatingHistoryEntry,
} from "@/types/contest";
import {
  fetchUpcomingContests,
  fetchRunningContests,
  fetchPastContests,
  fetchContestDetail,
  registerForContest as apiRegister,
  unregisterFromContest as apiUnregister,
  fetchParticipationStatus,
  startVirtualContest as apiStartVirtual,
  fetchVirtualSession,
  finishVirtualContest as apiFinishVirtual,
  fetchUserContests as apiFetchUserContests,
  fetchUserContestHistory,
  fetchUserRatingHistory,
  fetchGlobalRankings,
} from "@/api/contest";

export const useContestStore = defineStore("contest", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  const upcomingContests = ref<ContestListItem[]>([]);
  const runningContests = ref<ContestListItem[]>([]);
  const pastContests = ref<ContestListItem[]>([]);
  const pastContestsTotal = ref(0);

  const currentContest = ref<ContestDetail | null>(null);

  const userParticipation = ref<Map<string, ParticipationStatus>>(new Map());
  const virtualSession = ref<VirtualContestSession | null>(null);

  const registeredContests = ref<ContestListItem[]>([]);
  const participatedContests = ref<ContestListItem[]>([]);
  const virtualContests = ref<ContestListItem[]>([]);
  const contestHistory = ref<UserContestHistory[]>([]);
  const ratingHistory = ref<RatingHistoryEntry[]>([]);

  const globalRankings = ref<GlobalRankingEntry[]>([]);

  const loading = ref(false);
  const loadingContests = ref(false);
  const loadingRankings = ref(false);

  const error = ref<string | null>(null);

  const countdownTimers = ref<Map<string, number>>(new Map());

  // =========================================================================
  // GETTERS
  // =========================================================================

  const isRegistered = computed(() => (contestId: string) => {
    const participation = userParticipation.value.get(contestId);
    return participation?.status === "REGISTERED" || participation?.status === "STARTED";
  });

  const isInVirtualContest = computed(
    () => virtualSession.value?.status === "IN_PROGRESS",
  );

  const currentVirtualTimeRemaining = computed(() => {
    if (!virtualSession.value?.endsAt) return 0;
    const endsAt = new Date(virtualSession.value.endsAt).getTime();
    const now = Date.now();
    return Math.max(0, Math.floor((endsAt - now) / 1000));
  });

  // =========================================================================
  // ACTIONS — CONTEST LOADING
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

  async function loadGlobalRankings() {
    loadingRankings.value = true;
    error.value = null;
    try {
      const result = await fetchGlobalRankings({ page: 1, limit: 10 });
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
  // ACTIONS — PARTICIPATION
  // =========================================================================

  async function registerForContest(contestId: string) {
    error.value = null;
    try {
      await apiRegister(contestId);
      const status = await fetchParticipationStatus(contestId);
      userParticipation.value.set(contestId, status);

      upcomingContests.value = upcomingContests.value.map((c) =>
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

      upcomingContests.value = upcomingContests.value.map((c) =>
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

  // =========================================================================
  // ACTIONS — VIRTUAL CONTEST
  // =========================================================================

  async function startVirtualContest(contestId: string) {
    error.value = null;
    try {
      const session = await apiStartVirtual(contestId);
      virtualSession.value = session;
      return session;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to start virtual contest";
      throw err;
    }
  }

  async function loadVirtualSession(contestId: string) {
    try {
      virtualSession.value = await fetchVirtualSession(contestId);
    } catch {
      virtualSession.value = null;
    }
  }

  async function finishVirtualContest(contestId: string) {
    if (!virtualSession.value?.id) return;
    error.value = null;
    try {
      await apiFinishVirtual(contestId, virtualSession.value.id);
      virtualSession.value = null;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to finish virtual contest";
      throw err;
    }
  }

  // =========================================================================
  // ACTIONS — USER CONTESTS
  // =========================================================================

  async function loadUserContests() {
    error.value = null;
    try {
      const [registered, participated, virtual] = await Promise.all([
        apiFetchUserContests("registered"),
        apiFetchUserContests("participated"),
        apiFetchUserContests("virtual"),
      ]);
      registeredContests.value = registered;
      participatedContests.value = participated;
      virtualContests.value = virtual;
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

  async function loadRatingHistory() {
    error.value = null;
    try {
      ratingHistory.value = await fetchUserRatingHistory();
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load rating history";
      throw err;
    }
  }

  // =========================================================================
  // COUNTDOWN MANAGEMENT
  // =========================================================================

  const timerHandles = new Map<string, number>();

  function startCountdownTimer(contestId: string, endTime: Date) {
    const timerId = window.setInterval(() => {
      const now = Date.now();
      const remaining = Math.max(
        0,
        Math.floor((endTime.getTime() - now) / 1000),
      );
      countdownTimers.value.set(contestId, remaining);

      if (remaining <= 0) {
        stopCountdownTimer(contestId);
      }
    }, 1000);

    timerHandles.set(contestId, timerId);
    return timerId;
  }

  function stopCountdownTimer(contestId: string) {
    const handle = timerHandles.get(contestId);
    if (handle) {
      clearInterval(handle);
      timerHandles.delete(contestId);
    }
    countdownTimers.value.delete(contestId);
  }

  function getCountdown(contestId: string): number {
    return countdownTimers.value.get(contestId) ?? 0;
  }

  // =========================================================================
  // RESET
  // =========================================================================

  function clearError() {
    error.value = null;
  }

  function $reset() {
    upcomingContests.value = [];
    runningContests.value = [];
    pastContests.value = [];
    pastContestsTotal.value = 0;
    currentContest.value = null;
    userParticipation.value = new Map();
    virtualSession.value = null;
    registeredContests.value = [];
    participatedContests.value = [];
    virtualContests.value = [];
    contestHistory.value = [];
    ratingHistory.value = [];
    globalRankings.value = [];
    loading.value = false;
    loadingContests.value = false;
    loadingRankings.value = false;
    countdownTimers.value = new Map();
    error.value = null;
  }

  return {
    // State
    upcomingContests,
    runningContests,
    pastContests,
    pastContestsTotal,
    currentContest,
    userParticipation,
    virtualSession,
    registeredContests,
    participatedContests,
    virtualContests,
    contestHistory,
    ratingHistory,
    globalRankings,
    loading,
    loadingContests,
    loadingRankings,
    countdownTimers,
    error,

    // Getters
    isRegistered,
    isInVirtualContest,
    currentVirtualTimeRemaining,

    // Actions
    loadContests,
    loadPastContests,
    loadContestDetail,
    loadGlobalRankings,
    registerForContest,
    unregisterFromContest,
    loadParticipationStatus,
    startVirtualContest,
    loadVirtualSession,
    finishVirtualContest,
    loadUserContests,
    loadContestHistory,
    loadRatingHistory,
    startCountdownTimer,
    stopCountdownTimer,
    getCountdown,
    clearError,
    $reset,
  };
});