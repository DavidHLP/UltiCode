<script setup lang="ts">
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { ContestRanking } from '@/api/admin/contests'
import {
  getRankAccentClass,
  getRankIcon,
  RANK_TEXT_CLASS,
} from '@/views/contest/rankingPresentation'

withDefaults(
  defineProps<{
    rankings?: ContestRanking[]
  }>(),
  {
    rankings: () => [],
  },
)
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center gap-2">
      <span class="terminal-comment text-xs">[{{ rankings.length }}]</span>
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-[var(--silver-200)] dark:border-[var(--silver-700)]">
      <Table class="terminal-table">
        <TableHeader>
          <TableRow
            class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
          >
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[60px]"
            >
              {{ $t('contests.detail.rank') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]"
            >
              {{ $t('contests.detail.user') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[100px] text-right"
            >
              {{ $t('contests.detail.score') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[100px] text-right"
            >
              {{ $t('contests.detail.penalty') }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="(r, i) in rankings"
            :key="r.userId"
            :class="[
              'border-b border-[var(--silver-100)] dark:border-[var(--silver-800)]',
              i < 3 ? 'bg-[var(--surface-sunken)]' : '',
            ]"
          >
            <TableCell>
              <div class="flex items-center gap-2">
                <component
                  :is="getRankIcon(i + 1)"
                  v-if="getRankIcon(i + 1)"
                  :class="['h-4 w-4', getRankAccentClass(i + 1)]"
                />
                <span :class="['font-data text-sm font-bold tabular-nums', RANK_TEXT_CLASS]">
                  #{{ i + 1 }}
                </span>
              </div>
            </TableCell>
            <TableCell>
              <span class="font-medium text-sm text-[var(--foreground)]">{{ r.username }}</span>
            </TableCell>
            <TableCell class="text-right">
              <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums font-bold">
                {{ r.score }}
              </span>
            </TableCell>
            <TableCell class="text-right">
              <span class="font-data text-xs text-[var(--silver-400)] tabular-nums">
                {{ r.penalty }}
              </span>
            </TableCell>
          </TableRow>
          <TableRow v-if="!rankings.length">
            <TableCell colspan="4" class="h-24 text-center">
              <span class="terminal-comment">{{ $t('contests.detail.noRankingsYet') }}</span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
