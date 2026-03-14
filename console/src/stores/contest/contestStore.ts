// console/src/stores/contest/contestStore.ts
import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  ContestListItem,
  ContestDetail,
  ContestProblem,
  ContestAnnouncement,
  ParticipationStatus,
  ContestFilters,
  PaginatedResult,
} from "@/types/contest";
import {
  getContests,
  getContest,
  getContestProblems,
  getAnnouncements,
  register,
  checkIn,
  withdraw,
  getMyParticipation,
} from "@/api/contest";

/**
 * Metadata for paginated contest list
 */
interface ContestMeta {
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

export const useContestStore = defineStore("contest", () => {
  // =========================================================================
  // STATE
  // =========================================================================

  /** List of contests from current query */
  const contests = ref<ContestListItem[]>([]);

  /** Current contest details */
  const currentContest = ref<ContestDetail | null>(null);

  /** Problems for the current contest */
  const currentProblems = ref<ContestProblem[]>([]);

  /** Announcements for the current contest */
  const currentAnnouncements = ref<ContestAnnouncement[]>([]);

  /** Current user's participation status for the current contest */
  const myParticipation = ref<ParticipationStatus | null>(null);

  /** Loading state */
  const loading = ref(false);

  /** Error message */
  const error = ref<string | null>(null);

  /** Pagination metadata */
  const meta = ref<ContestMeta>({
    total: 0,
    page: 1,
    limit: 10,
    totalPages: 0,
  });

  // =========================================================================
  // GETTERS
  // =========================================================================

  /** Check if the current contest is active (running/ongoing) */
  const isActive = computed(() => {
    if (!currentContest.value) return false;
    const status = currentContest.value.status;
    return status === "RUNNING" || status === "ONGOING";
  });

  /** Check if registration is open for the current contest */
  const canRegister = computed(() => {
    if (!currentContest.value) return false;
    const status = currentContest.value.status;
    // Can register if contest is upcoming or in registering state
    return status === "UPCOMING" || status === "REGISTERING";
  });

  /** Check if the current user is registered for the current contest */
  const isRegistered = computed(() => {
    return myParticipation.value?.isRegistered ?? false;
  });

  /** Check if the current user has checked in for the current contest */
  const isCheckedIn = computed(() => {
    return myParticipation.value?.status === "CHECKED_IN";
  });

  // =========================================================================
  // ACTIONS
  // =========================================================================

  /**
   * Fetch contests list with optional filters
   */
  async function fetchContests(filters?: ContestFilters): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      const result: PaginatedResult<ContestListItem> = await getContests(
        filters,
      );
      contests.value = result.items;
      meta.value = {
        total: result.total,
        page: result.page,
        limit: result.limit,
        totalPages: result.totalPages,
      };
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Failed to load contests";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Fetch contest details by slug
   */
  async function fetchContest(slug: string): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      currentContest.value = await getContest(slug);
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Failed to load contest";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Fetch problems for a contest
   */
  async function fetchProblems(slug: string): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      currentProblems.value = await getContestProblems(slug);
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Failed to load problems";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Fetch announcements for a contest
   */
  async function fetchAnnouncements(slug: string): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      currentAnnouncements.value = await getAnnouncements(slug);
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load announcements";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Register for a contest
   */
  async function registerContest(slug: string): Promise<void> {
    error.value = null;
    try {
      await register(slug);
      // Refresh participation status
      myParticipation.value = await getMyParticipation(slug);
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to register for contest";
      throw err;
    }
  }

  /**
   * Check in for a contest
   */
  async function checkInContest(slug: string): Promise<void> {
    error.value = null;
    try {
      await checkIn(slug);
      // Refresh participation status
      myParticipation.value = await getMyParticipation(slug);
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Failed to check in";
      throw err;
    }
  }

  /**
   * Withdraw from a contest
   */
  async function withdrawContest(slug: string): Promise<void> {
    error.value = null;
    try {
      await withdraw(slug);
      // Refresh participation status
      myParticipation.value = await getMyParticipation(slug);
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Failed to withdraw";
      throw err;
    }
  }

  /**
   * Clear current contest data
   */
  function clearCurrentContest(): void {
    currentContest.value = null;
    currentProblems.value = [];
    currentAnnouncements.value = [];
    myParticipation.value = null;
  }

  /**
   * Clear error state
   */
  function clearError(): void {
    error.value = null;
  }

  /**
   * Reset store to initial state
   */
  function $reset(): void {
    contests.value = [];
    currentContest.value = null;
    currentProblems.value = [];
    currentAnnouncements.value = [];
    myParticipation.value = null;
    loading.value = false;
    error.value = null;
    meta.value = {
      total: 0,
      page: 1,
      limit: 10,
      totalPages: 0,
    };
  }

  return {
    // State
    contests,
    currentContest,
    currentProblems,
    currentAnnouncements,
    myParticipation,
    loading,
    error,
    meta,

    // Getters
    isActive,
    canRegister,
    isRegistered,
    isCheckedIn,

    // Actions
    fetchContests,
    fetchContest,
    fetchProblems,
    fetchAnnouncements,
    registerContest,
    checkInContest,
    withdrawContest,
    clearCurrentContest,
    clearError,
    $reset,
  };
});
