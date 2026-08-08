<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useUserStatsStore } from "@/stores/userStats";
import { useAuthStore } from "@/stores/auth";
import StatsCard from "@/components/dashboard/StatsCard.vue";
import ActivityHeatmap from "@/components/dashboard/ActivityHeatmap.vue";
import SkillRadarChart from "@/components/dashboard/SkillRadarChart.vue";
import RecentActivity from "@/components/dashboard/RecentActivity.vue";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { Trophy, Flame, Target, BarChart3 } from "lucide-vue-next";
import type { RecentActivity as RecentActivityType } from "@/types/userStats";

const { t } = useI18n();
const userStatsStore = useUserStatsStore();
const authStore = useAuthStore();

const loading = computed(() => userStatsStore.loading);

// Mock recent activity - in a real app, this would come from an API
const recentActivity = ref<RecentActivityType[]>([]);

onMounted(async () => {
  await userStatsStore.initialize();
});
</script>

<template>
  <div class="container mx-auto max-w-6xl space-y-6 py-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold tracking-tight">
          {{ t("personal.dashboard.title") }}
        </h1>
        <p class="text-muted-foreground">
          {{
            t("personal.dashboard.welcomeBack", { name: authStore.userName })
          }}
        </p>
      </div>
    </div>

    <!-- Stats Grid -->
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <template v-if="loading">
        <Skeleton v-for="i in 4" :key="i" class="h-24 rounded-none-none" />
      </template>
      <template v-else>
        <StatsCard
          :title="t('personal.dashboard.stats.problemsSolved')"
          :value="userStatsStore.totalProgress.count"
          :subtitle="
            t('personal.dashboard.stats.ofTotal', {
              total: userStatsStore.totalProgress.total,
            })
          "
          color="green"
        >
          <template #icon>
            <Trophy class="h-5 w-5 text-[var(--terminal-green)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.dashboard.stats.currentStreak')"
          :value="userStatsStore.stats?.streak || 0"
          :subtitle="t('personal.dashboard.stats.days')"
          color="orange"
        >
          <template #icon>
            <Flame class="h-5 w-5 text-[var(--terminal-amber)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.dashboard.stats.completion')"
          :value="`${userStatsStore.totalProgress.percentage}%`"
          :subtitle="t('personal.dashboard.stats.problemsCompleted')"
          color="blue"
        >
          <template #icon>
            <Target class="h-5 w-5 text-[var(--accent-electric)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.dashboard.stats.skillsMastered')"
          :value="userStatsStore.skills?.skills.length || 0"
          :subtitle="t('personal.dashboard.stats.uniqueTopics')"
          color="purple"
        >
          <template #icon>
            <BarChart3 class="h-5 w-5 text-[var(--terminal-purple)]" />
          </template>
        </StatsCard>
      </template>
    </div>

    <!-- Difficulty Progress -->
    <div class="rounded-none-none border bg-card p-6">
      <h2 class="mb-4 text-lg font-semibold">
        {{ t("personal.dashboard.progress.title") }}
      </h2>

      <div v-if="loading" class="space-y-4">
        <Skeleton v-for="i in 3" :key="i" class="h-12 rounded-none" />
      </div>

      <div v-else class="space-y-4">
        <!-- Easy -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="font-medium text-[var(--terminal-green)]">{{
              t("personal.stats.easy")
            }}</span>
            <span class="text-muted-foreground">
              {{ userStatsStore.easyProgress.count }} /
              {{ userStatsStore.easyProgress.total }}
            </span>
          </div>
          <Progress
            :model-value="userStatsStore.easyProgress.percentage"
            class="h-2 [&>div]:bg-[var(--terminal-green)]"
          />
        </div>

        <!-- Medium -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="font-medium text-[var(--terminal-amber)]">{{
              t("personal.stats.medium")
            }}</span>
            <span class="text-muted-foreground">
              {{ userStatsStore.mediumProgress.count }} /
              {{ userStatsStore.mediumProgress.total }}
            </span>
          </div>
          <Progress
            :model-value="userStatsStore.mediumProgress.percentage"
            class="h-2 [&>div]:bg-[var(--terminal-amber)]"
          />
        </div>

        <!-- Hard -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="font-medium text-[var(--terminal-red)]">{{
              t("personal.stats.hard")
            }}</span>
            <span class="text-muted-foreground">
              {{ userStatsStore.hardProgress.count }} /
              {{ userStatsStore.hardProgress.total }}
            </span>
          </div>
          <Progress
            :model-value="userStatsStore.hardProgress.percentage"
            class="h-2 [&>div]:bg-[var(--terminal-red)]"
          />
        </div>
      </div>
    </div>

    <!-- Activity Heatmap & Skills Grid -->
    <div class="grid gap-6 lg:grid-cols-2">
      <!-- Heatmap -->
      <div class="rounded-none-none border bg-card p-6">
        <h2 class="mb-4 text-lg font-semibold">
          {{ t("personal.dashboard.heatmap.title") }}
        </h2>
        <ActivityHeatmap
          v-if="userStatsStore.stats"
          :data="userStatsStore.stats.heatmap"
        />
        <Skeleton v-else class="h-32 rounded-none" />
      </div>

      <!-- Skills Radar -->
      <div class="rounded-none-none border bg-card p-6">
        <h2 class="mb-4 text-lg font-semibold">
          {{ t("personal.dashboard.skills.title") }}
        </h2>
        <SkillRadarChart
          v-if="userStatsStore.skills"
          :skills="userStatsStore.skills.skills"
          :max-display="8"
        />
        <Skeleton v-else class="h-64 rounded-none" />
      </div>
    </div>

    <!-- Recent Activity -->
    <div class="rounded-none-none border bg-card p-6">
      <h2 class="mb-4 text-lg font-semibold">
        {{ t("personal.dashboard.activity.title") }}
      </h2>
      <RecentActivity :activities="recentActivity" />
    </div>
  </div>
</template>
