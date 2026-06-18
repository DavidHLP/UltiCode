<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { useContestStore } from "@/stores/contest";
import {
  getContestProblems,
  fetchContestProblemSubmissions,
} from "@/api/contest";
import { useAuthStore } from "@/stores/auth";
import type { ContestProblemSummary } from "@/types/contest";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { toast } from "vue-sonner";
import { Trophy, Calendar, Clock, Users } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import VirtualContestTimer from "../components/VirtualContestTimer.vue";
import ContestHeader from "./components/ContestHeader.vue";
import ContestRegistration from "./components/ContestRegistration.vue";
import ContestProblemList from "./components/ContestProblemList.vue";
import ContestRankingTable from "./components/ContestRankingTable.vue";
import { useContestStatus } from "./composables/useContestStatus";
import { useContestRankings } from "./composables/useContestRankings";

const route = useRoute();
const contestStore = useContestStore();
const { t } = useI18n();
const contestId = route.params.slug as string;

const contest = computed(() => contestStore.currentContest);
const loading = computed(() => contestStore.loading);
const registering = ref(false);
const startingVirtual = ref(false);
const contestProblems = ref<ContestProblemSummary[]>([]);

const isRegistered = computed(() => contestStore.isRegistered(contestId));
// 委托 store 的归一化判定（后端返回 status "started"，store 已处理枚举错配），
// 避免在此重复硬编码 "IN_PROGRESS"。
const virtualSessionActive = computed(() => contestStore.isInVirtualContest);

const {
  statusCountdown,
  statusLabel,
  statusHint,
  statusProgress,
  statusCardClass,
  contestEndTime,
  formatDateTime,
  getDifficultyColor,
} = useContestStatus(contest, isRegistered);

const contestIdRef = computed(() => contestId);
const { rankings, getCountryFlag } = useContestRankings(contestIdRef, contest);

const problemStatuses = ref<Record<number, "solved" | "attempted" | "todo">>(
  {},
);

async function loadProblemStatuses() {
  const authStore = useAuthStore();
  if (!authStore.isAuthenticated) return;

  const promises = contestProblems.value.map(async (prob) => {
    try {
      const submissions = await fetchContestProblemSubmissions(
        contestId,
        prob.problemId,
      );
      const isSolved = submissions.some((sub) => sub.status === "Accepted");
      const isAttempted = submissions.length > 0;
      problemStatuses.value[prob.problemId] = isSolved
        ? "solved"
        : isAttempted
          ? "attempted"
          : "todo";
    } catch (e) {
      console.error(
        `Failed to load submissions for problem ${prob.problemId}:`,
        e,
      );
      problemStatuses.value[prob.problemId] = "todo";
    }
  });

  await Promise.allSettled(promises);
}

onMounted(async () => {
  try {
    await contestStore.loadContestDetail(contestId);
    const [, problemsResult] = await Promise.allSettled([
      Promise.all([
        contestStore.loadParticipationStatus(contestId),
        contestStore.loadVirtualSession(contestId),
      ]),
      getContestProblems(contestId),
    ]);
    if (problemsResult.status === "fulfilled") {
      contestProblems.value = problemsResult.value;
      await loadProblemStatuses();
    }
  } catch (err) {
    // Log error for debugging
    console.error("Failed to load contest details:", err);
    // Error handled by UI state - contest will be null and loading will be false
  }
});

async function handleRegister() {
  registering.value = true;
  try {
    await contestStore.registerForContest(contestId);
  } catch (error: unknown) {
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
    toast.error(
      getErrorMessage(error, t("contest.detail.unregistrationFailed")),
    );
  } finally {
    registering.value = false;
  }
}

async function handleStartVirtual() {
  startingVirtual.value = true;
  try {
    await contestStore.startVirtualContest(contestId);
    await contestStore.loadParticipationStatus(contestId);
    toast.success(t("contest.virtual.started"));
    scrollToSection("contest-problems");
  } catch (error: unknown) {
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
      <ContestHeader :contest="contest" />

      <ContestRegistration
        :contest="contest"
        :status-card-class="statusCardClass"
        :status-label="statusLabel"
        :status-countdown="statusCountdown"
        :status-hint="statusHint"
        :status-progress="statusProgress"
        :contest-end-time="contestEndTime"
        :format-date-time="formatDateTime"
        :is-registered="isRegistered"
        :registering="registering"
        :starting-virtual="startingVirtual"
        :virtual-session-active="!!virtualSessionActive"
        @register="handleRegister"
        @unregister="handleUnregister"
        @start-virtual="handleStartVirtual"
        @scroll-to-problems="scrollToSection('contest-problems')"
        @scroll-to-ranking="scrollToSection('contest-ranking')"
      />

      <Separator />

      <!-- Virtual Contest Timer -->
      <VirtualContestTimer />

      <!-- Contest Info Cards -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Card
          class="border border-border border-t-2 border-t-[var(--accent-electric)] bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-sm transition-all duration-300 hover:border-muted-foreground/40 hover:-translate-y-0.5 rounded-none"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-none bg-primary/10 text-primary shadow-sm"
              >
                <Calendar class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-bold font-mono uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.detail.startTime") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{ formatDateTime(contest.startTime) }}
            </p>
          </CardContent>
        </Card>

        <Card
          class="border border-border border-t-2 border-t-[var(--terminal-amber)] bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-sm transition-all duration-300 hover:border-muted-foreground/40 hover:-translate-y-0.5 rounded-none"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-none bg-[var(--terminal-amber)]/10 text-[var(--terminal-amber)] shadow-sm"
              >
                <Clock class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-bold font-mono uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.detail.duration") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{ contest.duration || 0 }}
              {{ t("contest.time.min_short") }}
            </p>
          </CardContent>
        </Card>

        <Card
          class="border border-border border-t-2 border-t-[var(--terminal-green)] bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-sm transition-all duration-300 hover:border-muted-foreground/40 hover:-translate-y-0.5 rounded-none"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-none bg-[var(--terminal-green)]/10 text-[var(--terminal-green)] shadow-sm"
              >
                <Users class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-bold font-mono uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.detail.participants") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{ contest.participantCount || 0 }}
            </p>
          </CardContent>
        </Card>

        <Card
          class="border border-border border-t-2 border-t-[var(--accent-electric)] bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-sm transition-all duration-300 hover:border-muted-foreground/40 hover:-translate-y-0.5 rounded-none"
        >
          <CardContent class="p-6 flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-none bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] shadow-sm"
              >
                <Trophy class="h-5 w-5" />
              </div>
              <p
                class="text-[10px] font-bold font-mono uppercase tracking-widest text-muted-foreground"
              >
                {{ t("contest.types.title") }}
              </p>
            </div>
            <p class="text-base font-bold truncate pl-1">
              {{
                contest.isRated
                  ? t("contest.types.rated")
                  : t("contest.types.unrated")
              }}
            </p>
          </CardContent>
        </Card>
      </div>

      <Card
        v-if="contest.rules"
        class="border border-border bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-sm overflow-hidden rounded-none"
      >
        <CardHeader
          class="pb-3 border-b border-border bg-[var(--silver-100)]/50 dark:bg-[var(--solarized-base03)]/50"
        >
          <CardTitle
            class="text-xs font-bold font-mono uppercase tracking-widest text-[var(--solarized-base01)] dark:text-[var(--solarized-base1)]"
          >
            {{ t("contest.detail.rules") }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-6">
          <p
            class="text-sm text-[var(--solarized-base00)] dark:text-[var(--solarized-base0)] whitespace-pre-line leading-relaxed"
          >
            {{ contest.rules }}
          </p>
        </CardContent>
      </Card>

      <!-- Main Content Area -->
      <Tabs default-value="problems" class="w-full">
        <div class="flex items-center justify-between mb-6">
          <TabsList
            class="bg-transparent border-b border-border/60 w-full justify-start p-0 h-10 gap-2 rounded-none"
          >
            <TabsTrigger
              value="problems"
              class="rounded-none px-6 h-full font-bold bg-transparent text-muted-foreground border-b-2 border-b-transparent data-[state=active]:border-b-[var(--accent-electric)] data-[state=active]:text-foreground data-[state=active]:bg-transparent transition-all shadow-none hover:text-foreground cursor-pointer"
            >
              {{ t("contest.detail.problems") }}
            </TabsTrigger>
            <TabsTrigger
              value="ranking"
              class="rounded-none px-6 h-full font-bold bg-transparent text-muted-foreground border-b-2 border-b-transparent data-[state=active]:border-b-[var(--accent-electric)] data-[state=active]:text-foreground data-[state=active]:bg-transparent transition-all shadow-none hover:text-foreground cursor-pointer"
            >
              {{ t("contest.detail.ranking") }}
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="problems" class="mt-0">
          <ContestProblemList
            :contest="contest"
            :problems="contestProblems"
            :contest-id="contestId"
            :is-registered="isRegistered"
            :registering="registering"
            :get-difficulty-color="getDifficultyColor"
            :problem-statuses="problemStatuses"
            :is-in-virtual-session="virtualSessionActive"
            @register="handleRegister"
          />
        </TabsContent>

        <TabsContent value="ranking" class="mt-0">
          <ContestRankingTable
            :rankings="rankings"
            :get-country-flag="getCountryFlag"
          />
        </TabsContent>
      </Tabs>
    </div>

    <div
      v-else-if="!loading"
      class="flex flex-col items-center justify-center py-32 border-2 border-dashed rounded-none bg-muted/5 text-center px-6"
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
        class="mt-8 px-8 h-11 font-bold"
        @click="$router.push({ name: 'contest-list' })"
      >
        {{ t("contest.detail.notFound.return") }}
      </Button>
    </div>
  </div>
</template>
