<script setup lang="ts">
import { computed } from "vue";
import { Badge } from "@ulticode/design-system/ui/badge";
import { formatDate } from "@/utils/datetime";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Trophy,
  CheckCircle2,
  Flame,
  Target,
  Activity,
  GitCommit,
  ChevronRight,
} from "@lucide/vue";
import { RouterLink } from "vue-router";
import { useI18n } from "vue-i18n";
import ActivityHeatmap from "./ActivityHeatmap.vue";
import SkillRadarChart from "./SkillRadarChart.vue";
import SubmissionHistoryChart from "./SubmissionHistoryChart.vue";
import LearningProgressChart from "./LearningProgressChart.vue";
import type { UserStats, UserSkill } from "@/types/userStats";
import type { SubmissionRecord } from "@/types/submission";
import { getStatusLabelI18nKey } from "@ulticode/submission-status";

const props = defineProps<{
  statsData: UserStats | null;
  submissions: SubmissionRecord[];
  skillsData: UserSkill[];
  skillsLoading: boolean;
  userRank?: number;
}>();

const { t } = useI18n();

const stats = computed(() => {
  if (!props.statsData)
    return {
      easy: {
        count: 0,
        total: 0,
        color: "text-foreground",
        bg: "bg-status-success-mark",
      },
      medium: {
        count: 0,
        total: 0,
        color: "text-foreground",
        bg: "bg-status-warning-mark",
      },
      hard: {
        count: 0,
        total: 0,
        color: "text-foreground",
        bg: "bg-status-error-mark",
      },
    };

  const { stats: s } = props.statsData;
  return {
    easy: {
      count: s.Easy.count,
      total: s.Easy.total,
      color: "text-foreground",
      bg: "bg-status-success-mark",
    },
    medium: {
      count: s.Medium.count,
      total: s.Medium.total,
      color: "text-foreground",
      bg: "bg-status-warning-mark",
    },
    hard: {
      count: s.Hard.count,
      total: s.Hard.total,
      color: "text-foreground",
      bg: "bg-status-error-mark",
    },
  };
});

const getSubmissionLabel = (status: string): string => {
  const key = getStatusLabelI18nKey(status);
  return key ? t(key) : status;
};

const recentActivity = computed(() => {
  return props.submissions.slice(0, 5).map((sub) => ({
    action:
      sub.status === "Accepted"
        ? t("personal.stats.solved")
        : t("personal.submissions.attempted"),
    problem: sub.problem?.title || "Unknown Problem",
    problemSlug: sub.problem?.slug || "",
    time: formatDate(sub.created_at),
    status: sub.status,
  }));
});
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-4">
    <!-- Left & Center Columns: Stats & Progress -->
    <div class="space-y-4 lg:col-span-3">
      <!-- Key Metrics Row -->
      <div class="grid gap-4 sm:grid-cols-3">
        <Card class="relative overflow-hidden group py-3.5 gap-2">
          <div
            class="absolute -right-2 -top-2 h-12 w-12 rounded-lg bg-primary/5 group-hover:scale-150 transition-transform duration-500"
          ></div>
          <CardHeader
            class="pb-1 px-4 space-y-0 flex flex-row items-center justify-between"
          >
            <CardTitle as="h3">{{
              t("personal.profile.globalRank")
            }}</CardTitle>
            <Trophy class="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent class="px-4 pb-1">
            <div class="text-3xl font-bold tracking-tight text-foreground">
              #{{ (userRank || 12403).toLocaleString() }}
            </div>
            <div class="mt-1 flex items-center gap-1.5">
              <Badge variant="secondary" class="px-1 py-0 h-4">
                DIAMOND III
              </Badge>
              <span
                class="text-xs text-muted-foreground font-medium uppercase"
                >{{ t("personal.stats.topPercent", { percent: "0.5" }) }}</span
              >
            </div>
          </CardContent>
        </Card>

        <Card class="relative overflow-hidden group py-3.5 gap-2">
          <div
            class="absolute -right-2 -top-2 h-12 w-12 rounded-lg bg-status-success-mark/5 group-hover:scale-150 transition-transform duration-500"
          ></div>
          <CardHeader
            class="pb-1 px-4 space-y-0 flex flex-row items-center justify-between"
          >
            <CardTitle as="h3">{{ t("personal.profile.solved") }}</CardTitle>
            <CheckCircle2 class="h-4 w-4 text-status-success-mark" />
          </CardHeader>
          <CardContent class="px-4 pb-1">
            <div class="text-3xl font-bold tracking-tight text-foreground">
              {{ statsData?.totalSolved || 0 }}
            </div>
            <div class="mt-1 text-xs text-muted-foreground font-medium">
              {{ t("personal.profile.totalProblems") }}
            </div>
          </CardContent>
        </Card>

        <Card class="relative overflow-hidden group py-3.5 gap-2">
          <div
            class="absolute -right-2 -top-2 h-12 w-12 rounded-lg bg-status-warning-mark/5 group-hover:scale-150 transition-transform duration-500"
          ></div>
          <CardHeader
            class="pb-1 px-4 space-y-0 flex flex-row items-center justify-between"
          >
            <CardTitle as="h3">{{ t("personal.profile.streak") }}</CardTitle>
            <Flame class="h-4 w-4 text-status-warning-mark" />
          </CardHeader>
          <CardContent class="px-4 pb-1">
            <div class="text-3xl font-bold tracking-tight text-foreground">
              {{ statsData?.streak || 0 }}
            </div>
            <div class="mt-2 flex gap-0.5">
              <div
                v-for="i in 7"
                :key="i"
                class="h-1 flex-1 rounded-lg bg-muted overflow-hidden"
              >
                <div
                  v-if="i <= (statsData?.streak || 0) % 7"
                  class="h-full bg-status-warning-mark shadow-[0_0_8px_color-mix(in_srgb,var(--status-warning-mark)_70%,transparent)]"
                ></div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- Solving Progress & Skill Radar Side-by-Side -->
      <div class="grid gap-4 md:grid-cols-2">
        <!-- Detailed Problem Stats -->
        <Card class="py-3.5 gap-2.5">
          <CardHeader class="pb-2 px-4 mb-2.5">
            <div class="flex items-center justify-between">
              <CardTitle as="h3" class="flex items-center gap-1.5">
                <Target class="h-4 w-4 text-primary/80" />
                {{ t("personal.profile.solvingProgress") }}
              </CardTitle>
              <div class="text-xs font-bold text-muted-foreground/80">
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
          <CardContent class="px-4 pb-2">
            <div class="space-y-3.5">
              <div
                v-for="(stat, difficulty) in stats"
                :key="difficulty"
                class="space-y-1.5"
              >
                <div class="flex items-baseline justify-between text-xs">
                  <div class="flex items-baseline gap-1.5">
                    <span class="font-bold capitalize text-foreground/90">{{
                      t(`personal.stats.${difficulty}`)
                    }}</span>
                    <span class="font-mono text-muted-foreground/80 text-xs">
                      {{ stat.count }} / {{ stat.total }}
                    </span>
                  </div>
                  <span
                    class="font-mono font-black text-xs"
                    :class="stat.color"
                  >
                    {{ ((stat.count / (stat.total || 1)) * 100).toFixed(1) }}%
                  </span>
                </div>
                <div
                  class="h-1.5 w-full overflow-hidden rounded-lg bg-muted shadow-inner"
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

        <!-- Skill Radar Chart -->
        <Card class="py-3.5 gap-2.5">
          <CardHeader class="pb-2 px-4 mb-2.5">
            <CardTitle as="h3" class="flex items-center gap-1.5">
              <Target class="h-4 w-4 text-primary/80" />
              {{ t("personal.skills.title") }}
            </CardTitle>
            <CardDescription class="mt-0.5">{{
              t("personal.skills.subtitle")
            }}</CardDescription>
          </CardHeader>
          <CardContent class="px-4 pb-2">
            <SkillRadarChart :skills="skillsData" :loading="skillsLoading" />
          </CardContent>
        </Card>
      </div>

      <!-- Activity Heatmap -->
      <Card class="py-3.5 gap-2.5">
        <CardHeader class="pb-2 px-4 mb-2.5">
          <CardTitle as="h3" class="flex items-center gap-1.5">
            <Activity class="h-4 w-4 text-primary/80" />
            {{ t("personal.profile.heatmapTitle") }}
          </CardTitle>
          <CardDescription class="mt-0.5">{{
            t("personal.profile.heatmapSubtitle")
          }}</CardDescription>
        </CardHeader>
        <CardContent class="px-4 pb-2">
          <ActivityHeatmap :data="statsData?.heatmap" />
        </CardContent>
      </Card>

      <!-- Submission History & Learning Progress Side-by-Side -->
      <div class="grid gap-4 md:grid-cols-2">
        <!-- Submission History Chart -->
        <Card class="py-3.5 gap-2.5">
          <CardHeader class="pb-2 px-4 mb-2.5">
            <CardTitle as="h3" class="flex items-center gap-1.5">
              <GitCommit class="h-4 w-4 text-primary/80" />
              {{ t("personal.history.title") }}
            </CardTitle>
            <CardDescription class="mt-0.5">{{
              t("personal.history.subtitle")
            }}</CardDescription>
          </CardHeader>
          <CardContent class="px-4 pb-2">
            <SubmissionHistoryChart />
          </CardContent>
        </Card>

        <!-- Learning Progress Chart -->
        <Card class="py-3.5 gap-2.5">
          <CardHeader class="pb-2 px-4 mb-2.5">
            <CardTitle as="h3" class="flex items-center gap-1.5">
              <Target class="h-4 w-4 text-primary/80" />
              {{ t("personal.learning.title") }}
            </CardTitle>
            <CardDescription class="mt-0.5">{{
              t("personal.learning.subtitle")
            }}</CardDescription>
          </CardHeader>
          <CardContent class="px-4 pb-2">
            <LearningProgressChart />
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Right Column: Recent Activity -->
    <div class="space-y-4 lg:col-span-1">
      <Card class="h-full py-3.5 gap-2.5">
        <CardHeader class="pb-2 px-4 mb-2.5">
          <CardTitle as="h3" class="flex items-center gap-1.5">
            <GitCommit class="h-4 w-4 text-primary/80" />
            {{ t("personal.profile.recentActivity") }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 max-h-[380px] overflow-y-auto scrollbar-hide">
          <div
            v-if="recentActivity.length > 0"
            class="divide-y divide-border/50"
          >
            <div
              v-for="(item, index) in recentActivity"
              :key="index"
              class="group relative flex items-start gap-3 p-3 hover:bg-muted/40 transition-colors"
            >
              <div
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border bg-background group-hover:border-primary/50 transition-colors"
              >
                <div
                  class="h-2 w-2 rounded-lg animate-pulse shadow-[0_0_8px_color-mix(in_srgb,var(--status-info-mark)_70%,transparent)]"
                  :class="
                    item.status === 'Accepted'
                      ? 'bg-status-success-mark'
                      : 'bg-status-error-mark shadow-[0_0_8px_color-mix(in_srgb,var(--status-error-mark)_70%,transparent)]'
                  "
                ></div>
              </div>
              <div class="flex flex-1 flex-col gap-0.5 min-w-0 leading-normal">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-xs font-bold truncate">
                    {{ item.action }}
                    <RouterLink
                      :to="`/problems/${item.problemSlug}`"
                      class="text-primary hover:underline underline-offset-2 decoration-1"
                    >
                      {{ item.problem }}
                    </RouterLink>
                  </span>
                </div>
                <div class="flex items-center justify-between gap-2 mt-0.5">
                  <span class="text-xs font-medium text-muted-foreground/75">{{
                    item.time
                  }}</span>
                  <Badge
                    variant="outline"
                    class="h-3.5 px-1"
                    :class="
                      item.status === 'Accepted'
                        ? 'border-status-success-mark/50 text-foreground'
                        : 'border-status-error-mark/50 text-foreground'
                    "
                  >
                    {{ getSubmissionLabel(item.status) }}
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
            <p class="text-sm">{{ t("personal.submissions.noSubmissions") }}</p>
          </div>
        </CardContent>
        <div class="p-3 border-t bg-muted/5">
          <Button variant="ghost" size="sm" class="w-full gap-1" as-child>
            <RouterLink to="/personal/submissions">
              {{ t("personal.profile.viewAllSubmissions") }}
              <ChevronRight class="h-3 w-3" />
            </RouterLink>
          </Button>
        </div>
      </Card>
    </div>
  </div>
</template>
