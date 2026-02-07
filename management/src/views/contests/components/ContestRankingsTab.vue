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

defineProps<{
  rankings: ContestRanking[]
}>()
</script>

<template>
  <div class="border rounded-md">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead class="w-[60px]">{{ $t('contests.detail.rank') }}</TableHead>
          <TableHead>{{ $t('contests.detail.user') }}</TableHead>
          <TableHead class="text-right">{{ $t('contests.detail.score') }}</TableHead>
          <TableHead class="text-right">{{ $t('contests.detail.penalty') }}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow v-for="(r, i) in rankings" :key="r.id">
          <TableCell class="font-medium">#{{ i + 1 }}</TableCell>
          <TableCell>
            <div class="flex items-center gap-2">
              <span>{{ r.user.username }}</span>
            </div>
          </TableCell>
          <TableCell class="text-right font-medium">{{ r.total_score }}</TableCell>
          <TableCell class="text-right text-muted-foreground">
            {{ r.total_penalty }}
          </TableCell>
        </TableRow>
        <TableRow v-if="!rankings.length">
          <TableCell colspan="4" class="h-24 text-center text-muted-foreground">
            {{ $t('contests.detail.noRankingsYet') }}
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  </div>
</template>
