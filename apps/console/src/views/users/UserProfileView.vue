<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { formatDate } from "@/utils/datetime";
import {
  fetchUserProfile,
  fetchProfileByUsername,
  fetchUserStats,
  fetchUserSkills,
  type ProfileData,
} from "@/api/user";
import type { UserStats, UserSkills } from "@/types/userStats";
import StatsCard from "@/components/dashboard/StatsCard.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import ActivityHeatmap from "@/components/dashboard/ActivityHeatmap.vue";
import SkillRadarChart from "@/components/dashboard/SkillRadarChart.vue";
import { Trophy, Flame, Target, BarChart3, Calendar, Globe } from "@lucide/vue";
import { Skeleton } from "@ulticode/design-system/ui/skeleton";
import { Progress } from "@ulticode/design-system/ui/progress";

const { t } = useI18n();
const route = useRoute();

const userId = computed(() => route.params.id as string);

const loading = ref(true);
const profile = ref<ProfileData | null>(null);
const stats = ref<UserStats | null>(null);
const skills = ref<UserSkills | null>(null);
const error = ref<string | null>(null);

const acceptanceRate = computed(() =>
  Math.min(100, Math.max(0, Number(profile.value?.acceptanceRate ?? 0))),
);

const totalSolved = computed(() => {
  if (!stats.value) return 0;
  const { stats: problemStats } = stats.value;
  return (
    (problemStats.Easy?.count || 0) +
    (problemStats.Medium?.count || 0) +
    (problemStats.Hard?.count || 0)
  );
});

async function loadProfile() {
  loading.value = true;
  error.value = null;
  profile.value = null;
  stats.value = null;
  skills.value = null;
  try {
    const [profileData, statsData, skillsData] = await Promise.all([
      fetchUserProfile(userId.value),
      fetchUserStats(userId.value),
      fetchUserSkills(userId.value),
    ]);
    const canonicalProfile = await fetchProfileByUsername(profileData.username);
    if (canonicalProfile.id !== profileData.id) {
      throw new Error(t("personal.social.loadError"));
    }
    profile.value = canonicalProfile;
    stats.value = statsData;
    skills.value = skillsData;
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "Failed to load user profile";
  } finally {
    loading.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <div
    class="mx-auto min-w-0 w-full max-w-6xl space-y-6 px-4 py-6 sm:px-6 lg:px-8"
  >
    <!-- Error State -->
    <div
      v-if="error"
      role="alert"
      class="space-y-3 rounded-lg border bg-card p-6 text-center [overflow-wrap:anywhere]"
    >
      <p class="text-destructive">{{ t("personal.social.loadError") }}</p>
      <p class="text-sm text-muted-foreground">{{ error }}</p>
      <RetryButton @retry="loadProfile" />
    </div>

    <!-- Loading State -->
    <template v-else-if="loading">
      <div class="flex items-center justify-between">
        <div class="space-y-2">
          <Skeleton class="h-8 w-48" />
          <Skeleton class="h-4 w-32" />
        </div>
        <Skeleton class="h-24 w-24" />
      </div>
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Skeleton v-for="i in 4" :key="i" class="h-24" />
      </div>
    </template>

    <!-- User Profile Content -->
    <template v-else-if="profile">
      <!-- Header -->
      <div class="flex items-center justify-between gap-4">
        <div class="min-w-0 flex-1">
          <h1 class="break-words text-2xl font-bold tracking-tight">
            {{ profile.name || profile.username }}
          </h1>
          <p class="break-all text-muted-foreground">@{{ profile.username }}</p>
        </div>
        <img
          v-if="profile.avatar"
          :src="profile.avatar"
          :alt="profile.username"
          class="h-24 w-24 shrink-0 rounded-full object-cover"
        />
        <div
          v-else
          class="flex h-24 w-24 shrink-0 items-center justify-center rounded-full bg-status-success-mark text-2xl font-bold text-[var(--color-background)]"
        >
          {{ (profile.name || profile.username).charAt(0).toUpperCase() }}
        </div>
      </div>

      <!-- Bio and Meta -->
      <div class="rounded-lg border bg-card p-6 space-y-4 break-words">
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
              class="break-all hover:text-primary"
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
            <Trophy class="h-5 w-5 text-status-success-mark" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('profile.stats.globalRank')"
          :value="profile.globalRank ?? '-'"
          :subtitle="t('profile.stats.basedOnSolved')"
          color="blue"
        >
          <template #icon>
            <Target class="h-5 w-5 text-primary" />
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
            <BarChart3 class="h-5 w-5 text-status-special-mark" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.stats.submissions')"
          :value="profile.submissionCount || 0"
          :subtitle="t('profile.stats.totalSubmissions')"
          color="orange"
        >
          <template #icon>
            <Flame class="h-5 w-5 text-status-warning-mark" />
          </template>
        </StatsCard>
      </div>

      <!-- Difficulty Progress -->
      <div v-if="stats" class="rounded-lg border bg-card p-6">
        <h2 class="mb-4 text-lg font-semibold">
          {{ t("profile.progress.title") }}
        </h2>
        <div class="space-y-4">
          <!-- Easy -->
          <div class="space-y-2">
            <div class="flex items-center justify-between text-sm">
              <span class="font-medium text-foreground">{{
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
              class="h-2 [&>div]:bg-status-success-mark"
            />
          </div>

          <!-- Medium -->
          <div class="space-y-2">
            <div class="flex items-center justify-between text-sm">
              <span class="font-medium text-foreground">{{
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
              class="h-2 [&>div]:bg-status-warning-mark"
            />
          </div>

          <!-- Hard -->
          <div class="space-y-2">
            <div class="flex items-center justify-between text-sm">
              <span class="font-medium text-foreground">{{
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
              class="h-2 [&>div]:bg-status-error-mark"
            />
          </div>
        </div>
      </div>

      <!-- Activity Heatmap & Skills -->
      <div v-if="stats || skills" class="grid min-w-0 gap-6 lg:grid-cols-2">
        <!-- Heatmap -->
        <div v-if="stats" class="min-w-0 rounded-lg border bg-card p-6">
          <h2 class="mb-4 text-lg font-semibold">
            {{ t("personal.dashboard.heatmap.title") }}
          </h2>
          <ActivityHeatmap v-if="stats.heatmap" :data="stats.heatmap" />
        </div>

        <!-- Skills Radar -->
        <div v-if="skills" class="min-w-0 rounded-lg border bg-card p-6">
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
