/**
 * Cross-component signalling for the contest problem page.
 *
 * The submit button lives in LayoutHeaderCenter; the contest-aware
 * toast + score refresh live in ContestProblemDock. They live in
 * different parts of the tree, so we use this tiny Pinia store as
 * a one-way message bus:
 *
 *   LayoutHeaderCenter       ── pushSubmit(result) ──▶   store.lastSubmitResult
 *   ContestProblemDock       ◀── watch lastSubmitResult ──▶  refresh + toast
 *
 * The store is intentionally minimal: it doesn't talk to the
 * network and doesn't cache any state. Anything that wants to
 * *display* contest-aware UI reads from `useContestProblemContext`
 * (provided by ProblemDetailView); this store only ferries
 * submit-result events.
 *
 * `announceUnreadCount` lives here too because the announcement
 * bell and the contest dock share the same
 * contest-scoped unread count.
 */
import { defineStore } from "pinia";
import { ref } from "vue";
import type { SubmissionRecord } from "@/types/submission";

export const useContestProblemShellStore = defineStore(
  "contestProblemShell",
  () => {
    /**
     * The most recent contest submission result. `null` until the
     * user submits something; the shell watches this and runs the
     * 1.5s-then-refresh dance when it changes.
     */
    const lastSubmitResult = ref<SubmissionRecord | null>(null);

    /**
     * "Unread" announcement count for the current contest. v1 uses
     * "created in the last 24h" as a proxy for unread — see the
     * degraded-v1 policy in the product spec. The bell reads this
     * and the ContestAnnouncementBell component updates it on
     * STOMP `onAnnouncement` pushes.
     */
    const announceUnreadCount = ref(0);

    function pushSubmit(result: SubmissionRecord): void {
      lastSubmitResult.value = result;
    }

    function clearLastSubmit(): void {
      lastSubmitResult.value = null;
    }

    function setAnnounceUnreadCount(n: number): void {
      announceUnreadCount.value = Math.max(0, n | 0);
    }

    function markAnnouncementsRead(): void {
      announceUnreadCount.value = 0;
    }

    return {
      lastSubmitResult,
      announceUnreadCount,
      pushSubmit,
      clearLastSubmit,
      setAnnounceUnreadCount,
      markAnnouncementsRead,
    };
  },
);
