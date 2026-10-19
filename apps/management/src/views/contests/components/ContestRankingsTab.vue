<script setup lang="ts">
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@ulticode/design-system/ui/table'
import type { ContestRanking } from '@/api/admin/contests'
import { Button } from '@ulticode/design-system/ui/button'
import {
  getRankAccentClass,
  getRankIcon,
  RANK_TEXT_CLASS,
} from '@/views/contest/rankingPresentation'

const emit = defineEmits(['retry'])

withDefaults(
  defineProps<{
    rankings?: ContestRanking[]
    isLoading?: boolean
    hasError?: boolean
  }>(),
  {
    rankings: () => [],
    isLoading: false,
    hasError: false,
  },
)
</script>

<template>
  <div class="space-y-4">
    <div v-if="isLoading" role="status" class="flex h-24 items-center justify-center">
      <span class="text-sm text-muted-foreground">{{ $t('contests.detail.rankingsLoading') }}</span>
    </div>

    <div
      v-else-if="hasError"
      role="alert"
      class="flex min-h-24 flex-col items-center justify-center gap-3 border border-border p-4 text-center"
    >
      <span class="text-sm text-muted-foreground">
        {{ $t('contests.detail.rankingsLoadFailed') }}
      </span>
      <Button variant="outline" size="sm" @click="emit('retry')">
        {{ $t('common.retry') }}
      </Button>
    </div>

    <template v-else>
      <!-- Header -->
      <div class="flex items-center gap-2">
        <span class="text-sm text-muted-foreground text-xs">[{{ rankings.length }}]</span>
      </div>

      <!-- Table - Terminal Style -->
      <div class="border border-border dark:border-foreground">
        <Table class="w-full">
          <TableHeader>
            <TableRow>
              <TableHead class="w-[60px]">
                {{ $t('contests.detail.rank') }}
              </TableHead>
              <TableHead>
                {{ $t('contests.detail.user') }}
              </TableHead>
              <TableHead class="w-[100px] text-right">
                {{ $t('contests.detail.score') }}
              </TableHead>
              <TableHead class="w-[100px] text-right">
                {{ $t('contests.detail.penalty') }}
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="(r, i) in rankings"
              :key="r.userId"
              :class="['border-b border-accent dark:border-foreground', i < 3 ? 'bg-muted' : '']"
            >
              <TableCell>
                <div class="flex items-center gap-2">
                  <component
                    :is="getRankIcon(i + 1)"
                    v-if="getRankIcon(i + 1)"
                    :class="['h-4 w-4', getRankAccentClass(i + 1)]"
                  />
                  <span :class="['font-mono text-sm font-bold tabular-nums', RANK_TEXT_CLASS]">
                    #{{ i + 1 }}
                  </span>
                </div>
              </TableCell>
              <TableCell>
                <span class="font-medium text-sm text-foreground">{{ r.username }}</span>
              </TableCell>
              <TableCell class="text-right">
                <span class="font-mono text-sm text-foreground tabular-nums font-bold">
                  {{ r.score }}
                </span>
              </TableCell>
              <TableCell class="text-right">
                <span class="font-mono text-xs text-muted-foreground tabular-nums">
                  {{ r.penalty }}
                </span>
              </TableCell>
            </TableRow>
            <TableRow v-if="!rankings.length">
              <TableCell colspan="4" class="h-24 text-center">
                <span class="text-sm text-muted-foreground">{{
                  $t('contests.detail.noRankingsYet')
                }}</span>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </template>
  </div>
</template>
