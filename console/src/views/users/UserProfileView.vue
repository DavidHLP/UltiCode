<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { formatDate } from "@/utils/datetime";
import {
  fetchUserProfile,
  fetchUserStats,
  fetchUserSkills,
  type ProfileData,
} from "@/api/user";
import type { UserStats, UserSkills } from "@/types/userStats";
import StatsCard from "@/components/dashboard/StatsCard.vue";
import ActivityHeatmap from "@/components/dashboard/ActivityHeatmap.vue";
import SkillRadarChart from "@/components/dashboard/SkillRadarChart.vue";
import {
  Trophy,
  Flame,
  Target,
  BarChart3,
  Calendar,
  Globe,
} from "lucide-vue-next";
import { Skeleton } from "@/components/ui/skeleton";
import { Progress } from "@/components/ui/progress";

const { t } = useI18n();
const route = useRoute();

const userId = computed(() => route.params.id as string);

const loading = ref(true);
const profile = ref<ProfileData | null>(null);
const stats = ref<UserStats | null>(null);
const skills = ref<UserSkills | null>(null);
const error = ref<string | null>(null);

const acceptanceRate = computed(() => {
  if (!stats.value || !profile.value?.submissionCount) return 0;
  const { stats: problemStats } = stats.value;
  const total =
    (problemStats.Easy?.count || 0) +
    (problemStats.Medium?.count || 0) +
    (problemStats.Hard?.count || 0);
  if (profile.value.submissionCount === 0) return 0;
  return Math.round((total / profile.value.submissionCount) * 100);
});

const totalSolved = computed(() => {
  if (!stats.value) return 0;
  const { stats: problemStats } = stats.value;
  return (
    (problemStats.Easy?.count || 0) +
    (problemStats.Medium?.count || 0) +
    (problemStats.Hard?.count || 0)
  );
});

onMounted(async () => {
  try {
    loading.value = true;
    const [profileData, statsData, skillsData] = await Promise.all([
      fetchUserProfile(userId.value),
      fetchUserStats(userId.value),
      fetchUserSkills(userId.value),
    ]);
    profile.value = profileData;
    stats.value = statsData;
    skills.value = skillsData;
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "Failed to load user profile";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="container mx-auto max-w-6xl space-y-6 py-6">
    <!-- Error State -->
    <div v-if="error" class="rounded-none border bg-card p-6 text-center">
      <p class="text-muted-foreground">{{ error }}</p>
    </div>

    <!-- Loading State -->
    <template v-else-if="loading">
      <div class="flex items-center justify-between">
        <div class="space-y-2">
          <Skeleton class="h-8 w-48" />
          <Skeleton class="h-4 w-32" />
        </div>
        <Skeleton class="h-24 w-24 rounded-full" />
      </div>
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Skeleton v-for="i in 4" :key="i" class="h-24" />
      </div>
    </template>

    <!-- User Profile Content -->
    <template v-else-if="profile">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold tracking-tight">
            {{ profile.name || profile.username }}
          </h1>
          <p class="text-muted-foreground">@{{ profile.username }}</p>
        </div>
        <img
          v-if="profile.avatar"
          :src="profile.avatar"
          :alt="profile.username"
          class="h-24 w-24 rounded-full object-cover"
        />
        <div
          v-else
          class="flex h-24 w-24 items-center justify-center rounded-full bg-[var(--terminal-green)] text-2xl font-bold text-[var(--color-background)]"
        >
          {{ (profile.name || profile.username).charAt(0).toUpperCase() }}
        </div>
      </div>

      <!-- Bio and Meta -->
      <div class="rounded-none border bg-card p-6 space-y-4">
        <p v-if="profile.bio" class="text-sm">{{ profile.bio }}</p>
        <div class="flex flex-wrap gap-4 text-sm text-muted-foreground">
          <div v-if="profile.joinedAt" class="flex items-center gap-1">
            <Calendar class="h-4 w-4" />
            <span>{{
              t("profile.joined", {
                date: formatDate(profile.joinedAt),
              })
            }}</span>
          </div>
          <div v-if="profile.location" class="flex items-center gap-1">
            <Globe class="h-4 w-4" />
            <span>{{ profile.location }}</span>
          </div>
          <div v-if="profile.website" class="flex items-center gap-1">
            <a
              :href="profile.website"
              target="_blank"
              rel="noopener"
              class="hover:text-[var(--accent-electric)]"
            >
              {{ profile.website }}
            </a>
          </div>
        </div>
      </div>

      <!-- Stats Grid -->
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatsCard
          :title="t('profile.stats.problemsSolved')"
          :value="totalSolved"
          :subtitle="
            t('profile.stats.ofTotal', { total: stats?.totalSolved || 0 })
          "
          color="green"
        >
          <template #icon>
            <Trophy class="h-5 w-5 text-[var(--terminal-green)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('profile.stats.globalRank')"
          :value="profile.globalRank ?? '-'"
          :subtitle="t('profile.stats.basedOnSolved')"
          color="blue"
        >
          <template #icon>
            <Target class="h-5 w-5 text-[var(--accent-electric)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('profile.stats.acceptanceRate')"
          :value="`${acceptanceRate}%`"
          :subtitle="
            t('profile.stats.submissions', {
              count: profile.submissionCount || 0,
            })
          "
          color="purple"
        >
          <template #icon>
            <BarChart3 class="h-5 w-5 text-[var(--terminal-purple)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('profile.stats.submissions')"
          :value="profile.submissionCount || 0"
          :subtitle="t('profile.stats.totalSubmissions')"
          color="orange"
        >
          <template #icon>
            <Flame class="h-5 w-5 text-[var(--terminal-amber)]" />
          </template>
        </StatsCard>
      </div>

      <!-- Difficulty Progress -->
      <div v-if="stats" class="rounded-none border bg-card p-6">
        <h2 class="mb-4 text-lg font-semibold">
          {{ t("profile.progress.title") }}
        </h2>
        <div class="space-y-4">
          <!-- Easy -->
          <div class="space-y-2">
            <div class="flex items-center justify-between text-sm">
              <span class="font-medium text-[var(--terminal-green)]">{{
                t("personal.stats.easy")
              }}</span>
              <span class="text-muted-foreground">
                {{ stats.stats.Easy.count }} / {{ stats.stats.Easy.total }}
              </span>
            </div>
            <Progress
              :model-value="
                stats.stats.Easy.total > 0
                  ? (stats.stats.Easy.count / stats.stats.Easy.total) * 100
                  : 0
              "
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
                {{ stats.stats.Medium.count }} / {{ stats.stats.Medium.total }}
              </span>
            </div>
            <Progress
              :model-value="
                stats.stats.Medium.total > 0
                  ? (stats.stats.Medium.count / stats.stats.Medium.total) * 100
                  : 0
              "
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
                {{ stats.stats.Hard.count }} / {{ stats.stats.Hard.total }}
              </span>
            </div>
            <Progress
              :model-value="
                stats.stats.Hard.total > 0
                  ? (stats.stats.Hard.count / stats.stats.Hard.total) * 100
                  : 0
              "
              class="h-2 [&>div]:bg-[var(--terminal-red)]"
            />
          </div>
        </div>
      </div>

      <!-- Activity Heatmap & Skills -->
      <div v-if="stats || skills" class="grid gap-6 lg:grid-cols-2">
        <!-- Heatmap -->
        <div v-if="stats" class="rounded-none border bg-card p-6">
          <h2 class="mb-4 text-lg font-semibold">
            {{ t("personal.dashboard.heatmap.title") }}
          </h2>
          <ActivityHeatmap v-if="stats.heatmap" :data="stats.heatmap" />
        </div>

        <!-- Skills Radar -->
        <div v-if="skills" class="rounded-none border bg-card p-6">
          <h2 class="mb-4 text-lg font-semibold">
            {{ t("personal.dashboard.skills.title") }}
          </h2>
          <SkillRadarChart
            v-if="skills.skills"
            :skills="skills.skills"
            :max-display="8"
          />
        </div>
      </div>
    </template>
  </div>
</template>
