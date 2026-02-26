import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type { Achievement, AchievementProgress } from "@/types/achievement";
import { achievementApi } from "@/api/achievement";
import { getSocketManager, NotificationEvent } from "@/lib/socket";

export const useAchievementStore = defineStore("achievement", () => {
  const achievements = ref<Achievement[]>([]);
  const userAchievements = ref<AchievementProgress[]>([]);
  const totalPoints = ref(0);
  const loading = ref(false);
  const initialized = ref(false);

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
    loading.value = true;
    try {
      const result = await achievementApi.getAll(params);
      achievements.value = result.items;
      return result;
    } finally {
      loading.value = false;
    }
  }

  async function fetchUserAchievements() {
    loading.value = true;
    try {
      const result = await achievementApi.getUserAchievements();
      userAchievements.value = result;
      return result;
    } finally {
      loading.value = false;
    }
  }

  async function fetchUserPoints() {
    try {
      const result = await achievementApi.getUserPoints();
      totalPoints.value = result.points;
      return result.points;
    } catch {
      return 0;
    }
  }

  async function initialize() {
    if (initialized.value) return;

    await Promise.all([fetchUserAchievements(), fetchUserPoints()]);
    initialized.value = true;
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

  return {
    // State
    achievements,
    userAchievements,
    totalPoints,
    loading,
    initialized,

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
  };
});
