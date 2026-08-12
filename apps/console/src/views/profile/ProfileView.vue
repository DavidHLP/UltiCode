<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { formatDate } from "@/utils/datetime";
import { useAuthStore } from "@/stores/auth";
import { apiGet } from "@/utils/request";
import { fetchProfileByUsername, type ProfileData } from "@/api/user";
import { useFollowStatus } from "@/composables/useFollowStatus";
import type { AchievementProgress } from "@/types/achievement";
import FollowButton from "@/components/follow/FollowButton.vue";
import StatsCard from "@/components/dashboard/StatsCard.vue";
import AchievementCard from "@/components/achievement/AchievementCard.vue";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  Trophy,
  Target,
  BarChart3,
  Flame,
  Users,
  UserPlus,
  Calendar,
  MapPin,
  Link as LinkIcon,
} from "lucide-vue-next";

async function fetchUserAchievementsById(
  userId: string,
): Promise<AchievementProgress[]> {
  return apiGet<AchievementProgress[]>(`/achievements/user/${userId}`);
}

const route = useRoute();
const { t } = useI18n();
const authStore = useAuthStore();

const loading = ref(true);
const error = ref<string | null>(null);
const profile = ref<ProfileData | null>(null);
const userAchievements = ref<AchievementProgress[]>([]);

const username = computed(() => route.params.username as string);
const currentUserId = computed(() => authStore.user?.id);
const isOwnProfile = computed(() => profile.value?.id === currentUserId.value);
const earnedAchievements = computed(() =>
  userAchievements.value.filter((a) => a.earned).slice(0, 5),
);

// Follow status — initialized after profile loads so we have userId
const followStatusUserId = ref<string | null>(null);
const { isFollowing, fetchStatus } = useFollowStatus(
  followStatusUserId.value ?? "",
  false,
);

watch(
  () => profile.value?.id,
  (newId) => {
    followStatusUserId.value = newId ?? null;
  },
);

onMounted(async () => {
  try {
    loading.value = true;
    error.value = null;

    const profileData = await fetchProfileByUsername(username.value);
    profile.value = profileData;

    const [achievements] = await Promise.all([
      fetchUserAchievementsById(profileData.id),
      !isOwnProfile.value ? fetchStatus() : Promise.resolve(),
    ]);
    userAchievements.value = achievements;
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : t("personal.social.loadError");
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
      <Button variant="outline" class="mt-4" @click="() => $router.go(0)">
        {{ t("personal.social.retry") }}
      </Button>
    </div>

    <!-- Loading State -->
    <template v-else-if="loading">
      <div class="flex items-start justify-between">
        <div class="space-y-2">
          <Skeleton class="h-8 w-48" />
          <Skeleton class="h-4 w-32" />
          <Skeleton class="mt-2 h-4 w-64" />
        </div>
        <Skeleton class="h-24 w-24 rounded-full" />
      </div>
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Skeleton v-for="i in 6" :key="i" class="h-24" />
      </div>
    </template>

    <!-- Profile Content -->
    <template v-else-if="profile">
      <!-- Header -->
      <div class="flex items-start justify-between">
        <div class="flex-1 space-y-3">
          <div>
            <h1 class="text-2xl font-bold tracking-tight">
              {{ profile.name || profile.username }}
            </h1>
            <p class="text-muted-foreground">@{{ profile.username }}</p>
          </div>
          <p v-if="profile.bio" class="text-sm text-muted-foreground">
            {{ profile.bio }}
          </p>
          <div class="flex flex-wrap gap-4 text-sm text-muted-foreground">
            <div v-if="profile.joinedAt" class="flex items-center gap-1">
              <Calendar class="h-4 w-4" />
              <span>{{
                t("personal.profile.joinedDate", {
                  date: formatDate(profile.joinedAt),
                })
              }}</span>
            </div>
            <div v-if="profile.location" class="flex items-center gap-1">
              <MapPin class="h-4 w-4" />
              <span>{{ profile.location }}</span>
            </div>
            <div v-if="profile.website" class="flex items-center gap-1">
              <LinkIcon class="h-4 w-4" />
              <a
                :href="profile.website"
                target="_blank"
                rel="noopener"
                class="hover:text-[var(--primary)]"
              >
                {{ profile.website }}
              </a>
            </div>
          </div>
        </div>

        <!-- Avatar + Follow Button -->
        <div class="ml-6 flex flex-col items-center gap-3">
          <img
            v-if="profile.avatar"
            :src="profile.avatar"
            :alt="profile.username"
            class="h-24 w-24 rounded-full object-cover"
          />
          <div
            v-else
            class="flex h-24 w-24 items-center justify-center rounded-full bg-[var(--status-success-mark)] text-2xl font-bold text-[var(--background)]"
          >
            {{ (profile.name || profile.username).charAt(0).toUpperCase() }}
          </div>
          <FollowButton
            v-if="!isOwnProfile"
            :target-user-id="profile.id"
            :initial-is-following="isFollowing"
          />
        </div>
      </div>

      <!-- Stats Grid -->
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatsCard
          :title="t('personal.social.problemsSolved')"
          :value="profile.totalSolved ?? 0"
          :subtitle="t('personal.social.problemsSolvedSubtitle')"
          color="green"
        >
          <template #icon>
            <Trophy class="h-5 w-5 text-[var(--status-success-mark)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.social.globalRank')"
          :value="profile.globalRank ?? '-'"
          :subtitle="t('personal.social.globalRankSubtitle')"
          color="blue"
        >
          <template #icon>
            <Target class="h-5 w-5 text-[var(--accent-primary)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.stats.acceptanceRate')"
          :value="`${profile.acceptanceRate ?? 0}%`"
          :subtitle="
            t('personal.social.submissionsSubtitle', {
              count: profile.submissionCount ?? 0,
            })
          "
          color="purple"
        >
          <template #icon>
            <BarChart3 class="h-5 w-5 text-[var(--status-special-mark)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.stats.submissions')"
          :value="profile.submissionCount ?? 0"
          :subtitle="t('personal.social.totalSubmissions')"
          color="orange"
        >
          <template #icon>
            <Flame class="h-5 w-5 text-[var(--status-warning-mark)]" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.social.followers')"
          :value="profile.followerCount ?? 0"
          :subtitle="t('personal.social.followersSubtitle')"
          color="default"
        >
          <template #icon>
            <Users class="h-5 w-5 text-muted-foreground" />
          </template>
        </StatsCard>

        <StatsCard
          :title="t('personal.social.following')"
          :value="profile.followingCount ?? 0"
          :subtitle="t('personal.social.followingSubtitle')"
          color="default"
        >
          <template #icon>
            <UserPlus class="h-5 w-5 text-muted-foreground" />
          </template>
        </StatsCard>
      </div>

      <!-- Achievements Section -->
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold">
            {{ t("personal.social.achievements") }}
            <span
              v-if="earnedAchievements.length > 0"
              class="ml-2 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
            >
              {{ earnedAchievements.length }}
            </span>
          </h2>
          <Button
            v-if="earnedAchievements.length > 0"
            variant="link"
            class="text-primary"
            @click="$router.push('/personal/achievements')"
          >
            {{ t("personal.social.viewAllAchievements") }}
          </Button>
        </div>

        <div
          v-if="earnedAchievements.length > 0"
          class="grid gap-4 lg:grid-cols-2"
        >
          <AchievementCard
            v-for="achievement in earnedAchievements"
            :key="achievement.achievementId"
            :achievement="achievement"
            :show-progress="false"
          />
        </div>

        <div
          v-else
          class="rounded-none border bg-card p-6 text-center text-muted-foreground"
        >
          {{ t("personal.social.noAchievements") }}
        </div>
      </div>
    </template>
  </div>
</template>
