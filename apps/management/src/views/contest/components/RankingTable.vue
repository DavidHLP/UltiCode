<script setup lang="ts">
/**
 * RankingTable Component
 *
 * Table showing ranking entries with:
 * - Columns: rank, user, score, time/penalty, solved count
 * - Uses data from rankingStore or passed props
 */
import { computed } from 'vue'
import { IconTrophy, IconMedal } from '@tabler/icons-vue'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

export interface RankingEntry {
  id: string
  rank: number
  userId: string
  username: string
  displayName?: string | null
  score: number
  penalty: number
  solvedCount: number
  problems?: Record<string, { solved: boolean; attempts: number; time?: number }>
}

const props = withDefaults(
  defineProps<{
    rankings: RankingEntry[]
    loading?: boolean
    showProblems?: boolean
    problemLabels?: string[]
    highlightUserId?: string
  }>(),
  {
    loading: false,
    showProblems: false,
    problemLabels: () => [],
  },
)

// Get rank styling
function getRankStyle(rank: number) {
  if (rank === 1) return 'text-[var(--terminal-amber)]'
  if (rank === 2) return 'text-[var(--silver-400)]'
  if (rank === 3) return 'text-[oklch(0.5808_0.1732_39.5)]'
  return 'text-[var(--foreground)]'
}

// Get rank icon
function getRankIcon(rank: number) {
  if (rank === 1) return IconTrophy
  if (rank <= 3) return IconMedal
  return null
}

// Format penalty as time string
function formatPenalty(penalty: number): string {
  const hours = Math.floor(penalty / 3600)
  const minutes = Math.floor((penalty % 3600) / 60)
  const seconds = penalty % 60

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

// Get problem cell styling
function getProblemCellStyles(problem?: { solved: boolean; attempts: number }) {
  if (!problem) return ''
  if (problem.solved) {
    return 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)] text-[var(--terminal-green)]'
  }
  if (problem.attempts > 0) {
    return 'bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)] text-[var(--terminal-red)]'
  }
  return ''
}

// Check if row should be highlighted
function isHighlighted(entry: RankingEntry) {
  return props.highlightUserId === entry.userId
}

// Computed problem labels
const displayProblemLabels = computed(() => {
  if (props.problemLabels.length > 0) return props.problemLabels
  // Generate A, B, C, ... labels based on first ranking's problems
  const firstRanking = props.rankings[0]
  if (firstRanking && firstRanking.problems) {
    return Object.keys(firstRanking.problems).sort()
  }
  return []
})
</script>

<template>
  <div
    class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] rounded-none overflow-hidden"
  >
    <Table>
      <TableHeader class="bg-[var(--surface-sunken)]">
        <TableRow class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)]">
          <TableHead
            class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[60px]"
          >
            Rank
          </TableHead>
          <TableHead class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]">
            User
          </TableHead>
          <TableHead
            class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[80px] text-right"
          >
            Score
          </TableHead>
          <TableHead
            class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[80px] text-right"
          >
            Time
          </TableHead>
          <TableHead
            class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[80px] text-right"
          >
            Solved
          </TableHead>
          <!-- Problem columns -->
          <TableHead
            v-for="label in displayProblemLabels"
            :key="label"
            class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[50px] text-center"
          >
            {{ label }}
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <!-- Loading state -->
        <template v-if="loading">
          <TableRow v-for="i in 5" :key="i">
            <TableCell v-for="j in 4 + displayProblemLabels.length" :key="j">
              <div class="h-4 bg-[var(--silver-200)] animate-pulse rounded-none" />
            </TableCell>
          </TableRow>
        </template>

        <!-- Data rows -->
        <template v-else>
          <TableRow
            v-for="(entry, i) in rankings"
            :key="entry.id"
            :class="[
              'border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] transition-colors',
              i < 3 ? 'bg-[var(--surface-sunken)]' : '',
              isHighlighted(entry) ? 'ring-2 ring-inset ring-[var(--accent-electric)]' : '',
            ]"
          >
            <!-- Rank -->
            <TableCell>
              <div class="flex items-center gap-2">
                <component
                  :is="getRankIcon(entry.rank)"
                  v-if="getRankIcon(entry.rank)"
                  :class="['h-4 w-4', getRankStyle(entry.rank)]"
                />
                <span
                  :class="['font-data text-sm font-bold tabular-nums', getRankStyle(entry.rank)]"
                >
                  #{{ entry.rank }}
                </span>
              </div>
            </TableCell>

            <!-- User -->
            <TableCell>
              <span class="font-medium text-sm text-[var(--foreground)]">
                {{ entry.displayName || entry.username }}
              </span>
              <span
                v-if="entry.displayName"
                class="block font-data text-xs text-[var(--silver-400)]"
              >
                @{{ entry.username }}
              </span>
            </TableCell>

            <!-- Score -->
            <TableCell class="text-right">
              <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums font-bold">
                {{ entry.score }}
              </span>
            </TableCell>

            <!-- Time/Penalty -->
            <TableCell class="text-right">
              <span class="font-data text-xs text-[var(--silver-400)] tabular-nums">
                {{ formatPenalty(entry.penalty) }}
              </span>
            </TableCell>

            <!-- Solved count -->
            <TableCell class="text-right">
              <span class="font-data text-sm text-[var(--foreground)] tabular-nums">
                {{ entry.solvedCount }}
              </span>
            </TableCell>

            <!-- Problem cells -->
            <TableCell
              v-for="label in displayProblemLabels"
              :key="label"
              :class="[
                'text-center font-data text-xs',
                getProblemCellStyles(entry.problems?.[label]),
              ]"
            >
              <template v-if="entry.problems?.[label]">
                <span v-if="entry.problems[label].solved" class="text-[var(--terminal-green)]">
                  +{{ entry.problems[label].attempts || 1 }}
                </span>
                <span
                  v-else-if="entry.problems[label].attempts > 0"
                  class="text-[var(--terminal-red)]"
                >
                  -{{ entry.problems[label].attempts }}
                </span>
                <span v-else class="text-[var(--silver-400)]">-</span>
              </template>
            </TableCell>
          </TableRow>

          <!-- Empty state -->
          <TableRow v-if="rankings.length === 0">
            <TableCell :colspan="5 + displayProblemLabels.length" class="h-24 text-center">
              <span class="text-[var(--silver-400)] text-sm">No rankings available yet</span>
            </TableCell>
          </TableRow>
        </template>
      </TableBody>
    </Table>
  </div>
</template>
