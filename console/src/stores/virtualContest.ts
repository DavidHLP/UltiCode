import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { VirtualContestSession } from "@/types/contest";
import {
  finishVirtualContest as apiFinishVirtual,
  fetchVirtualSession,
  startVirtualContest as apiStartVirtual,
} from "@/api/contest";

/**
 * Virtual contest store — start / load / finish lifecycle.
 *
 * Session is persisted into sessionStorage (keyed by contestId) so a page
 * refresh doesn't drop an in-progress virtual replay. Storage helpers are
 * internal; only the session state and the three lifecycle actions are public.
 *
 * Cross-tab broadcast and per-contest countdown timers were previously
 * co-located here but are now removed (C3 deepening — dead export removal).
 * Timer rendering is handled entirely inside VirtualContestTimer.vue via
 * reactive `virtualSession.endsAt`; no shared countdown state is needed.
 */
const VIRTUAL_SESSION_PREFIX = "ulticode:virtual-session:";

export const useVirtualContestStore = defineStore("virtualContest", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  const virtualSession = ref<VirtualContestSession | null>(null);
  const error = ref<string | null>(null);

  // =========================================================================
  // SESSION STORAGE HELPERS (internal)
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
      // sessionStorage may be unavailable (private mode / quota); ignore.
    }
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  // R7.4 / F-15: backend returns isActive boolean; use it as primary
  // signal. Status literal (STARTED / FINISHED) is a fallback.
  const isInVirtualContest = computed(() => {
    const session = virtualSession.value;
    if (!session) return false;
    if (typeof session.isActive === "boolean") return session.isActive;
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
  // ACTIONS — LIFECYCLE
  // =========================================================================

  /**
   * Start a virtual contest session.
   * - Writes session into reactive state and sessionStorage.
   * - On API failure: state and storage are unchanged; error is set and
   *   the exception is re-thrown so callers can handle it.
   */
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

  /**
   * Load the active virtual session for a contest.
   *
   * R10.1 / F-51: the persisted session is a placeholder for instant render,
   * NOT a source of truth. Always re-validate against the server — the
   * backend may have finalised the session (F-07 90-min hard deadline,
   * admin force-finish, scheduler sweep) while this tab was idle.
   *
   * R3.4 (perf nicety): cache is rendered immediately so the timer card
   * doesn't blank during the network round-trip.
   */
  async function loadVirtualSession(contestId: string) {
    const persisted = loadVirtualSessionFromStorage(contestId);
    if (persisted) virtualSession.value = persisted;
    try {
      const server = await fetchVirtualSession(contestId);
      virtualSession.value = server;
      saveVirtualSessionToStorage(contestId, server);
    } catch {
      // Cache survives a network failure so the timer persists offline.
      // If no cache existed, clear the phantom in-memory session.
      if (!persisted) virtualSession.value = null;
    }
  }

  /**
   * Finish the active virtual session.
   * - Calls the API, then nulls the reactive state and sessionStorage.
   * - On API failure: state and storage are unchanged; error is set and
   *   the exception is re-thrown.
   */
  async function finishVirtualContest(contestId: string) {
    if (!virtualSession.value?.id) return;
    error.value = null;
    try {
      await apiFinishVirtual(contestId, virtualSession.value.id);
      virtualSession.value = null;
      saveVirtualSessionToStorage(contestId, null);
    } catch (err) {
      error.value =
        err instanceof Error
          ? err.message
          : "Failed to finish virtual contest";
      throw err;
    }
  }

  /**
   * R6.4 / HIGH-1: mutable update for the active virtual session.
   * Used by VirtualContestTimer's visibilitychange handler to shift
   * endsAt forward by the hidden duration so the visible timer doesn't
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

  return {
    // State
    virtualSession,
    error,

    // Getters
    isInVirtualContest,
    currentVirtualTimeRemaining,

    // Actions — lifecycle
    startVirtualContest,
    loadVirtualSession,
    finishVirtualContest,
    setVirtualSession,

    clearError,
  };
});
