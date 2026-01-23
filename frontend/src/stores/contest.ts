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

  // Contest lists
  const upcomingContests = ref<ContestListItem[]>([]);
  const runningContests = ref<ContestListItem[]>([]);
  const pastContests = ref<ContestListItem[]>([]);
  const pastContestsTotal = ref(0);

  // Current contest
  const currentContest = ref<ContestDetail | null>(null);

  // User participation
  const userParticipation = ref<Map<string, ParticipationStatus>>(new Map());
  const virtualSession = ref<VirtualContestSession | null>(null);

  // User contests
  const registeredContests = ref<ContestListItem[]>([]);
  const participatedContests = ref<ContestListItem[]>([]);
  const virtualContests = ref<ContestListItem[]>([]);
  const contestHistory = ref<UserContestHistory[]>([]);
  const ratingHistory = ref<RatingHistoryEntry[]>([]);

  // Global rankings
  const globalRankings = ref<GlobalRankingEntry[]>([]);

  // Loading states
  const loading = ref(false);
  const loadingContests = ref(false);
  const loadingRankings = ref(false);

  // Countdown timers
  const countdownTimers = ref<Map<string, number>>(new Map());

  // =========================================================================
  // GETTERS
  // =========================================================================

  const isRegistered = computed(() => (contestId: string) => {
    const participation = userParticipation.value.get(contestId);
    return participation?.isRegistered ?? false;
  });

  const isInVirtualContest = computed(
    () => virtualSession.value?.status === "IN_PROGRESS",
  );

  const currentVirtualTimeRemaining = computed(() => {
    if (!virtualSession.value?.ends_at) return 0;
    const endsAt = new Date(virtualSession.value.ends_at).getTime();
    const now = Date.now();
    return Math.max(0, Math.floor((endsAt - now) / 1000));
  });

  // =========================================================================
  // ACTIONS - CONTEST LOADING
  // =========================================================================

  async function loadContests() {
    loadingContests.value = true;
    try {
      const [upcoming, running] = await Promise.all([
        fetchUpcomingContests(),
        fetchRunningContests(),
      ]);
      upcomingContests.value = upcoming;
      runningContests.value = running;
    } finally {
      loadingContests.value = false;
    }
  }

  async function loadPastContests(page: number = 1, limit: number = 10) {
    loadingContests.value = true;
    try {
      const result = await fetchPastContests(page, limit);
      pastContests.value = result.data;
      pastContestsTotal.value = result.total;
    } finally {
      loadingContests.value = false;
    }
  }

  async function loadContestDetail(contestId: string) {
    loading.value = true;
    try {
      currentContest.value = await fetchContestDetail(contestId);
    } finally {
      loading.value = false;
    }
  }

  async function loadGlobalRankings() {
    loadingRankings.value = true;
    try {
      const result = await fetchGlobalRankings({ page: 1, limit: 10 });
      globalRankings.value = result.items;
    } finally {
      loadingRankings.value = false;
    }
  }

  // =========================================================================
  // ACTIONS - PARTICIPATION
  // =========================================================================

  async function registerForContest(contestId: string) {
    await apiRegister(contestId);
    // Refresh participation status
    const status = await fetchParticipationStatus(contestId);
    userParticipation.value.set(contestId, status);

    // Update contest registered count in lists
    const updateCount = (list: ContestListItem[]) => {
      const contest = list.find((c) => c.id === contestId);
      if (contest) {
        contest.registered_count = (contest.registered_count || 0) + 1;
      }
    };
    updateCount(upcomingContests.value);
  }

  async function unregisterFromContest(contestId: string) {
    await apiUnregister(contestId);
    // Refresh participation status
    const status = await fetchParticipationStatus(contestId);
    userParticipation.value.set(contestId, status);

    // Update contest registered count in lists
    const updateCount = (list: ContestListItem[]) => {
      const contest = list.find((c) => c.id === contestId);
      if (contest) {
        contest.registered_count = Math.max(
          0,
          (contest.registered_count || 0) - 1,
        );
      }
    };
    updateCount(upcomingContests.value);
  }

  async function loadParticipationStatus(contestId: string) {
    try {
      const status = await fetchParticipationStatus(contestId);
      userParticipation.value.set(contestId, status);
    } catch {
      // User not logged in or other error
      userParticipation.value.set(contestId, {
        isRegistered: false,
        status: null,
        participantId: null,
        virtualSessionId: null,
        startedAt: null,
        finishedAt: null,
        totalScore: 0,
        totalPenalty: 0,
      });
    }
  }

  // =========================================================================
  // ACTIONS - VIRTUAL CONTEST
  // =========================================================================

  async function startVirtualContest(contestId: string) {
    const session = await apiStartVirtual(contestId);
    virtualSession.value = session;
    return session;
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
    await apiFinishVirtual(contestId, virtualSession.value.id);
    virtualSession.value = null;
  }

  // =========================================================================
  // ACTIONS - USER CONTESTS
  // =========================================================================

  async function loadUserContests() {
    const [registered, participated, virtual] = await Promise.all([
      apiFetchUserContests("registered"),
      apiFetchUserContests("participated"),
      apiFetchUserContests("virtual"),
    ]);
    registeredContests.value = registered;
    participatedContests.value = participated;
    virtualContests.value = virtual;
  }

  async function loadContestHistory() {
    contestHistory.value = await fetchUserContestHistory();
  }

  async function loadRatingHistory() {
    ratingHistory.value = await fetchUserRatingHistory();
  }

  // =========================================================================
  // COUNTDOWN MANAGEMENT
  // =========================================================================

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

    // Store timer ID for cleanup
    countdownTimers.value.set(contestId, 0);
    return timerId;
  }

  function stopCountdownTimer(contestId: string) {
    countdownTimers.value.delete(contestId);
  }

  function getCountdown(contestId: string): number {
    return countdownTimers.value.get(contestId) ?? 0;
  }

  // =========================================================================
  // RESET
  // =========================================================================

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
    $reset,
  };
});
