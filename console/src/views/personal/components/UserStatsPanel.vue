<script setup lang="ts">
import { computed } from "vue";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Trophy,
  CheckCircle2,
  Flame,
  Target,
  Activity,
  GitCommit,
  ChevronRight,
} from "lucide-vue-next";
import { RouterLink } from "vue-router";
import { useI18n } from "vue-i18n";
import ActivityHeatmap from "./ActivityHeatmap.vue";
import SkillRadarChart from "./SkillRadarChart.vue";
import SubmissionHistoryChart from "./SubmissionHistoryChart.vue";
import LearningProgressChart from "./LearningProgressChart.vue";
import type { UserStats, UserSkill } from "@/types/userStats";
import type { SubmissionRecord } from "@/types/submission";

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
        color: "text-[var(--terminal-green)]",
        bg: "bg-[var(--terminal-green)]",
      },
      medium: {
        count: 0,
        total: 0,
        color: "text-[var(--terminal-amber)]",
        bg: "bg-[var(--terminal-amber)]",
      },
      hard: {
        count: 0,
        total: 0,
        color: "text-[var(--terminal-red)]",
        bg: "bg-[var(--terminal-red)]",
      },
    };

  const { stats: s } = props.statsData;
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

const getSubmissionLabel = (status: string): string => {
  const normalized = status.toUpperCase().replace(/\s+/g, "_");
  const map: Record<string, string> = {
    ACCEPTED: "submission.status.accepted",
    WRONG_ANSWER: "submission.status.wrongAnswer",
    TIME_LIMIT_EXCEEDED: "submission.status.timeLimitExceeded",
    MEMORY_LIMIT_EXCEEDED: "submission.status.memoryLimitExceeded",
    OUTPUT_LIMIT_EXCEEDED: "submission.status.outputLimitExceeded",
    RUNTIME_ERROR: "submission.status.runtimeError",
    COMPILE_ERROR: "submission.status.compileError",
    PRESENTATION_ERROR: "submission.status.presentationError",
    SYSTEM_ERROR: "submission.status.systemError",
    JUDGING: "submission.status.judging",
    PENDING: "submission.status.pending",
  };
  const key = map[normalized];
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
    time: new Date(sub.created_at).toLocaleDateString(),
    status: sub.status,
  }));
});
</script>

<template>
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
              #{{ (userRank || 12403).toLocaleString() }}
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
                >{{ t("personal.stats.topPercent", { percent: "0.5" }) }}</span
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
          <CardDescription>{{
            t("personal.profile.heatmapSubtitle")
          }}</CardDescription>
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
          <CardDescription>{{ t("personal.skills.subtitle") }}</CardDescription>
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
          <CardDescription>{{
            t("personal.history.subtitle")
          }}</CardDescription>
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
          <CardDescription>{{
            t("personal.learning.subtitle")
          }}</CardDescription>
        </CardHeader>
        <CardContent class="pt-2">
          <LearningProgressChart />
        </CardContent>
      </Card>
    </div>

    <!-- Right Column: Recent Activity -->
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
                  <span class="text-[11px] font-medium text-muted-foreground">{{
                    item.time
                  }}</span>
                  <Badge
                    variant="outline"
                    class="text-[9px] h-4 px-1 rounded-none font-bold uppercase tracking-tighter"
                    :class="
                      item.status === 'Accepted'
                        ? 'border-[var(--terminal-green)]/50 text-[var(--terminal-green)]'
                        : 'border-[var(--terminal-red)]/50 text-[var(--terminal-red)]'
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
</template>
