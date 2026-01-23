<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted, watch } from "vue";
import { useRoute } from "vue-router";
import { useContestStore } from "@/stores/contest";
import { fetchContestRanking, fetchLiveRanking } from "@/api/contest";
import type { ContestRankingEntry } from "@/types/contest";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { toast } from "vue-sonner";
import VirtualContestTimer from "../components/VirtualContestTimer.vue";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Trophy,
  Users,
  Calendar,
  Clock,
  PlayCircle,
  ChevronRight,
  TrendingUp,
  TrendingDown,
  Award,
  Target,
  ArrowLeft,
  Lock,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { formatPenaltyTime } from "@/utils/date";

const route = useRoute();
const contestStore = useContestStore();
const { t } = useI18n();
const contestId = route.params.contestId as string;

const contest = computed(() => contestStore.currentContest);
const rankings = ref<ContestRankingEntry[]>([]);
const loading = computed(() => contestStore.loading);
const registering = ref(false);
const startingVirtual = ref(false);
const statusCountdown = ref("00:00:00");
const statusLabel = ref(t("contest.time.startsIn"));
const statusHint = ref("");
const statusProgress = ref(0);
let statusIntervalId: number | null = null;
let rankingIntervalId: number | null = null;

// Get participation status
const isRegistered = computed(() => contestStore.isRegistered(contestId));

const statusCardClass = computed(() => {
  const status = contest.value?.status;
  if (status === "running") {
    return "bg-gradient-to-br from-rose-600 via-orange-500 to-amber-500";
  }
  if (status === "upcoming") {
    return "bg-gradient-to-br from-emerald-600 via-teal-500 to-cyan-600";
  }
  return "bg-gradient-to-br from-slate-700 to-slate-800";
});

const contestEndTime = computed(() => {
  const value = contest.value;
  if (!value) return "";
  const endTime = value.end_time ?? value.endTime;
  if (endTime) return endTime;
  const startMs = new Date(value.start_time).getTime();
  const durationMinutes = Number(
    value.duration_minutes ?? value.durationMinutes ?? 0,
  );
  if (Number.isNaN(startMs) || !durationMinutes) return "";
  return new Date(startMs + durationMinutes * 60 * 1000).toISOString();
});

onMounted(async () => {
  try {
    await contestStore.loadContestDetail(contestId);
    await Promise.all([
      contestStore.loadParticipationStatus(contestId),
      contestStore.loadVirtualSession(contestId),
    ]);
    await loadRankings();
  } catch (error) {
    console.error("Failed to load contest detail:", error);
  }
});

watch(
  contest,
  (value) => {
    if (!value) return;
    updateStatusTimer();
    if (statusIntervalId !== null) {
      clearInterval(statusIntervalId);
    }
    statusIntervalId = window.setInterval(updateStatusTimer, 1000);

    if (rankingIntervalId !== null) {
      clearInterval(rankingIntervalId);
    }
    if (value.status === "running") {
      rankingIntervalId = window.setInterval(loadRankings, 30000);
    }
  },
  { immediate: true },
);

onUnmounted(() => {
  if (statusIntervalId !== null) {
    clearInterval(statusIntervalId);
  }
  if (rankingIntervalId !== null) {
    clearInterval(rankingIntervalId);
  }
});

// Registration handler
async function handleRegister() {
  registering.value = true;
  try {
    await contestStore.registerForContest(contestId);
  } catch (error: unknown) {
    console.error("Registration failed:", error);
    toast.error(
      getErrorMessage(error, t("contest.messages.registrationFailed")),
    );
  } finally {
    registering.value = false;
  }
}

async function handleUnregister() {
  registering.value = true;
  try {
    await contestStore.unregisterFromContest(contestId);
  } catch (error: unknown) {
    console.error("Unregister failed:", error);
    toast.error(
      getErrorMessage(error, t("contest.detail.unregistrationFailed")),
    );
  } finally {
    registering.value = false;
  }
}

// Virtual contest handler
async function handleStartVirtual() {
  startingVirtual.value = true;
  try {
    await contestStore.startVirtualContest(contestId);
    // Reload participation status
    await contestStore.loadParticipationStatus(contestId);
  } catch (error: unknown) {
    console.error("Failed to start virtual contest:", error);
    toast.error(getErrorMessage(error, t("contest.detail.startVirtualFailed")));
  } finally {
    startingVirtual.value = false;
  }
}

function scrollToSection(sectionId: string) {
  const section = document.getElementById(sectionId);
  if (section) {
    section.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return fallback;
}

function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, totalSeconds);
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  if (days > 0) {
    return `${days}${t("contest.time.days")} ${hours}${t("contest.time.hours")} ${minutes}${t("contest.time.minutes")}`;
  }
  return `${hours.toString().padStart(2, "0")}:${minutes
    .toString()
    .padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
}

function getContestEndTimeMs(): number | null {
  const value = contest.value;
  if (!value) return null;
  const endTime = value.end_time ?? value.endTime;
  if (endTime) {
    const endMs = new Date(endTime).getTime();
    return Number.isNaN(endMs) ? null : endMs;
  }
  const startMs = new Date(value.start_time).getTime();
  if (Number.isNaN(startMs)) return null;
  const durationMinutes = Number(
    value.duration_minutes ?? value.durationMinutes ?? 0,
  );
  if (!durationMinutes) return null;
  return startMs + durationMinutes * 60 * 1000;
}

function updateStatusTimer() {
  const value = contest.value;
  if (!value) return;
  const startMs = new Date(value.start_time).getTime();
  const endMs = getContestEndTimeMs();
  const now = Date.now();

  if (value.status === "upcoming") {
    const remaining = Math.max(0, Math.floor((startMs - now) / 1000));
    statusLabel.value = t("contest.time.startsIn");
    statusCountdown.value = formatCountdown(remaining);
    statusHint.value = isRegistered.value
      ? t("contest.detail.youAreRegistered")
      : t("contest.detail.registrationOpen");
    statusProgress.value = 0;
    return;
  }

  if (value.status === "running") {
    const remaining = Math.max(0, Math.floor(((endMs ?? now) - now) / 1000));
    const total = Math.max(1, Math.floor(((endMs ?? now) - startMs) / 1000));
    const elapsed = Math.min(total, Math.max(0, total - remaining));
    statusLabel.value = t("contest.virtual.timeRemaining");
    statusCountdown.value = formatCountdown(remaining);
    statusHint.value = t("contest.detail.submissionsLive");
    statusProgress.value = Math.min(100, Math.max(0, (elapsed / total) * 100));
    return;
  }

  statusLabel.value = t("contest.detail.ended");
  statusCountdown.value = t("contest.detail.resultsPublished");
  statusHint.value = t("contest.detail.replayHint");
  statusProgress.value = 100;
}

async function loadRankings() {
  if (!contest.value) return;
  try {
    if (contest.value.status === "running") {
      rankings.value = await fetchLiveRanking(contestId);
      return;
    }
    const rankingRes = await fetchContestRanking(contestId);
    rankings.value = rankingRes.items;
  } catch (error) {
    console.error("Failed to load contest rankings:", error);
  }
}

// Format Date Time
function formatDateTime(isoString: string): string {
  const date = new Date(isoString);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}`;
}

// Get Difficulty Color
function getDifficultyColor(difficulty: string): string {
  const colors: Record<string, string> = {
    Easy: "text-green-600",
    Medium: "text-yellow-600",
    Hard: "text-red-600",
  };
  return colors[difficulty] || "text-gray-600";
}

// Get Country Flag Emoji
function getCountryFlag(countryCode: string): string {
  const flags: Record<string, string> = {
    CN: "🇨🇳",
    US: "🇺🇸",
    JP: "🇯🇵",
    KR: "🇰🇷",
    DE: "🇩🇪",
    UK: "🇬🇧",
    FR: "🇫🇷",
    CA: "🇨🇦",
    AU: "🇦🇺",
    SG: "🇸🇬",
  };
  return flags[countryCode] || "🌐";
}
</script>

<template>
  <div
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <div v-if="loading" class="flex h-[60vh] items-center justify-center">
      <div
        class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
      <p class="ml-4 text-sm text-muted-foreground">
        {{ t("contest.detail.loading") }}
      </p>
    </div>

    <div v-else-if="contest" class="space-y-8">
      <!-- Contest Header -->
      <div class="space-y-6">
        <Button
          variant="ghost"
          size="sm"
          class="gap-2 rounded-full"
          @click="$router.push({ name: 'contest-home' })"
        >
          <ArrowLeft class="h-4 w-4" />
          {{ t("contest.detail.backToList") }}
        </Button>

        <div
          class="flex flex-col md:flex-row md:items-center justify-between gap-6"
        >
          <div class="space-y-2 flex-1">
            <div class="flex items-center gap-3">
              <h1 class="text-4xl font-black tracking-tight">
                {{ contest.title }}
              </h1>
              <Badge
                :variant="
                  contest.status === 'running'
                    ? 'destructive'
                    : contest.status === 'upcoming'
                      ? 'default'
                      : 'secondary'
                "
                class="rounded-full px-3 h-6 font-bold uppercase text-[10px] tracking-widest"
              >
                {{
                  contest.status === "upcoming"
                    ? t("contest.status.upcoming")
                    : contest.status === "running"
                      ? t("contest.list.liveBadge")
                      : t("contest.status.finished")
                }}
              </Badge>
            </div>
            <p
              v-if="contest.description"
              class="text-lg text-muted-foreground max-w-3xl leading-relaxed"
            >
              {{ contest.description }}
            </p>
          </div>

          <div class="flex flex-col sm:flex-row gap-3">
            <Button
              v-if="!isRegistered && contest.status === 'upcoming'"
              size="lg"
              class="gap-2 rounded-full h-12 px-8 font-bold shadow-lg shadow-primary/20"
              :disabled="registering"
              @click="handleRegister"
            >
              <Users class="h-5 w-5" />
              {{
                registering
                  ? t("contest.detail.registering")
                  : t("contest.detail.register")
              }}
            </Button>
            <Button
              v-else-if="isRegistered && contest.status === 'upcoming'"
              size="lg"
              variant="outline"
              class="gap-2 rounded-full h-12 px-8 font-bold"
              :disabled="registering"
              @click="handleUnregister"
            >
              <Users class="h-5 w-5" />
              {{
                registering
                  ? t("contest.detail.unregistering")
                  : t("contest.detail.unregister")
              }}
            </Button>
            <template v-else-if="contest.status === 'running'">
              <Button
                size="lg"
                class="gap-2 rounded-full h-12 px-8 font-bold shadow-lg shadow-primary/20"
                @click="scrollToSection('contest-problems')"
              >
                <PlayCircle class="h-5 w-5" />
                {{ t("contest.detail.enterContest") }}
              </Button>
              <Button
                size="lg"
                variant="outline"
                class="gap-2 rounded-full h-12 px-8 font-bold"
                @click="scrollToSection('contest-ranking')"
              >
                <Trophy class="h-5 w-5" />
                {{ t("contest.detail.liveRanking") }}
              </Button>
            </template>
            <template v-if="contest.status === 'finished'">
              <Button
                v-if="
                  !contestStore.virtualSession ||
                  contestStore.virtualSession.status !== 'IN_PROGRESS'
                "
                size="lg"
                class="gap-2 rounded-full h-12 px-8 font-bold shadow-lg shadow-primary/20"
                :disabled="startingVirtual"
                @click="handleStartVirtual"
              >
                <PlayCircle class="h-5 w-5" />
                {{
                  startingVirtual
                    ? t("contest.detail.starting")
                    : t("contest.virtual.start")
                }}
              </Button>
              <Button
                v-else
                size="lg"
                variant="outline"
                class="gap-2 rounded-full h-12 px-8 font-bold"
                disabled
              >
                <PlayCircle class="h-5 w-5" />
                {{ t("contest.virtual.active") }}
              </Button>
            </template>
          </div>
        </div>
      </div>

      <Card
        class="border-none shadow-lg text-white overflow-hidden rounded-2xl backdrop-blur-md relative"
        :class="statusCardClass"
      >
        <!-- Background Pattern -->
        <div
          class="absolute inset-0 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] opacity-20 mix-blend-soft-light"
        ></div>
        <div
          class="absolute -top-24 -right-24 w-64 h-64 bg-white/10 rounded-full blur-3xl"
        ></div>
        <div
          class="absolute -bottom-24 -left-24 w-64 h-64 bg-black/10 rounded-full blur-3xl"
        ></div>

        <CardContent class="p-8 relative z-10">
          <div
            class="flex flex-col gap-6 md:flex-row md:items-center md:justify-between"
          >
            <div class="space-y-3">
              <p
                class="text-xs font-bold uppercase tracking-[0.2em] text-white/80"
              >
                {{ t("contest.detail.contestStatus") }}
              </p>
              <div class="flex items-center gap-3">
                <span
                  class="inline-flex items-center gap-2 rounded-full bg-white/20 px-3 py-1 text-[10px] font-black uppercase tracking-widest backdrop-blur-sm border border-white/10"
                >
                  <span
                    v-if="contest.status === 'running'"
                    class="h-2 w-2 rounded-full bg-rose-500 animate-pulse shadow-[0_0_8px_2px_rgba(244,63,94,0.6)]"
                  ></span>
                  <span
                    v-else-if="contest.status === 'upcoming'"
                    class="h-2 w-2 rounded-full bg-emerald-400"
                  ></span>
                  {{
                    contest.status === "running"
                      ? t("contest.list.liveBadge")
                      : contest.status === "upcoming"
                        ? t("contest.status.upcoming")
                        : t("contest.status.finished")
                  }}
                </span>
                <span
                  v-if="statusHint"
                  class="text-xs font-medium text-white/90"
                  >{{ statusHint }}</span
                >
              </div>
              <p
                class="text-3xl font-black md:text-5xl tracking-tight drop-shadow-sm"
              >
                {{ statusLabel }}
              </p>
            </div>
            <div class="text-left md:text-right space-y-2">
              <p
                class="text-xs font-bold uppercase tracking-widest text-white/70"
              >
                {{
                  contest.status === "running"
                    ? t("contest.virtual.timeRemaining")
                    : contest.status === "upcoming"
                      ? t("contest.time.startsIn")
                      : t("contest.detail.status")
                }}
              </p>
              <p
                class="font-mono text-4xl font-black tracking-tighter md:text-6xl tabular-nums drop-shadow-sm"
              >
                {{ statusCountdown }}
              </p>
              <p
                v-if="contestEndTime"
                class="text-xs font-medium text-white/60"
              >
                {{
                  contest.status === "finished"
                    ? t("contest.detail.endedAt")
                    : t("contest.detail.endsAt")
                }}
                {{ formatDateTime(contestEndTime) }}
              </p>
            </div>
          </div>

          <div
            class="mt-8 h-3 rounded-full bg-black/20 overflow-hidden backdrop-blur-sm border border-white/5"
          >
            <div
              class="h-full bg-gradient-to-r from-white/80 to-white shadow-[0_0_10px_rgba(255,255,255,0.5)] transition-all duration-1000 ease-out"
              :style="{ width: `${statusProgress}%` }"
            ></div>
          </div>

          <div
            class="mt-6 flex flex-wrap items-center gap-6 text-xs font-medium text-white/80"
          >
            <span
              class="flex items-center gap-2 bg-black/10 px-3 py-1.5 rounded-lg backdrop-blur-sm border border-white/5"
            >
              <Calendar class="h-4 w-4" />
              {{ formatDateTime(contest.start_time) }}
            </span>
            <span
              v-if="contestEndTime"
              class="flex items-center gap-2 bg-black/10 px-3 py-1.5 rounded-lg backdrop-blur-sm border border-white/5"
            >
              <Clock class="h-4 w-4" />
              {{ formatDateTime(contestEndTime) }}
            </span>
            <span
              class="flex items-center gap-2 bg-black/10 px-3 py-1.5 rounded-lg backdrop-blur-sm border border-white/5"
            >
              <Users class="h-4 w-4" />
              {{ contest.participant_count || contest.participantCount || 0 }}
              {{ t("contest.detail.participants") }}
            </span>
          </div>
        </CardContent>
      </Card>

      <Separator />

      <!-- Virtual Contest Timer -->
      <VirtualContestTimer />

      <!-- Contest Info Cards -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Card
          class="border-none shadow-sm bg-gradient-to-br from-primary/5 via-primary/0 to-transparent hover:bg-primary/5 transition-colors"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary shadow-sm"
              >
                <Calendar class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.detail.startTime") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{ formatDateTime(contest.start_time) }}
            </p>
          </CardContent>
        </Card>

        <Card
          class="border-none shadow-sm bg-gradient-to-br from-amber-500/5 via-amber-500/0 to-transparent hover:bg-amber-500/5 transition-colors"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 shadow-sm"
              >
                <Clock class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.detail.duration") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{ contest.duration_minutes || contest.durationMinutes || 0 }}
              {{ t("contest.time.min_short") }}
            </p>
          </CardContent>
        </Card>

        <Card
          class="border-none shadow-sm bg-gradient-to-br from-emerald-500/5 via-emerald-500/0 to-transparent hover:bg-emerald-500/5 transition-colors"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 shadow-sm"
              >
                <Users class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.detail.participants") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{ contest.participant_count || contest.participantCount || 0 }}
            </p>
          </CardContent>
        </Card>

        <Card
          class="border-none shadow-sm bg-gradient-to-br from-blue-500/5 via-blue-500/0 to-transparent hover:bg-blue-500/5 transition-colors"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-500/10 text-blue-600 shadow-sm"
              >
                <Trophy class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-black uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.types.title") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{
                (contest.isRated ?? contest.is_rated)
                  ? t("contest.list.rated")
                  : t("contest.types.unrated")
              }}
            </p>
          </CardContent>
        </Card>
      </div>

      <Card
        v-if="contest.rules"
        class="border-none shadow-sm overflow-hidden rounded-2xl"
      >
        <CardHeader class="pb-3 border-b bg-muted/20">
          <CardTitle
            class="text-lg font-black uppercase tracking-widest text-muted-foreground"
            >{{ t("contest.detail.rules") }}</CardTitle
          >
        </CardHeader>
        <CardContent class="p-6">
          <p
            class="text-sm text-muted-foreground whitespace-pre-line leading-relaxed"
          >
            {{ contest.rules }}
          </p>
        </CardContent>
      </Card>

      <!-- Main Content Area -->
      <Tabs default-value="problems" class="w-full">
        <div class="flex items-center justify-between mb-6">
          <TabsList class="bg-muted/50 p-1 h-11 rounded-full">
            <TabsTrigger
              value="problems"
              class="rounded-full px-8 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("contest.detail.problems") }}
            </TabsTrigger>
            <TabsTrigger
              value="ranking"
              class="rounded-full px-8 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("contest.detail.ranking") }}
            </TabsTrigger>
          </TabsList>
        </div>

        <!-- Problem List -->
        <TabsContent value="problems" class="mt-0">
          <Card
            id="contest-problems"
            class="border-none shadow-sm overflow-hidden rounded-2xl"
          >
            <CardHeader class="pb-3 border-b bg-muted/20">
              <CardTitle
                class="text-lg font-black uppercase tracking-widest text-muted-foreground"
                >{{ t("contest.detail.challenges") }}</CardTitle
              >
            </CardHeader>
            <CardContent class="p-0">
              <div
                v-if="contest.status === 'upcoming'"
                class="flex flex-col items-center justify-center gap-4 px-6 py-12 text-center"
              >
                <div
                  class="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted text-muted-foreground"
                >
                  <Lock class="h-7 w-7" />
                </div>
                <div class="space-y-2">
                  <h3 class="text-lg font-black">
                    {{ t("contest.detail.problemsLocked") }}
                  </h3>
                  <p class="text-sm text-muted-foreground max-w-sm">
                    {{ t("contest.detail.problemsUnlockHint") }}
                  </p>
                </div>
                <Button
                  v-if="!isRegistered"
                  variant="outline"
                  class="rounded-full px-6"
                  :disabled="registering"
                  @click="handleRegister"
                >
                  {{ t("contest.detail.register") }}
                </Button>
              </div>
              <Table v-else>
                <TableHeader class="bg-muted/50">
                  <TableRow>
                    <TableHead class="w-20 pl-6 font-bold">#</TableHead>
                    <TableHead class="font-bold">{{
                      t("contest.detail.problemHeaders.title")
                    }}</TableHead>
                    <TableHead class="w-32 font-bold">{{
                      t("contest.detail.problemHeaders.difficulty")
                    }}</TableHead>
                    <TableHead class="w-24 text-center font-bold">{{
                      t("contest.detail.problemHeaders.score")
                    }}</TableHead>
                    <TableHead class="w-32 text-center font-bold">{{
                      t("contest.detail.problemHeaders.acceptance")
                    }}</TableHead>
                    <TableHead class="w-20 pr-6"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow
                    v-for="problem in contest.problems"
                    :key="problem.id"
                    class="group cursor-pointer hover:bg-muted/30 transition-colors"
                  >
                    <TableCell class="pl-6">
                      <div
                        class="flex h-10 w-10 items-center justify-center rounded-xl bg-muted font-mono text-sm font-black text-muted-foreground group-hover:bg-primary group-hover:text-primary-foreground transition-all"
                      >
                        {{ problem.problemIndex || "#" }}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div class="space-y-1">
                        <router-link
                          :to="{
                            name: 'problem-detail',
                            params: { slug: problem.slug },
                            query: { contestId },
                          }"
                          class="text-base font-bold hover:text-primary transition-colors"
                        >
                          {{ problem.title }}
                        </router-link>
                        <div
                          class="flex items-center gap-3 text-[10px] font-bold text-muted-foreground uppercase tracking-wider"
                        >
                          <span class="flex items-center gap-1">
                            <Target class="h-3 w-3" />
                            {{ problem.solvedCount || 0 }}
                            {{ t("problem.status.solved") }}
                          </span>
                          <span
                            >{{ problem.submissionCount || 0 }}
                            {{ t("problem.detail.submissions") }}</span
                          >
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        :class="[
                          getDifficultyColor(problem.difficulty || 'Medium'),
                          'font-black text-[10px] uppercase h-5 px-2 rounded-sm border-current/20 bg-current/5',
                        ]"
                      >
                        {{
                          t(
                            `problem.difficulty.${(problem.difficulty || "medium").toLowerCase()}`,
                          )
                        }}
                      </Badge>
                    </TableCell>
                    <TableCell class="text-center">
                      <span
                        class="inline-flex items-center gap-1 font-black text-amber-600"
                      >
                        <Award class="h-4 w-4" />
                        {{ problem.score || 0 }}
                      </span>
                    </TableCell>
                    <TableCell class="text-center">
                      <span class="text-sm font-bold text-muted-foreground">
                        {{ problem.acceptanceRate || "0%" }}
                      </span>
                    </TableCell>
                    <TableCell class="pr-6">
                      <Button
                        size="icon"
                        variant="ghost"
                        class="h-8 w-8 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                        @click="
                          $router.push({
                            name: 'problem-detail',
                            params: { slug: problem.slug },
                            query: { contestId },
                          })
                        "
                      >
                        <ChevronRight class="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        <!-- Ranking -->
        <TabsContent value="ranking" class="mt-0">
          <Card
            id="contest-ranking"
            class="border-none shadow-sm overflow-hidden rounded-2xl"
          >
            <CardHeader
              class="flex flex-row items-center justify-between pb-3 border-b bg-muted/20"
            >
              <CardTitle
                class="text-lg font-black uppercase tracking-widest text-muted-foreground"
                >{{ t("contest.detail.leaderboard") }}</CardTitle
              >
              <Button
                variant="outline"
                size="sm"
                class="rounded-full h-8 font-bold text-[10px]"
                >{{ t("contest.detail.viewAll") }}</Button
              >
            </CardHeader>
            <CardContent class="p-0">
              <Table>
                <TableHeader class="bg-muted/50">
                  <TableRow>
                    <TableHead class="w-20 pl-6 font-bold">{{
                      t("contest.detail.rankingHeaders.rank")
                    }}</TableHead>
                    <TableHead class="font-bold">{{
                      t("contest.detail.rankingHeaders.user")
                    }}</TableHead>
                    <TableHead class="w-24 text-center font-bold">{{
                      t("contest.detail.rankingHeaders.score")
                    }}</TableHead>
                    <TableHead class="w-32 text-center font-bold">{{
                      t("contest.detail.rankingHeaders.time")
                    }}</TableHead>
                    <TableHead class="w-48 font-bold">{{
                      t("contest.detail.rankingHeaders.problems")
                    }}</TableHead>
                    <TableHead class="w-32 pr-6 text-right font-bold">{{
                      t("contest.detail.rankingHeaders.rating")
                    }}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow
                    v-for="entry in rankings.slice(0, 20)"
                    :key="entry.username"
                    class="group hover:bg-muted/30 transition-colors"
                  >
                    <TableCell class="pl-6">
                      <div
                        class="inline-flex h-10 w-10 items-center justify-center rounded-xl font-black text-sm transition-all"
                        :class="{
                          'bg-gradient-to-br from-yellow-400 to-yellow-600 text-white shadow-lg shadow-yellow-500/20 scale-110':
                            entry.rank === 1,
                          'bg-gradient-to-br from-slate-300 to-slate-500 text-white shadow-lg shadow-slate-400/20 scale-105':
                            entry.rank === 2,
                          'bg-gradient-to-br from-orange-400 to-orange-600 text-white shadow-lg shadow-orange-500/20':
                            entry.rank === 3,
                          'bg-muted text-muted-foreground': entry.rank > 3,
                        }"
                      >
                        {{ entry.rank }}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div class="flex items-center gap-3">
                        <div class="relative">
                          <img
                            :src="
                              entry.avatar ||
                              'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                            "
                            class="h-10 w-10 rounded-xl border border-border bg-muted shadow-sm"
                            alt="Avatar"
                          />
                          <span
                            class="absolute -bottom-1 -right-1 text-base shadow-sm bg-background rounded-sm"
                          >
                            {{ getCountryFlag(entry.country || "CN") }}
                          </span>
                        </div>
                        <div class="flex flex-col">
                          <span class="font-black text-sm">{{
                            entry.username
                          }}</span>
                          <span
                            class="text-[10px] font-bold text-muted-foreground uppercase tracking-widest"
                          >
                            {{ entry.ratingBefore || 1500 }} →
                            {{ entry.ratingAfter || 1500 }}
                          </span>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell class="text-center">
                      <span class="text-xl font-black tracking-tight">{{
                        entry.totalScore ?? entry.score ?? 0
                      }}</span>
                    </TableCell>
                    <TableCell class="text-center">
                      <span
                        class="font-mono text-xs font-bold text-muted-foreground"
                      >
                        {{
                          formatPenaltyTime(
                            entry.finishTime ??
                              entry.finish_time ??
                              entry.totalPenalty ??
                              0,
                          )
                        }}
                      </span>
                    </TableCell>
                    <TableCell>
                      <div class="flex flex-wrap gap-1">
                        <Badge
                          v-for="result in entry.problemResults || []"
                          :key="result.problemIndex"
                          :variant="result.isSolved ? 'default' : 'secondary'"
                          class="min-w-[2rem] justify-center font-mono text-[10px] h-6 rounded px-1.5"
                        >
                          {{ result.problemIndex }}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell class="pr-6 text-right">
                      <div
                        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-black shadow-sm"
                        :class="{
                          'bg-emerald-500/10 text-emerald-600 border border-emerald-500/20':
                            (entry.ratingChange || 0) > 0,
                          'bg-rose-500/10 text-rose-600 border border-rose-500/20':
                            (entry.ratingChange || 0) < 0,
                          'bg-muted text-muted-foreground border border-border':
                            (entry.ratingChange || 0) === 0,
                        }"
                      >
                        <TrendingUp
                          v-if="(entry.ratingChange || 0) > 0"
                          class="h-3 w-3"
                        />
                        <TrendingDown
                          v-else-if="(entry.ratingChange || 0) < 0"
                          class="h-3 w-3"
                        />
                        {{ (entry.ratingChange || 0) > 0 ? "+" : ""
                        }}{{ entry.ratingChange || 0 }}
                      </div>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>

    <div
      v-else-if="!loading"
      class="flex flex-col items-center justify-center py-32 border-2 border-dashed rounded-3xl bg-muted/5 text-center px-6"
    >
      <Trophy class="h-16 w-16 text-muted-foreground/20 mb-4" />
      <h3 class="text-2xl font-black tracking-tight">
        {{ t("contest.detail.notFound.title") }}
      </h3>
      <p class="text-muted-foreground mt-2 max-w-[300px]">
        {{ t("contest.detail.notFound.description") }}
      </p>
      <Button
        variant="outline"
        class="mt-8 rounded-full px-8 h-11 font-bold"
        @click="$router.push({ name: 'contest-home' })"
      >
        {{ t("contest.detail.notFound.return") }}
      </Button>
    </div>
  </div>
</template>
