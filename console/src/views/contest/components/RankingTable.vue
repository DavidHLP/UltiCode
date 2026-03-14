<script setup lang="ts">
/**
 * RankingTable - Live ranking table for contests
 *
 * Shows rank, user, score, time, problems solved with real-time updates.
 * Uses the ranking store for data and supports loading states.
 */
import { computed, watch, onMounted, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { useRankingStore } from "@/stores/contest/rankingStore";
import { useContestSocket } from "@/composables/contest";
import type { RankingEntry, ProblemResult } from "@/types/contest";
import { getRatingColor } from "@/types/contest";
import { formatPenaltyTime } from "@/utils/date";

const props = defineProps<{
  /** Contest ID for WebSocket subscription */
  contestId: string;
  /** Whether to show problem columns */
  showProblems?: boolean;
  /** Problem indices for column headers */
  problemIndices?: string[];
  /** Maximum rows to display (0 for unlimited) */
  maxRows?: number;
  /** Whether to highlight the current user */
  currentUserId?: string;
  /** Enable real-time updates via WebSocket */
  enableRealtime?: boolean;
}>();

const { t } = useI18n();
const rankingStore = useRankingStore();

// WebSocket connection for real-time updates
const { isConnected, joinContest, leaveContest, onRankingUpdate } = useContestSocket();

// Rankings from store
const rankings = computed(() => {
  const data = rankingStore.rankings;
  const maxRows = props.maxRows ?? 0;
  return maxRows > 0 ? data.slice(0, maxRows) : data;
});

const loading = computed(() => rankingStore.loading);
const error = computed(() => rankingStore.error);
const isFrozen = computed(() => rankingStore.isFrozen);

// Problem indices from rankings if not provided
const problemColumns = computed(() => {
  if (props.problemIndices?.length) {
    return props.problemIndices;
  }
  // Extract problem indices from first ranking entry
  const firstEntry = rankings.value[0];
  return firstEntry?.problemResults?.map((p) => p.problemIndex) || [];
});

// Get cell color based on problem result
function getProblemCellClass(result: ProblemResult | undefined): string {
  if (!result) return "";

  if (result.isSolved) {
    if (result.firstSolve) {
      return "bg-green-500 text-white"; // First solve
    }
    return "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400"; // Solved
  }

  if (result.attempts > 0) {
    return "bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400"; // Tried but failed
  }

  return "";
}

// Format problem result cell content
function formatProblemResult(result: ProblemResult | undefined): string {
  if (!result) return "-";

  if (result.isSolved) {
    return `${result.score}`;
  }

  if (result.attempts > 0) {
    return `-${result.wrongAttempts || result.attempts}`;
  }

  return "-";
}

// Get user initials for avatar fallback
function getInitials(username: string): string {
  return username.slice(0, 2).toUpperCase();
}

// Check if row is current user
function isCurrentUser(entry: RankingEntry): boolean {
  return props.currentUserId === entry.userId;
}

// Subscribe to real-time updates
let unsubscribe: (() => void) | null = null;

async function subscribeToUpdates() {
  if (!props.enableRealtime || !props.contestId) return;

  try {
    await joinContest(props.contestId);
    unsubscribe = onRankingUpdate((data) => {
      // Rankings are auto-updated in the store via the composable
      console.log("Ranking updated:", data.contestId);
    });
  } catch (err) {
    console.error("Failed to join contest room:", err);
  }
}

async function unsubscribeFromUpdates() {
  if (unsubscribe) {
    unsubscribe();
    unsubscribe = null;
  }
  if (props.enableRealtime) {
    await leaveContest();
  }
}

// Fetch initial rankings
onMounted(async () => {
  await rankingStore.fetchRanking(props.contestId);
  if (props.enableRealtime) {
    await subscribeToUpdates();
  }
});

// Cleanup on unmount
onUnmounted(async () => {
  await unsubscribeFromUpdates();
});

// Watch for contest ID changes
watch(
  () => props.contestId,
  async (newId) => {
    rankingStore.clearRanking();
    await rankingStore.fetchRanking(newId);
    if (props.enableRealtime) {
      await unsubscribeFromUpdates();
      await subscribeToUpdates();
    }
  }
);
</script>

<template>
  <div class="space-y-3">
    <!-- Frozen Banner -->
    <div
      v-if="isFrozen"
      class="bg-yellow-50 dark:bg-yellow-900/20 text-yellow-800 dark:text-yellow-200 px-3 py-2 rounded-md text-sm"
    >
      {{ t("contest.ranking.frozen", "Rankings are frozen during the final minutes") }}
    </div>

    <!-- Error State -->
    <div
      v-if="error"
      class="bg-destructive/10 text-destructive px-3 py-2 rounded-md text-sm"
    >
      {{ error }}
    </div>

    <!-- Table -->
    <div class="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16 text-center">
              {{ t("contest.ranking.rank") }}
            </TableHead>
            <TableHead class="min-w-[150px]">
              {{ t("contest.ranking.user") }}
            </TableHead>
            <TableHead class="w-20 text-center">
              {{ t("contest.ranking.score") }}
            </TableHead>
            <TableHead class="w-24 text-center">
              {{ t("contest.ranking.penalty") }}
            </TableHead>

            <!-- Problem columns -->
            <template v-if="showProblems">
              <TableHead
                v-for="idx in problemColumns"
                :key="idx"
                class="w-14 text-center font-semibold"
              >
                {{ idx }}
              </TableHead>
            </template>
          </TableRow>
        </TableHeader>

        <TableBody>
          <!-- Loading skeleton -->
          <template v-if="loading">
            <TableRow v-for="i in 5" :key="i">
              <TableCell><Skeleton class="h-4 w-8 mx-auto" /></TableCell>
              <TableCell><Skeleton class="h-8 w-full" /></TableCell>
              <TableCell><Skeleton class="h-4 w-10 mx-auto" /></TableCell>
              <TableCell><Skeleton class="h-4 w-12 mx-auto" /></TableCell>
              <TableCell v-for="j in problemColumns.length" :key="j">
                <Skeleton class="h-4 w-8 mx-auto" />
              </TableCell>
            </TableRow>
          </template>

          <!-- Empty state -->
          <TableRow v-else-if="rankings.length === 0">
            <TableCell
              :col-span="4 + problemColumns.length"
              class="text-center text-muted-foreground py-8"
            >
              {{ t("contest.ranking.noRankings", "No rankings available") }}
            </TableCell>
          </TableRow>

          <!-- Rankings -->
          <TableRow
            v-else
            v-for="entry in rankings"
            :key="entry.userId"
            :class="{ 'bg-primary/5': isCurrentUser(entry) }"
          >
            <!-- Rank -->
            <TableCell class="text-center font-semibold">
              <Badge
                v-if="entry.rank <= 3"
                :variant="entry.rank === 1 ? 'default' : 'secondary'"
                class="font-bold"
              >
                {{ entry.rank }}
              </Badge>
              <span v-else>{{ entry.rank }}</span>
            </TableCell>

            <!-- User -->
            <TableCell>
              <div class="flex items-center gap-3">
                <Avatar class="h-8 w-8">
                  <AvatarImage v-if="entry.avatar" :src="entry.avatar" />
                  <AvatarFallback>{{ getInitials(entry.username) }}</AvatarFallback>
                </Avatar>
                <div>
                  <span
                    class="font-medium"
                    :style="{ color: getRatingColor(entry.ratingBefore) }"
                  >
                    {{ entry.username }}
                  </span>
                  <Badge
                    v-if="entry.isVirtual"
                    variant="outline"
                    class="ml-2 text-[10px]"
                  >
                    {{ t("contest.types.virtual") }}
                  </Badge>
                </div>
              </div>
            </TableCell>

            <!-- Score -->
            <TableCell class="text-center font-semibold">
              {{ entry.totalScore }}
            </TableCell>

            <!-- Penalty -->
            <TableCell class="text-center text-muted-foreground">
              {{ formatPenaltyTime(entry.totalPenalty) }}
            </TableCell>

            <!-- Problem results -->
            <template v-if="showProblems">
              <TableCell
                v-for="result in entry.problemResults"
                :key="result.problemIndex"
                class="text-center text-sm font-medium"
                :class="getProblemCellClass(result)"
              >
                <div class="space-y-0.5">
                  <div>{{ formatProblemResult(result) }}</div>
                  <div
                    v-if="result.isSolved && result.solveTime"
                    class="text-[10px] opacity-70"
                  >
                    {{ Math.floor(result.solveTime / 60) }}:{{
                      (result.solveTime % 60).toString().padStart(2, "0")
                    }}
                  </div>
                </div>
              </TableCell>
            </template>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <!-- Connection status -->
    <div
      v-if="enableRealtime"
      class="flex items-center gap-2 text-xs text-muted-foreground"
    >
      <span
        class="h-2 w-2 rounded-full"
        :class="isConnected ? 'bg-green-500' : 'bg-gray-300'"
      />
      {{ isConnected ? t("contest.ranking.live", "Live") : t("contest.ranking.connecting", "Connecting...") }}
    </div>
  </div>
</template>
