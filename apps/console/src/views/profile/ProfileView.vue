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
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Activity,
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
const acceptanceRate = computed(() =>
  Math.min(100, Math.max(0, Number(profile.value?.acceptanceRate ?? 0))),
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
  <div
    class="mx-auto w-full max-w-7xl space-y-6 px-4 py-6 sm:px-6 lg:px-8"
  >
    <Card v-if="error" class="rounded-none border-destructive/40 bg-card">
      <CardContent class="flex flex-col items-center p-8 text-center">
        <p class="text-muted-foreground">{{ error }}</p>
        <Button variant="outline" class="mt-4 rounded-none" @click="$router.go(0)">
          {{ t("personal.social.retry") }}
        </Button>
      </CardContent>
    </Card>

    <template v-else-if="loading">
      <div class="rounded-none border bg-card p-6 shadow-[var(--shadow-float)]">
        <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
          <Skeleton class="h-28 w-28 rounded-none" />
          <div class="flex-1 space-y-3">
            <Skeleton class="h-8 w-56" />
            <Skeleton class="h-4 w-32" />
            <Skeleton class="h-4 w-full max-w-xl" />
          </div>
        </div>
      </div>
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Skeleton v-for="i in 6" :key="i" class="h-28" />
      </div>
      <div class="grid gap-6 lg:grid-cols-2">
        <Skeleton class="h-64" />
        <Skeleton class="h-64" />
      </div>
    </template>

    <template v-else-if="profile">
      <section
        class="relative overflow-hidden rounded-none border bg-card p-5 shadow-[var(--shadow-float)] md:p-7"
      >
        <div
          class="pointer-events-none absolute -right-16 -top-20 h-56 w-56 rounded-full bg-primary/10 blur-3xl"
        ></div>
        <div
          class="pointer-events-none absolute -bottom-24 -left-16 h-56 w-56 rounded-full bg-[var(--status-success-mark)]/10 blur-3xl"
        ></div>

        <div
          class="relative flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between"
        >
          <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
            <div class="relative shrink-0 self-center sm:self-auto">
              <div
                class="absolute -inset-1 bg-primary/20 blur-sm transition-opacity duration-500 hover:opacity-80"
              ></div>
              <img
                v-if="profile.avatar"
                :src="profile.avatar"
                :alt="profile.username"
                class="relative h-28 w-28 rounded-none border-4 border-background object-cover shadow-[var(--shadow-float)]"
              />
              <div
                v-else
                class="relative flex h-28 w-28 items-center justify-center rounded-none border-4 border-background bg-muted text-3xl font-bold text-foreground shadow-[var(--shadow-float)]"
              >
                {{ (profile.name || profile.username).charAt(0).toUpperCase() }}
              </div>
              <span
                class="absolute -bottom-2 -right-2 flex h-9 w-9 items-center justify-center rounded-none border-4 border-background bg-primary text-primary-foreground shadow-[var(--shadow-float)]"
                :title="t('personal.social.globalRank')"
              >
                <Trophy class="h-4 w-4" />
              </span>
            </div>

            <div class="min-w-0 space-y-3 text-center sm:text-left">
              <div>
                <div
                  class="mb-1 flex flex-wrap items-center justify-center gap-2 text-sm sm:justify-start"
                >
                  <span class="font-medium text-muted-foreground"
                    >@{{ profile.username }}</span
                  >
                  <span
                    v-if="profile.globalRank != null"
                    class="inline-flex items-center gap-1 border border-primary/30 bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary"
                  >
                    <Target class="h-3.5 w-3.5" />
                    #{{ profile.globalRank }}
                  </span>
                </div>
                <h1
                  class="text-3xl font-extrabold tracking-tight text-foreground md:text-4xl"
                >
                  {{ profile.name || profile.username }}
                </h1>
              </div>

              <p class="max-w-2xl text-sm leading-relaxed text-muted-foreground">
                {{ profile.bio || t("personal.profile.noBio") }}
              </p>

              <div
                class="flex flex-wrap justify-center gap-x-5 gap-y-2 text-sm text-muted-foreground sm:justify-start"
              >
                <div v-if="profile.joinedAt" class="flex items-center gap-1.5">
                  <Calendar class="h-4 w-4 text-primary/70" />
                  <span>{{
                    t("personal.profile.joinedDate", {
                      date: formatDate(profile.joinedAt),
                    })
                  }}</span>
                </div>
                <div v-if="profile.location" class="flex items-center gap-1.5">
                  <MapPin class="h-4 w-4 text-primary/70" />
                  <span>{{ profile.location }}</span>
                </div>
                <a
                  v-if="profile.website"
                  :href="profile.website"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="flex items-center gap-1.5 text-primary hover:underline underline-offset-4"
                >
                  <LinkIcon class="h-4 w-4" />
                  {{
                    profile.website
                      .replace("https://", "")
                      .replace("http://", "")
                  }}
                </a>
              </div>
            </div>
          </div>

          <FollowButton
            v-if="!isOwnProfile"
            :target-user-id="profile.id"
            :initial-is-following="isFollowing"
            class="self-center lg:self-auto"
          />
        </div>
      </section>

      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
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
          :value="`${acceptanceRate}%`"
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

        <div class="grid gap-4 sm:col-span-2 sm:grid-cols-2">
          <StatsCard
            :title="t('personal.social.followers')"
            :value="profile.followerCount ?? 0"
            :subtitle="t('personal.social.followersSubtitle')"
          >
            <template #icon>
              <Users class="h-5 w-5 text-muted-foreground" />
            </template>
          </StatsCard>

          <StatsCard
            :title="t('personal.social.following')"
            :value="profile.followingCount ?? 0"
            :subtitle="t('personal.social.followingSubtitle')"
          >
            <template #icon>
              <UserPlus class="h-5 w-5 text-muted-foreground" />
            </template>
          </StatsCard>
        </div>
      </div>

      <div class="grid gap-6 lg:grid-cols-[minmax(0,1.35fr)_minmax(20rem,0.85fr)]">
        <Card class="rounded-none bg-card shadow-sm">
          <CardHeader class="border-b border-border/50">
            <CardTitle class="flex items-center gap-2 text-base">
              <Activity class="h-4 w-4 text-primary" />
              {{ t("personal.profile.solvingProgress") }}
            </CardTitle>
            <CardDescription>
              {{
                t("personal.social.submissionsSubtitle", {
                  count: profile.submissionCount ?? 0,
                })
              }}
            </CardDescription>
          </CardHeader>
          <CardContent class="space-y-6 p-5">
            <div class="flex items-end justify-between gap-4">
              <div>
                <div class="text-4xl font-bold tracking-tight">
                  {{ acceptanceRate }}%
                </div>
                <p class="mt-1 text-sm text-muted-foreground">
                  {{ t("personal.social.acceptanceRate") }}
                </p>
              </div>
              <div class="text-right">
                <div class="text-2xl font-semibold">
                  {{ profile.totalSolved ?? 0 }}
                </div>
                <p class="text-xs text-muted-foreground">
                  {{ t("personal.social.problemsSolvedSubtitle") }}
                </p>
              </div>
            </div>

            <div class="space-y-2">
              <div class="flex items-center justify-between text-xs font-medium">
                <span class="text-muted-foreground">
                  {{ t("personal.social.acceptanceRate") }}
                </span>
                <span>{{ acceptanceRate }}%</span>
              </div>
              <div
                class="h-2 overflow-hidden rounded-none bg-muted"
                role="progressbar"
                :aria-label="t('personal.social.acceptanceRate')"
                :aria-valuenow="acceptanceRate"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <div
                  class="h-full bg-[var(--status-success-mark)] transition-all duration-700"
                  :style="{ width: `${acceptanceRate}%` }"
                ></div>
              </div>
            </div>

            <div class="grid gap-3 sm:grid-cols-3">
              <div class="border border-border/60 bg-muted/20 p-3">
                <p class="text-xs text-muted-foreground">
                  {{ t("personal.social.submissions") }}
                </p>
                <p class="mt-1 text-xl font-semibold">
                  {{ profile.submissionCount ?? 0 }}
                </p>
              </div>
              <div class="border border-border/60 bg-muted/20 p-3">
                <p class="text-xs text-muted-foreground">
                  {{ t("personal.social.followers") }}
                </p>
                <p class="mt-1 text-xl font-semibold">
                  {{ profile.followerCount ?? 0 }}
                </p>
              </div>
              <div class="border border-border/60 bg-muted/20 p-3">
                <p class="text-xs text-muted-foreground">
                  {{ t("personal.social.achievements") }}
                </p>
                <p class="mt-1 text-xl font-semibold">
                  {{ profile.achievementCount ?? earnedAchievements.length }}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card class="rounded-none bg-card shadow-sm">
          <CardHeader class="border-b border-border/50">
            <div class="flex items-center justify-between gap-3">
              <CardTitle class="flex items-center gap-2 text-base">
                <Trophy class="h-4 w-4 text-[var(--status-warning-mark)]" />
                {{ t("personal.social.achievements") }}
                <span
                  v-if="earnedAchievements.length > 0"
                  class="border border-primary/30 bg-primary/10 px-1.5 py-0.5 text-xs font-semibold text-primary"
                >
                  {{ earnedAchievements.length }}
                </span>
              </CardTitle>
              <Button
                v-if="earnedAchievements.length > 0"
                variant="link"
                class="h-auto rounded-none p-0 text-xs text-primary"
                @click="$router.push('/personal/achievements')"
              >
                {{ t("personal.social.viewAllAchievements") }}
              </Button>
            </div>
          </CardHeader>
          <CardContent class="p-5">
            <div v-if="earnedAchievements.length > 0" class="space-y-3">
              <AchievementCard
                v-for="achievement in earnedAchievements"
                :key="achievement.achievementId"
                :achievement="achievement"
                :show-progress="false"
              />
            </div>
            <div v-else class="border border-dashed p-8 text-center text-sm text-muted-foreground">
              {{ t("personal.social.noAchievements") }}
            </div>
          </CardContent>
        </Card>
      </div>
    </template>
  </div>
</template>
