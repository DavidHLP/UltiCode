<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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
        <h1 class="text-2xl font-bold tracking-tight">Dashboard</h1>
        <p class="text-muted-foreground">
          Welcome back, {{ authStore.userName }}! Here's your progress overview.
        </p>
      </div>
    </div>

    <!-- Stats Grid -->
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <template v-if="loading">
        <Skeleton v-for="i in 4" :key="i" class="h-24 rounded-lg" />
      </template>
      <template v-else>
        <StatsCard
          title="Problems Solved"
          :value="userStatsStore.totalProgress.count"
          :subtitle="`of ${userStatsStore.totalProgress.total} total`"
          color="green"
        >
          <template #icon>
            <Trophy class="h-5 w-5 text-green-500" />
          </template>
        </StatsCard>

        <StatsCard
          title="Current Streak"
          :value="userStatsStore.stats?.streak || 0"
          subtitle="days"
          color="orange"
        >
          <template #icon>
            <Flame class="h-5 w-5 text-orange-500" />
          </template>
        </StatsCard>

        <StatsCard
          title="Completion"
          :value="`${userStatsStore.totalProgress.percentage}%`"
          subtitle="problems completed"
          color="blue"
        >
          <template #icon>
            <Target class="h-5 w-5 text-blue-500" />
          </template>
        </StatsCard>

        <StatsCard
          title="Skills Mastered"
          :value="userStatsStore.skills?.skills.length || 0"
          subtitle="unique topics"
          color="purple"
        >
          <template #icon>
            <BarChart3 class="h-5 w-5 text-purple-500" />
          </template>
        </StatsCard>
      </template>
    </div>

    <!-- Difficulty Progress -->
    <div class="rounded-lg border bg-card p-6">
      <h2 class="mb-4 text-lg font-semibold">Problem Solving Progress</h2>

      <div v-if="loading" class="space-y-4">
        <Skeleton v-for="i in 3" :key="i" class="h-12 rounded" />
      </div>

      <div v-else class="space-y-4">
        <!-- Easy -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="font-medium text-green-600">Easy</span>
            <span class="text-muted-foreground">
              {{ userStatsStore.easyProgress.count }} /
              {{ userStatsStore.easyProgress.total }}
            </span>
          </div>
          <Progress
            :model-value="userStatsStore.easyProgress.percentage"
            class="h-2 [&>div]:bg-green-500"
          />
        </div>

        <!-- Medium -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="font-medium text-yellow-600">Medium</span>
            <span class="text-muted-foreground">
              {{ userStatsStore.mediumProgress.count }} /
              {{ userStatsStore.mediumProgress.total }}
            </span>
          </div>
          <Progress
            :model-value="userStatsStore.mediumProgress.percentage"
            class="h-2 [&>div]:bg-yellow-500"
          />
        </div>

        <!-- Hard -->
        <div class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="font-medium text-red-600">Hard</span>
            <span class="text-muted-foreground">
              {{ userStatsStore.hardProgress.count }} /
              {{ userStatsStore.hardProgress.total }}
            </span>
          </div>
          <Progress
            :model-value="userStatsStore.hardProgress.percentage"
            class="h-2 [&>div]:bg-red-500"
          />
        </div>
      </div>
    </div>

    <!-- Activity Heatmap & Skills Grid -->
    <div class="grid gap-6 lg:grid-cols-2">
      <!-- Heatmap -->
      <div class="rounded-lg border bg-card p-6">
        <h2 class="mb-4 text-lg font-semibold">Activity Heatmap</h2>
        <ActivityHeatmap
          v-if="userStatsStore.stats"
          :data="userStatsStore.stats.heatmap"
        />
        <Skeleton v-else class="h-32 rounded" />
      </div>

      <!-- Skills Radar -->
      <div class="rounded-lg border bg-card p-6">
        <h2 class="mb-4 text-lg font-semibold">Skill Radar</h2>
        <SkillRadarChart
          v-if="userStatsStore.skills"
          :skills="userStatsStore.skills.skills"
          :max-display="8"
        />
        <Skeleton v-else class="h-64 rounded" />
      </div>
    </div>

    <!-- Recent Activity -->
    <div class="rounded-lg border bg-card p-6">
      <h2 class="mb-4 text-lg font-semibold">Recent Activity</h2>
      <RecentActivity :activities="recentActivity" />
    </div>
  </div>
</template>
