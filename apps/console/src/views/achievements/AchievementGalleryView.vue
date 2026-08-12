<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useI18n } from "vue-i18n";
import { useAchievementStore } from "@/stores/achievement";
import AchievementCard from "@/components/achievement/AchievementCard.vue";
import UnlockToast from "@/components/achievement/UnlockToast.vue";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Trophy,
  Star,
  Target,
  Users,
  Flame,
  Medal,
  Sparkles,
} from "lucide-vue-next";

const { t } = useI18n();
const achievementStore = useAchievementStore();

const selectedCategory = ref<string>("all");
const showUnlockToast = ref(false);
const unlockedBadge = ref<{
  badgeName: string;
  badgeDescription: string;
  points: number;
} | null>(null);

const categories = computed(() => [
  { value: "all", label: t("achievement.categories.all"), icon: Sparkles },
  {
    value: "problem_solving",
    label: t("achievement.categories.problemSolving"),
    icon: Target,
  },
  {
    value: "consistency",
    label: t("achievement.categories.consistency"),
    icon: Flame,
  },
  { value: "contest", label: t("achievement.categories.contest"), icon: Medal },
  {
    value: "community",
    label: t("achievement.categories.community"),
    icon: Users,
  },
]);

const filteredAchievements = computed(() => {
  if (selectedCategory.value === "all") {
    return achievementStore.userAchievements;
  }
  return achievementStore.userAchievements.filter(
    (a) => a.category === selectedCategory.value,
  );
});

const sortedAchievements = computed(() => {
  return [...filteredAchievements.value].sort((a, b) => {
    // Earned first
    if (a.earned !== b.earned) return a.earned ? -1 : 1;
    // Then by tier
    return b.tier - a.tier;
  });
});

const stats = computed(() => ({
  total: achievementStore.totalCount,
  earned: achievementStore.earnedCount,
  points: achievementStore.totalPoints,
  percentage: achievementStore.completionPercentage,
}));

onMounted(async () => {
  await achievementStore.initialize();
});

function handleUnlockClose() {
  showUnlockToast.value = false;
  unlockedBadge.value = null;
}
</script>

<template>
  <div class="container mx-auto max-w-4xl space-y-6 py-6">
    <!-- Header -->
    <div class="space-y-2">
      <h1 class="text-3xl font-bold tracking-tight">
        {{ t("achievement.title") }}
      </h1>
      <p class="text-muted-foreground">
        {{ t("achievement.description") }}
      </p>
    </div>

    <!-- Stats Cards -->
    <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
      <div class="rounded-none border bg-card p-4 text-center">
        <Trophy class="mx-auto h-8 w-8 text-[var(--status-warning-mark)]" />
        <p class="mt-2 text-2xl font-bold">{{ stats.earned }}</p>
        <p class="text-sm text-muted-foreground">
          {{ t("achievement.earned") }}
        </p>
      </div>
      <div class="rounded-none border bg-card p-4 text-center">
        <Target class="mx-auto h-8 w-8 text-[var(--primary)]" />
        <p class="mt-2 text-2xl font-bold">{{ stats.total }}</p>
        <p class="text-sm text-muted-foreground">
          {{ t("achievement.total") }}
        </p>
      </div>
      <div class="rounded-none border bg-card p-4 text-center">
        <Star class="mx-auto h-8 w-8 text-[var(--status-special-mark)]" />
        <p class="mt-2 text-2xl font-bold">{{ stats.points }}</p>
        <p class="text-sm text-muted-foreground">
          {{ t("achievement.points") }}
        </p>
      </div>
      <div class="rounded-none border bg-card p-4 text-center">
        <div class="relative mx-auto h-8 w-8">
          <svg class="h-8 w-8 -rotate-90" viewBox="0 0 36 36">
            <circle
              cx="18"
              cy="18"
              r="16"
              fill="none"
              stroke="currentColor"
              stroke-width="3"
              class="text-muted"
            />
            <circle
              cx="18"
              cy="18"
              r="16"
              fill="none"
              stroke="currentColor"
              stroke-width="3"
              :stroke-dasharray="2 * Math.PI * 16"
              :stroke-dashoffset="
                2 * Math.PI * 16 * (1 - stats.percentage / 100)
              "
              class="text-foreground-strong transition-all duration-500"
            />
          </svg>
          <span
            class="absolute inset-0 flex items-center justify-center text-xs font-bold"
          >
            {{ stats.percentage }}%
          </span>
        </div>
        <p class="mt-2 text-sm text-muted-foreground">
          {{ t("achievement.complete") }}
        </p>
      </div>
    </div>

    <!-- Category Tabs -->
    <Tabs v-model="selectedCategory" class="w-full">
      <TabsList class="w-full justify-start">
        <TabsTrigger
          v-for="category in categories"
          :key="category.value"
          :value="category.value"
          class="gap-2"
        >
          <component :is="category.icon" class="h-4 w-4" />
          <span class="hidden sm:inline">{{ category.label }}</span>
        </TabsTrigger>
      </TabsList>

      <TabsContent :value="selectedCategory" class="mt-6">
        <!-- Loading state -->
        <div v-if="achievementStore.loading" class="grid gap-4 sm:grid-cols-2">
          <Skeleton v-for="i in 4" :key="i" class="h-32 rounded-none" />
        </div>

        <!-- Achievement grid -->
        <div
          v-else-if="sortedAchievements.length > 0"
          class="grid gap-4 sm:grid-cols-2"
        >
          <AchievementCard
            v-for="achievement in sortedAchievements"
            :key="achievement.achievementId"
            :achievement="achievement"
            show-progress
          />
        </div>

        <!-- Empty state -->
        <div
          v-else
          class="flex flex-col items-center justify-center py-12 text-center"
        >
          <Trophy class="h-16 w-16 text-muted-foreground/50" />
          <h3 class="mt-4 text-lg font-medium">
            {{ t("achievement.empty.title") }}
          </h3>
          <p class="mt-2 text-sm text-muted-foreground">
            {{ t("achievement.empty.description") }}
          </p>
        </div>
      </TabsContent>
    </Tabs>

    <!-- Unlock Toast -->
    <UnlockToast
      v-if="showUnlockToast && unlockedBadge"
      :badge-name="unlockedBadge.badgeName"
      :badge-description="unlockedBadge.badgeDescription"
      :points="unlockedBadge.points"
      :on-close="handleUnlockClose"
    />
  </div>
</template>
