<script setup lang="ts">
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  MapPin,
  Link as LinkIcon,
  Twitter,
  Github,
  Calendar,
  Target,
  Zap,
  Activity,
  GitCommit,
  Edit,
  Trophy,
  Flame,
  CheckCircle2,
  ChevronRight,
} from "lucide-vue-next";
import { onMounted, ref, computed } from "vue";
import { RouterLink } from "vue-router";
import ActivityHeatmap from "./components/ActivityHeatmap.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import SkillRadarChart from "./components/SkillRadarChart.vue";
import SubmissionHistoryChart from "./components/SubmissionHistoryChart.vue";
import LearningProgressChart from "./components/LearningProgressChart.vue";
import {
  fetchUserProfile,
  fetchUserStats,
  fetchUserSkills,
  type UserProfile,
  type UserStats,
  type UserSkill,
} from "@/api/user";
import { fetchUserSubmissions } from "@/api/submission";
import type { SubmissionRecord } from "@/types/submission";
import { useAuthStore } from "@/stores/auth";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const authStore = useAuthStore();
const loading = ref(true);
const user = ref<UserProfile | null>(null);
const submissions = ref<SubmissionRecord[]>([]);
const statsData = ref<UserStats | null>(null);
const skillsData = ref<UserSkill[]>([]);
const skillsLoading = ref(false);

// Use authenticated user from auth store
const currentUserId = computed(() => authStore.user?.id);

// Debug: log when component is created
if (import.meta.env.DEV) {
  console.log(
    "[PersonalView] Component created, authStore.user:",
    authStore.user,
  );
}

const stats = computed(() => {
  if (!statsData.value)
    return {
      easy: {
        count: 0,
        total: 0,
        color: "text-[var(--terminal-green)]",
        bg: "bg-[var(--terminal-green)]",
      },
      medium: {
        count: 0,
        total: 0,
        color: "text-[var(--terminal-amber)]",
        bg: "bg-[var(--terminal-amber)]",
      },
      hard: { count: 0, total: 0, color: "text-[var(--terminal-red)]", bg: "bg-[var(--terminal-red)]" },
    };

  const { stats: s } = statsData.value;
  return {
    easy: {
      count: s.Easy.count,
      total: s.Easy.total,
      color: "text-[var(--terminal-green)]",
      bg: "bg-[var(--terminal-green)]",
    },
    medium: {
      count: s.Medium.count,
      total: s.Medium.total,
      color: "text-[var(--terminal-amber)]",
      bg: "bg-[var(--terminal-amber)]",
    },
    hard: {
      count: s.Hard.count,
      total: s.Hard.total,
      color: "text-[var(--terminal-red)]",
      bg: "bg-[var(--terminal-red)]",
    },
  };
});

const recentActivity = computed(() => {
  return submissions.value.slice(0, 5).map((sub) => ({
    action:
      sub.status === "Accepted"
        ? t("personal.stats.solved")
        : t("personal.submissions.attempted"),
    problem: sub.problem?.title || "Unknown Problem",
    problemSlug: sub.problem?.slug || "",
    time: new Date(sub.created_at).toLocaleDateString(),
    status: sub.status,
  }));
});

onMounted(async () => {
  if (import.meta.env.DEV) {
  }

  try {
    const userId = currentUserId.value;
    if (!userId) {
      if (import.meta.env.DEV) {
        console.error("[PersonalView] No user ID found in auth store");
      }
      loading.value = false;
      return;
    }

    if (import.meta.env.DEV) {
    }

    const [userData, userSubmissions, userStats] = await Promise.all([
      fetchUserProfile(userId),
      fetchUserSubmissions(),
      fetchUserStats(userId),
    ]);

    if (import.meta.env.DEV) {
      console.log({
        userData,
        submissionsCount: userSubmissions.length,
        userStats,
      });
    }

    user.value = userData;
    submissions.value = userSubmissions;
    statsData.value = userStats;

    if (import.meta.env.DEV) {
      console.log('User data loaded');
    }

    // Fetch skills data separately (non-blocking)
    skillsLoading.value = true;
    fetchUserSkills(userId)
      .then((data) => {
        skillsData.value = data.skills;
      })
      .catch((e) => {
        console.error("Failed to load skills data", e);
      })
      .finally(() => {
        skillsLoading.value = false;
      });
  } catch (e) {
    console.error("[PersonalView] Failed to load profile data", e);
    if (import.meta.env.DEV) {
      console.error("[PersonalView] Error details:", e);
    }
  } finally {
    loading.value = false;
    if (import.meta.env.DEV) {
      console.log(
        "[PersonalView] onMounted completed, loading:",
        loading.value,
        "user:",
        !!user.value,
      );
    }
  }
});
</script>

<template>
  <div v-if="loading" class="flex h-[60vh] items-center justify-center">
    <div class="flex flex-col items-center gap-2">
      <div
        class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
      <p class="text-sm text-muted-foreground animate-pulse">
        {{ t("personal.profile.loadingProfile") }}
      </p>
    </div>
  </div>
  <div v-else-if="!user" class="flex h-[60vh] items-center justify-center">
    <Card class="w-full max-w-md border-dashed rounded-none">
      <CardContent class="flex flex-col items-center py-10 text-center">
        <div class="mb-4 rounded-full bg-muted p-3 text-muted-foreground">
          <Activity class="h-10 w-10" />
        </div>
        <h3 class="text-xl font-semibold">
          {{ t("personal.profile.authenticationRequired") }}
        </h3>
        <p class="mb-6 mt-2 text-sm text-muted-foreground">
          {{ t("personal.profile.loginToView") }}
        </p>
        <Button as-child class="rounded-full px-6 font-bold">
          <RouterLink to="/login">{{
            t("personal.profile.signIn")
          }}</RouterLink>
        </Button>
      </CardContent>
    </Card>
  </div>
  <PersonalPageShell v-else-if="user">
    <!-- Hero Section -->
    <div
      class="relative overflow-hidden rounded-none border bg-card p-6 md:p-10 shadow-[var(--shadow-float)]"
    >
      <div
        class="absolute right-0 top-0 h-32 w-32 -translate-y-8 translate-x-8 rounded-full bg-primary/10 blur-3xl"
      ></div>
      <div
        class="absolute bottom-0 left-0 h-32 w-32 translate-y-8 -translate-x-8 rounded-full bg-primary/5 blur-3xl"
      ></div>

      <div class="relative flex flex-col gap-8 md:flex-row md:items-center">
        <div class="shrink-0 flex justify-center md:block">
          <div class="relative group">
            <div
              class="absolute -inset-1 rounded-full bg-primary opacity-20 blur-sm group-hover:opacity-40 transition duration-500"
            ></div>
            <Avatar
              class="h-32 w-32 border-4 border-background shadow-[var(--shadow-float)] relative"
            >
              <AvatarImage :src="user.avatar" :alt="user.name" />
              <AvatarFallback class="text-2xl font-bold bg-muted">{{
                user.username.substring(0, 2).toUpperCase()
              }}</AvatarFallback>
            </Avatar>
            <div
              class="absolute -bottom-1 -right-1 flex h-9 w-9 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-[var(--shadow-float)] border-4 border-background"
              :title="t('personal.profile.proMember')"
            >
              <Zap class="h-4 w-4 fill-current" />
            </div>
          </div>
        </div>

        <div class="flex flex-1 flex-col space-y-4 text-center md:text-left">
          <div
            class="flex flex-col md:flex-row md:items-center justify-between gap-4"
          >
            <div class="space-y-1">
              <h1
                class="text-3xl md:text-4xl font-extrabold tracking-tighter text-foreground"
              >
                {{ user.name || user.username }}
              </h1>
              <div
                class="flex items-center justify-center md:justify-start gap-2"
              >
                <span class="text-lg font-medium text-muted-foreground"
                  >@{{ user.username }}</span
                >
                <Badge
                  variant="secondary"
                  class="rounded-full px-2 py-0 h-5 text-[10px] font-semibold uppercase tracking-wider"
                >
                  {{ t("personal.profile.proBadge") }}
                </Badge>
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              class="gap-2 self-center md:self-start rounded-full px-5 h-10 border-muted-foreground/20 hover:bg-muted"
              as-child
            >
              <RouterLink to="/personal/account">
                <Edit class="h-4 w-4" />
                {{ t("personal.profile.editProfile") }}
              </RouterLink>
            </Button>
          </div>

          <p class="max-w-2xl text-base leading-relaxed text-muted-foreground">
            {{ user.bio || t("personal.profile.noBio") }}
          </p>

          <div
            class="flex flex-wrap justify-center md:justify-start gap-x-6 gap-y-3 text-sm font-medium"
          >
            <div
              class="flex items-center gap-1.5 text-muted-foreground"
              v-if="user.location"
            >
              <MapPin class="h-4 w-4 text-primary/70" />
              <span>{{ user.location }}</span>
            </div>
            <div class="flex items-center gap-1.5" v-if="user.website">
              <LinkIcon class="h-4 w-4 text-primary/70" />
              <a
                :href="user.website"
                class="text-primary hover:underline transition-all underline-offset-4"
                target="_blank"
                rel="noopener noreferrer"
                >{{ user.website.replace(/^https?:\/\//, "") }}</a
              >
            </div>
            <div
              class="flex items-center gap-1.5 text-muted-foreground"
              v-if="user.github"
            >
              <Github class="h-4 w-4 text-primary/70" />
              <span>{{ user.github }}</span>
            </div>
            <div
              class="flex items-center gap-1.5 text-muted-foreground"
              v-if="user.twitter"
            >
              <Twitter class="h-4 w-4 text-primary/70" />
              <span>{{ user.twitter }}</span>
            </div>
            <div class="flex items-center gap-1.5 text-muted-foreground">
              <Calendar class="h-4 w-4 text-primary/70" />
              <span>{{
                t("personal.profile.joinedDate", {
                  date: user.joined_at
                    ? new Date(user.joined_at).toLocaleDateString(undefined, {
                        month: "long",
                        year: "numeric",
                      })
                    : "Recently",
                })
              }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Stats Dashboard -->
    <div class="grid gap-6 lg:grid-cols-12">
      <!-- Left & Center Columns: Stats & Progress -->
      <div class="space-y-6 lg:col-span-8">
        <!-- Key Metrics Row -->
        <div class="grid gap-4 sm:grid-cols-3">
          <Card class="relative overflow-hidden group rounded-none">
            <div
              class="absolute -right-2 -top-2 h-16 w-16 rounded-full bg-[var(--accent-electric)]/5 group-hover:scale-150 transition-transform duration-500"
            ></div>
            <CardHeader
              class="pb-2 space-y-0 flex flex-row items-center justify-between"
            >
              <CardTitle
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.profile.globalRank") }}</CardTitle
              >
              <Trophy class="h-4 w-4 text-[var(--accent-electric)]" />
            </CardHeader>
            <CardContent>
              <div class="text-3xl font-bold tracking-tight">
                #{{ (user.rank || 12403).toLocaleString() }}
              </div>
              <div class="mt-2 flex items-center gap-2">
                <Badge
                  variant="secondary"
                  class="bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/10 rounded-none px-1.5 font-bold"
                >
                  DIAMOND III
                </Badge>
                <span
                  class="text-[10px] text-muted-foreground font-medium uppercase"
                  >{{
                    t("personal.stats.topPercent", { percent: "0.5" })
                  }}</span
                >
              </div>
            </CardContent>
          </Card>

          <Card class="relative overflow-hidden group rounded-none">
            <div
              class="absolute -right-2 -top-2 h-16 w-16 rounded-full bg-[var(--terminal-green)]/5 group-hover:scale-150 transition-transform duration-500"
            ></div>
            <CardHeader
              class="pb-2 space-y-0 flex flex-row items-center justify-between"
            >
              <CardTitle
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.profile.solved") }}</CardTitle
              >
              <CheckCircle2 class="h-4 w-4 text-[var(--terminal-green)]" />
            </CardHeader>
            <CardContent>
              <div class="text-3xl font-bold tracking-tight">
                {{ statsData?.totalSolved || 0 }}
              </div>
              <div class="mt-2 text-[11px] text-muted-foreground font-medium">
                {{ t("personal.profile.totalProblems") }}
              </div>
            </CardContent>
          </Card>

          <Card class="relative overflow-hidden group rounded-none">
            <div
              class="absolute -right-2 -top-2 h-16 w-16 rounded-full bg-[var(--terminal-amber)]/5 group-hover:scale-150 transition-transform duration-500"
            ></div>
            <CardHeader
              class="pb-2 space-y-0 flex flex-row items-center justify-between"
            >
              <CardTitle
                class="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >{{ t("personal.profile.streak") }}</CardTitle
              >
              <Flame class="h-4 w-4 text-[var(--terminal-amber)]" />
            </CardHeader>
            <CardContent>
              <div class="text-3xl font-bold tracking-tight">
                {{ statsData?.streak || 0 }}
              </div>
              <div class="mt-2 flex gap-1">
                <div
                  v-for="i in 7"
                  :key="i"
                  class="h-1.5 flex-1 rounded-full bg-muted overflow-hidden"
                >
                  <div
                    v-if="i <= (statsData?.streak || 0) % 7"
                    class="h-full bg-[var(--terminal-amber)] shadow-[0_0_8px_oklch(0.7_0.15_55)]"
                  ></div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <!-- Detailed Problem Stats -->
        <Card class="border-none shadow-none bg-muted/30 rounded-none">
          <CardHeader class="pb-4">
            <div class="flex items-center justify-between">
              <CardTitle class="text-lg font-bold flex items-center gap-2">
                <Target class="h-5 w-5 text-primary" />
                {{ t("personal.profile.solvingProgress") }}
              </CardTitle>
              <div class="text-xs font-bold text-muted-foreground">
                {{ t("personal.profile.overallProgress") }}
                {{
                  (
                    ((statsData?.totalSolved || 0) /
                      ((statsData?.stats?.Easy.total || 0) +
                        (statsData?.stats?.Medium.total || 0) +
                        (statsData?.stats?.Hard.total || 0) || 1)) *
                    100
                  ).toFixed(1)
                }}%
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div class="space-y-6">
              <div
                v-for="(stat, difficulty) in stats"
                :key="difficulty"
                class="space-y-2"
              >
                <div class="flex items-end justify-between">
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-bold capitalize">{{
                      t(`personal.stats.${difficulty}`)
                    }}</span>
                    <Badge
                      variant="outline"
                      class="text-[10px] h-4 px-1 border-muted-foreground/30 text-muted-foreground"
                    >
                      {{ stat.count }} / {{ stat.total }}
                    </Badge>
                  </div>
                  <span class="text-xs font-black" :class="stat.color">
                    {{ ((stat.count / (stat.total || 1)) * 100).toFixed(1) }}%
                  </span>
                </div>
                <div
                  class="h-3 w-full overflow-hidden rounded-full bg-muted shadow-inner"
                >
                  <div
                    class="h-full transition-all duration-1000 ease-out"
                    :class="stat.bg"
                    :style="{
                      width: (stat.count / (stat.total || 1)) * 100 + '%',
                    }"
                  ></div>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- Activity Heatmap -->
        <Card class="rounded-none">
          <CardHeader class="pb-2">
            <CardTitle class="text-lg font-bold flex items-center gap-2">
              <Activity class="h-5 w-5 text-primary" />
              {{ t("personal.profile.heatmapTitle") }}
            </CardTitle>
            <CardDescription>
              {{ t("personal.profile.heatmapSubtitle") }}
            </CardDescription>
          </CardHeader>
          <CardContent class="pt-2">
            <ActivityHeatmap :data="statsData?.heatmap" />
          </CardContent>
        </Card>

        <!-- Skill Radar Chart -->
        <Card class="rounded-none">
          <CardHeader class="pb-2">
            <CardTitle class="text-lg font-bold flex items-center gap-2">
              <Target class="h-5 w-5 text-primary" />
              {{ t("personal.skills.title") }}
            </CardTitle>
            <CardDescription>
              {{ t("personal.skills.subtitle") }}
            </CardDescription>
          </CardHeader>
          <CardContent class="pt-2">
            <SkillRadarChart :skills="skillsData" :loading="skillsLoading" />
          </CardContent>
        </Card>

        <!-- Submission History Chart -->
        <Card class="rounded-none">
          <CardHeader class="pb-2">
            <CardTitle class="text-lg font-bold flex items-center gap-2">
              <GitCommit class="h-5 w-5 text-primary" />
              {{ t("personal.history.title") }}
            </CardTitle>
            <CardDescription>
              {{ t("personal.history.subtitle") }}
            </CardDescription>
          </CardHeader>
          <CardContent class="pt-2">
            <SubmissionHistoryChart />
          </CardContent>
        </Card>

        <!-- Learning Progress Chart -->
        <Card class="rounded-none">
          <CardHeader class="pb-2">
            <CardTitle class="text-lg font-bold flex items-center gap-2">
              <Target class="h-5 w-5 text-primary" />
              {{ t("personal.learning.title") }}
            </CardTitle>
            <CardDescription>
              {{ t("personal.learning.subtitle") }}
            </CardDescription>
          </CardHeader>
          <CardContent class="pt-2">
            <LearningProgressChart />
          </CardContent>
        </Card>
      </div>

      <!-- Right Column: Recent Activity & Badges -->
      <div class="space-y-6 lg:col-span-4">
        <Card class="h-full border-muted/50 rounded-none">
          <CardHeader class="pb-4 border-b bg-muted/20">
            <CardTitle class="text-base font-bold flex items-center gap-2">
              <GitCommit class="h-4 w-4 text-primary" />
              {{ t("personal.profile.recentActivity") }}
            </CardTitle>
          </CardHeader>
          <CardContent class="p-0">
            <div
              v-if="recentActivity.length > 0"
              class="divide-y divide-border/50"
            >
              <div
                v-for="(item, index) in recentActivity"
                :key="index"
                class="group relative flex items-start gap-4 p-4 hover:bg-muted/40 transition-colors"
              >
                <div
                  class="flex h-10 w-10 shrink-0 items-center justify-center rounded-none border bg-background group-hover:border-primary/50 transition-colors"
                >
                  <div
                    class="h-3 w-3 rounded-full animate-pulse shadow-[0_0_8px_oklch(0.7_0.15_160)]"
                    :class="
                      item.status === 'Accepted'
                        ? 'bg-[var(--terminal-green)]'
                        : 'bg-[var(--terminal-red)] shadow-[0_0_8px_oklch(0.65_0.2_25)]'
                    "
                  ></div>
                </div>
                <div class="flex flex-1 flex-col gap-0.5 min-w-0">
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-sm font-bold truncate">
                      {{ item.action }}
                      <RouterLink
                        :to="`/problems/${item.problemSlug}`"
                        class="text-primary hover:underline underline-offset-2 decoration-1"
                      >
                        {{ item.problem }}
                      </RouterLink>
                    </span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span
                      class="text-[11px] font-medium text-muted-foreground"
                      >{{ item.time }}</span
                    >
                    <Badge
                      variant="outline"
                      class="text-[9px] h-4 px-1 rounded-none font-bold uppercase tracking-tighter"
                      :class="
                        item.status === 'Accepted'
                          ? 'border-[var(--terminal-green)]/50 text-[var(--terminal-green)]'
                          : 'border-[var(--terminal-red)]/50 text-[var(--terminal-red)]'
                      "
                    >
                      {{ item.status }}
                    </Badge>
                  </div>
                </div>
              </div>
            </div>
            <div
              v-else
              class="flex flex-col items-center justify-center py-12 text-center text-muted-foreground"
            >
              <Activity class="h-8 w-8 mb-2 opacity-20" />
              <p class="text-sm">
                {{ t("personal.submissions.noSubmissions") }}
              </p>
            </div>
          </CardContent>
          <div class="p-4 border-t bg-muted/5">
            <Button
              variant="ghost"
              size="sm"
              class="w-full text-xs font-bold text-muted-foreground hover:text-primary gap-1"
              as-child
            >
              <RouterLink to="/personal/submissions">
                {{ t("personal.profile.viewAllSubmissions") }}
                <ChevronRight class="h-3 w-3" />
              </RouterLink>
            </Button>
          </div>
        </Card>
      </div>
    </div>
  </PersonalPageShell>
</template>
