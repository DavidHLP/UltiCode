import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  ContestListItem,
  ContestDetail,
  ParticipationStatus,
  VirtualContestSession,
  GlobalRankingEntry,
  UserContestHistory,
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

  // R3.4: virtualSession is per-contest; persist into sessionStorage so a
  // page refresh doesn't drop the session. The key encodes the contestId so
  // the same user can have separate active virtual sessions across contests.
  // We rehydrate below in the action definitions; this is the single source
  // of truth for the in-memory state. The prefix is documented here so a
  // future cross-store consumer (e.g. a logout flow that needs to clear
  // virtual sessions) can derive the same key shape.
  const VIRTUAL_SESSION_PREFIX = "ulticode:virtual-session:";
  function loadVirtualSessionFromStorage(contestId: string): VirtualContestSession | null {
    try {
      const raw = sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + contestId);
      if (!raw) return null;
      return JSON.parse(raw) as VirtualContestSession;
    } catch {
      return null;
    }
  }
  function saveVirtualSessionToStorage(contestId: string, session: VirtualContestSession | null) {
    try {
      const key = VIRTUAL_SESSION_PREFIX + contestId;
      if (session == null) {
        sessionStorage.removeItem(key);
      } else {
        sessionStorage.setItem(key, JSON.stringify(session));
      }
    } catch {
      // sessionStorage may be unavailable (private mode, quota); ignore.
    }
  }
  const virtualSession = ref<VirtualContestSession | null>(null);

  const registeredContests = ref<ContestListItem[]>([]);
  const participatedContests = ref<ContestListItem[]>([]);
  const virtualContests = ref<ContestListItem[]>([]);
  const contestHistory = ref<UserContestHistory[]>([]);

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
    return (
      participation?.status === "REGISTERED" ||
      participation?.status === "STARTED"
    );
  });

  // 后端 /virtual/start 与 /virtual/session 返回的 status 为小写 "started"，
  // 与前端 VirtualContestStatus 的 "IN_PROGRESS" 不一致（跨栈 DTO 枚举错配，
  // F-15 / R6.5 已记录）。后端同时返回 isActive 布尔，优先用它判定；status
  // 字面量仅作兜底，集中处理避免各组件重复踩坑。彻底解决需后端 enum 化
  // （CLAUDE.md 优先项），不在本轮范围。
  const isInVirtualContest = computed(() => {
    const session = virtualSession.value;
    if (!session) return false;
    if (typeof session.isActive === "boolean") return session.isActive;
    // F-15 / R6.5: 跨栈枚举错配。后端目前返回小写 "started"，
    // 前端 VirtualContestStatus 是 "IN_PROGRESS"。这里按 string
    // 比较兜底；后续 ADR-008 阶段推动后端 enum 化后，类型会自动对齐。
    const status = session.status as string;
    return status === "IN_PROGRESS" || status === "started";
  });

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
      saveVirtualSessionToStorage(contestId, session);
      return session;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to start virtual contest";
      throw err;
    }
  }

  async function loadVirtualSession(contestId: string) {
    // R3.4: prefer the persisted session so a page refresh keeps the user
    // on the right timer. Only fall back to the server if storage is empty
    // (e.g. user opened the page in a new tab that didn't see the start).
    const persisted = loadVirtualSessionFromStorage(contestId);
    if (persisted) {
      virtualSession.value = persisted;
      return;
    }
    try {
      const server = await fetchVirtualSession(contestId);
      virtualSession.value = server;
      saveVirtualSessionToStorage(contestId, server);
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
      saveVirtualSessionToStorage(contestId, null);
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to finish virtual contest";
      throw err;
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
    // R3.4: only drop the in-memory ref. The sessionStorage entries are
    // scoped per contestId and the user might still be in a virtual session
    // in another contest. We don't enumerate all sessionStorage keys here
    // because each tab is typically tied to a single contest flow; if the
    // store is reset, the next loadVirtualSession call will re-fetch from
    // the server (or rehydrate from storage if it persists across resets).
    virtualSession.value = null;
    registeredContests.value = [];
    participatedContests.value = [];
    virtualContests.value = [];
    contestHistory.value = [];
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
    startCountdownTimer,
    stopCountdownTimer,
    getCountdown,
    clearError,
    $reset,
  };
});
