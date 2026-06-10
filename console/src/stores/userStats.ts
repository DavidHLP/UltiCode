import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type { UserStats, UserSkills } from "@/types/userStats";
import { fetchUserStats, fetchUserSkills } from "@/api/user";
import { useAuthStore } from "./auth";

export const useUserStatsStore = defineStore("userStats", () => {
  const stats = ref<UserStats | null>(null);
  const skills = ref<UserSkills | null>(null);
  const loading = ref(false);
  const lastFetch = ref<number>(0);
  const cacheTTL = 5 * 60 * 1000; // 5 分钟
  const error = ref<string | null>(null);

  // Computed properties
  const easyProgress = computed(() => {
    if (!stats.value) return { count: 0, total: 0, percentage: 0 };
    const { count, total } = stats.value.stats.Easy;
    return {
      count,
      total,
      percentage: total > 0 ? Math.round((count / total) * 100) : 0,
    };
  });

  const mediumProgress = computed(() => {
    if (!stats.value) return { count: 0, total: 0, percentage: 0 };
    const { count, total } = stats.value.stats.Medium;
    return {
      count,
      total,
      percentage: total > 0 ? Math.round((count / total) * 100) : 0,
    };
  });

  const hardProgress = computed(() => {
    if (!stats.value) return { count: 0, total: 0, percentage: 0 };
    const { count, total } = stats.value.stats.Hard;
    return {
      count,
      total,
      percentage: total > 0 ? Math.round((count / total) * 100) : 0,
    };
  });

  const totalProgress = computed(() => {
    if (!stats.value) return { count: 0, total: 0, percentage: 0 };
    const totalCount =
      stats.value.stats.Easy.count +
      stats.value.stats.Medium.count +
      stats.value.stats.Hard.count;
    const totalTotal =
      stats.value.stats.Easy.total +
      stats.value.stats.Medium.total +
      stats.value.stats.Hard.total;
    return {
      count: totalCount,
      total: totalTotal,
      percentage:
        totalTotal > 0 ? Math.round((totalCount / totalTotal) * 100) : 0,
    };
  });

  const isCacheValid = computed(() => {
    return Date.now() - lastFetch.value < cacheTTL;
  });

  // Actions
  async function fetchStats(forceRefresh = false) {
    const authStore = useAuthStore();
    if (!authStore.userId) return null;

    if (!forceRefresh && isCacheValid.value && stats.value) {
      return stats.value;
    }

    loading.value = true;
    error.value = null;
    try {
      const result = await fetchUserStats(authStore.userId);
      stats.value = result;
      lastFetch.value = Date.now();
      return result;
    } catch (err) {
      error.value = err instanceof Error ? err.message : "Failed to load stats";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function fetchSkills(forceRefresh = false) {
    const authStore = useAuthStore();
    if (!authStore.userId) return null;

    if (!forceRefresh && skills.value) {
      return skills.value;
    }

    loading.value = true;
    error.value = null;
    try {
      const result = await fetchUserSkills(authStore.userId);
      skills.value = result;
      return result;
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to load skills";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function initialize() {
    error.value = null;
    try {
      await Promise.all([fetchStats(), fetchSkills()]);
    } catch (err) {
      error.value =
        err instanceof Error ? err.message : "Failed to initialize user stats";
    }
  }

  function invalidateCache() {
    lastFetch.value = 0;
  }

  function clearError() {
    error.value = null;
  }

  return {
    // State
    stats,
    skills,
    loading,
    error,

    // Computed
    easyProgress,
    mediumProgress,
    hardProgress,
    totalProgress,

    // Actions
    fetchStats,
    fetchSkills,
    initialize,
    invalidateCache,
    clearError,
  };
});
