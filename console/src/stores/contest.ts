import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  ContestListItem,
  ContestDetail,
  ContestProblemSummary,
  ParticipationStatus,
  VirtualContestSession,
  GlobalRankingEntry,
  UserContestHistory,
} from "@/types/contest";
import { useAuthStore } from "@/stores/auth";
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
  getContestProblems,
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

  // Contest problems keyed by contestId. Hoisted from the local ref that
  // used to live in ContestDetailView so the problem page can read it
  // without re-fetching or hand-wiring a parallel store. The problem page
  // uses this to compute prev/next within a contest and to guard "this
  // problem is part of the contest" without a new endpoint.
  const contestProblems = ref<Map<string, ContestProblemSummary[]>>(
    new Map(),
  );

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

  // R7.4 / F-15: 后端 /virtual/start 与 /virtual/session 返回 status 字面量
  // 已对齐到 ContestParticipantStatus 枚举（STARTED / FINISHED）。后端
  // 同时返回 isActive 布尔，优先用它判定；status 字面量仅作兜底，集中
  // 处理避免各组件重复踩坑。前端 VirtualContestStatus 的 "IN_PROGRESS"
  // 历史命名保留为 alias，保证现有组件不破。
  const isInVirtualContest = computed(() => {
    const session = virtualSession.value;
    if (!session) return false;
    if (typeof session.isActive === "boolean") return session.isActive;
    // F-15: 跨栈枚举对齐到 ContestParticipantStatus 枚举值。
    // VirtualContestStatus.IN_PROGRESS 保留为 alias。
    const status = session.status as string;
    return status === "STARTED" || status === "IN_PROGRESS";
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

  /**
   * R9.4 / F-46: localStorage cross-tab broadcast for active virtual
   * sessions. Two tabs opening the same virtual replay produces a
   * confusing duplicate-finish UX; the backend R3.3 FOR UPDATE
   * serialises but does not prevent the duplicate UX. The frontend
   * detection is a UX optimisation; the backend is the source of
   * truth. Stale entries (>30s) are ignored so a crashed tab does
   * not lock out the user.
   */
  const VIRTUAL_TAB_BROADCAST_KEY = "ulticode:virtual:active";
  const VIRTUAL_TAB_STALE_MS = 30_000;

  interface VirtualTabBroadcast {
    contestId: string;
    userId: string;
    ts: number;
  }

  function readVirtualTabBroadcast(): VirtualTabBroadcast | null {
    try {
      const raw = localStorage.getItem(VIRTUAL_TAB_BROADCAST_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as VirtualTabBroadcast;
      if (Date.now() - parsed.ts > VIRTUAL_TAB_STALE_MS) return null;
      return parsed;
    } catch {
      return null;
    }
  }

  function writeVirtualTabBroadcast(contestId: string, userId: string): void {
    try {
      const payload: VirtualTabBroadcast = {
        contestId,
        userId,
        ts: Date.now(),
      };
      localStorage.setItem(VIRTUAL_TAB_BROADCAST_KEY, JSON.stringify(payload));
    } catch {
      // localStorage may be unavailable (private mode / quota); ignore.
    }
  }

  function clearVirtualTabBroadcast(): void {
    try {
      localStorage.removeItem(VIRTUAL_TAB_BROADCAST_KEY);
    } catch {
      // ignore
    }
  }

  async function startVirtualContest(contestId: string) {
    error.value = null;
    // R9.4 / F-46: pre-check whether another tab is already in a
    // virtual session for the same contest. The broadcast is keyed
    // by contest+user; a stale entry (>30s) is ignored.
    const auth = useAuthStore();
    const currentUserId = auth.userId ?? "";
    const existing = readVirtualTabBroadcast();
    if (
      existing &&
      existing.contestId === contestId &&
      existing.userId === currentUserId
    ) {
      const msg = "You already have an active virtual session in another tab";
      error.value = msg;
      throw new Error(msg);
    }
    try {
      const session = await apiStartVirtual(contestId);
      virtualSession.value = session;
      saveVirtualSessionToStorage(contestId, session);
      writeVirtualTabBroadcast(contestId, currentUserId);
      return session;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to start virtual contest";
      throw err;
    }
  }

  async function loadVirtualSession(contestId: string) {
    // R10.1 / F-51: the persisted session is a placeholder for instant
    // render, NOT a source of truth. Always re-validate against the
    // server. The backend may have finalized the session (F-07 90-min
    // hard deadline, admin force-finish, scheduler sweep) while this
    // tab was idle, and rehydrating the cache as-is would keep the
    // timer card stuck on "进行中" with no manual way to recover.
    // R3.4 (perf nicety): we still show the cache immediately so the
    // timer card doesn't blank during the network round-trip.
    const persisted = loadVirtualSessionFromStorage(contestId);
    if (persisted) virtualSession.value = persisted;
    try {
      const server = await fetchVirtualSession(contestId);
      virtualSession.value = server;
      saveVirtualSessionToStorage(contestId, server);
    } catch {
      // If the cache is present and the network failed, keep it so the
      // timer survives an offline navigation. Otherwise clear so we
      // don't render a phantom "in progress" card.
      if (!persisted) virtualSession.value = null;
    }
  }

  async function finishVirtualContest(contestId: string) {
    if (!virtualSession.value?.id) return;
    error.value = null;
    try {
      await apiFinishVirtual(contestId, virtualSession.value.id);
      virtualSession.value = null;
      saveVirtualSessionToStorage(contestId, null);
      // R9.4 / F-46: clear the cross-tab broadcast so other tabs can
      // start a fresh virtual session for the same contest.
      clearVirtualTabBroadcast();
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

  /**
   * R6.4 / HIGH-1: mutable update for the active virtual session. Used
   * by VirtualContestTimer's visibilitychange handler to shift endsAt
   * forward by the hidden duration so the user-visible timer doesn't
   * burn through virtual time on backgrounded tabs.
   */
  function setVirtualSession(session: VirtualContestSession | null): void {
    virtualSession.value = session;
    if (session) {
      saveVirtualSessionToStorage(session.contestId, session);
    }
  }

  function $reset() {
    upcomingContests.value = [];
    runningContests.value = [];
    pastContests.value = [];
    pastContestsTotal.value = 0;
    currentContest.value = null;
    contestProblems.value = new Map();
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
    contestProblems,
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
    loadProblems,
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
    setVirtualSession,
    $reset,
  };
});
