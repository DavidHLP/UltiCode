import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type { Achievement, AchievementProgress } from "@/types/achievement";
import { achievementApi } from "@/api/achievement";
import { getSocketManager, NotificationEvent } from "@/lib/socket";
import { createValueRequest } from "@ulticode/request-state";

export const useAchievementStore = defineStore("achievement", () => {
  const achievements = ref<Achievement[]>([]);
  const userAchievements = ref<AchievementProgress[]>([]);
  const totalPoints = ref(0);
  const allRequest = createValueRequest({ errorMessage: "Failed to load achievements", rethrow: true });
  const userRequest = createValueRequest({ errorMessage: "Failed to load user achievements", rethrow: true });
  const loading = computed(() => allRequest.loading.value || userRequest.loading.value);
  const initialized = ref(false);
  const operationError = ref<string | null>(null);
  const error = computed(() => operationError.value ?? allRequest.error.value ?? userRequest.error.value);

  // Computed properties
  const earnedAchievements = computed(() =>
    userAchievements.value.filter((a) => a.earned),
  );

  const unearnedAchievements = computed(() =>
    userAchievements.value.filter((a) => !a.earned),
  );

  const achievementsByCategory = computed(() => {
    const grouped: Record<string, AchievementProgress[]> = {};
    userAchievements.value.forEach((a) => {
      if (!grouped[a.category]) {
        grouped[a.category] = [];
      }
      grouped[a.category]!.push(a);
    });
    return grouped;
  });

  const earnedCount = computed(() => earnedAchievements.value.length);
  const totalCount = computed(() => userAchievements.value.length);
  const completionPercentage = computed(() =>
    totalCount.value > 0
      ? Math.round((earnedCount.value / totalCount.value) * 100)
      : 0,
  );

  // Actions
  async function fetchAll(params?: { category?: string }) {
    operationError.value = null;
    allRequest.error.value = null;
    userRequest.error.value = null;
    try {
      const result = await allRequest.run(() => achievementApi.getAll(params));
      if (!result) return null;
      achievements.value = result.items;
      return result;
    } catch (err) {
      throw err;
    }
  }

  async function fetchUserAchievements() {
    operationError.value = null;
    allRequest.error.value = null;
    userRequest.error.value = null;
    try {
      const result = await userRequest.run(() => achievementApi.getUserAchievements());
      if (!result) return null;
      userAchievements.value = result;
      return result;
    } catch (err) {
      throw err;
    }
  }

  async function fetchUserPoints() {
    operationError.value = null;
    allRequest.error.value = null;
    userRequest.error.value = null;
    try {
      const result = await achievementApi.getUserPoints();
      totalPoints.value = result.points;
      return result.points;
    } catch (err) {
      operationError.value =
        err instanceof Error ? err.message : "Failed to load points";
      return 0;
    }
  }

  async function initialize() {
    if (initialized.value) return;

    operationError.value = null;
    allRequest.error.value = null;
    userRequest.error.value = null;
    try {
      await Promise.all([fetchUserAchievements(), fetchUserPoints()]);
      initialized.value = true;
    } catch (err) {
      operationError.value =
        err instanceof Error
          ? err.message
          : "Failed to initialize achievements";
    }
  }

  // Handle badge earned event from WebSocket
  function handleBadgeEarned(payload: {
    badgeId: string;
    badgeName: string;
    badgeDescription: string;
    earnedAt: string;
  }) {
    // Update the achievement in the list
    const achievement = userAchievements.value.find(
      (a) => a.achievementId === payload.badgeId,
    );
    if (achievement) {
      achievement.earned = true;
      achievement.earnedAt = payload.earnedAt;
      achievement.progress = achievement.target;
    }
    // Update points
    totalPoints.value += achievement?.points ?? 0;

    // Show a toast notification (handled by the component that listens)
  }

  // Setup WebSocket listeners
  function setupListeners() {
    const socketManager = getSocketManager();
    socketManager.on(NotificationEvent.BADGE_EARNED, handleBadgeEarned);
  }

  // Initialize listeners
  setupListeners();

  function clearError() {
    operationError.value = null;
    allRequest.error.value = null;
    userRequest.error.value = null;
  }

  return {
    // State
    achievements,
    userAchievements,
    totalPoints,
    loading,
    initialized,
    error,

    // Computed
    earnedAchievements,
    unearnedAchievements,
    achievementsByCategory,
    earnedCount,
    totalCount,
    completionPercentage,

    // Actions
    fetchAll,
    fetchUserAchievements,
    fetchUserPoints,
    initialize,
    clearError,
  };
});
