import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { VirtualContestSession } from "@/types/contest";
import {
  finishVirtualContest as apiFinishVirtual,
  fetchVirtualSession,
  startVirtualContest as apiStartVirtual,
} from "@/api/contest";
import { useAuthStore } from "@/stores/auth";

/**
 * Virtual contest store — start/load/finish lifecycle, cross-tab
 * broadcast, and per-contest countdown timers.
 *
 * Per the 2026-07-06 architecture-review sweep, broadcast + timer live
 * here as one cohesive concern: a virtual replay is the only feature
 * that needs cross-tab coordination AND a ticking timer, so they share
 * state and lifecycle hooks naturally.
 *
 * R3.4 / F-51: virtualSession is per-contest; persist into
 * sessionStorage so a page refresh doesn't drop the session. The key
 * encodes the contestId so the same user can have separate active
 * virtual sessions across contests. The prefix is documented here so a
 * future cross-store consumer (e.g. a logout flow that needs to clear
 * virtual sessions) can derive the same key shape.
 */
const VIRTUAL_SESSION_PREFIX = "ulticode:virtual-session:";

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

export const useVirtualContestStore = defineStore("virtualContest", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  const virtualSession = ref<VirtualContestSession | null>(null);
  const countdownTimers = ref<Map<string, number>>(new Map());
  const error = ref<string | null>(null);

  // =========================================================================
  // SESSION STORAGE HELPERS
  // =========================================================================

  function loadVirtualSessionFromStorage(
    contestId: string,
  ): VirtualContestSession | null {
    try {
      const raw = sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + contestId);
      if (!raw) return null;
      return JSON.parse(raw) as VirtualContestSession;
    } catch {
      return null;
    }
  }

  function saveVirtualSessionToStorage(
    contestId: string,
    session: VirtualContestSession | null,
  ) {
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

  // =========================================================================
  // GETTERS
  // =========================================================================

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
  // ACTIONS — CROSS-TAB BROADCAST
  // =========================================================================

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

  // =========================================================================
  // ACTIONS — VIRTUAL CONTEST LIFECYCLE
  // =========================================================================

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

  function clearError() {
    error.value = null;
  }

  // =========================================================================
  // COUNTDOWN TIMER
  // =========================================================================

  // Map of contestId → window interval handle. Kept outside the
  // reactive state because the handle is a non-serialisable DOM
  // resource; we only care about the *remaining* seconds which lives
  // in the reactive `countdownTimers` map.
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

  return {
    // State
    virtualSession,
    countdownTimers,
    error,

    // Getters
    isInVirtualContest,
    currentVirtualTimeRemaining,

    // Actions — broadcast
    readVirtualTabBroadcast,
    writeVirtualTabBroadcast,
    clearVirtualTabBroadcast,

    // Actions — virtual session lifecycle
    startVirtualContest,
    loadVirtualSession,
    finishVirtualContest,
    setVirtualSession,

    // Actions — countdown timer
    startCountdownTimer,
    stopCountdownTimer,
    getCountdown,

    clearError,
  };
});
