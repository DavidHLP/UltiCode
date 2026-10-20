<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@ulticode/design-system/ui/table'
import { Plus as IconPlus, Trash as IconTrash } from '@lucide/vue'

import type { Contest } from '@/api/admin/contests'

defineProps<{
  contest: Contest
  canUpdate: boolean
}>()

const emit = defineEmits<{
  addProblem: []
  removeProblem: [problemId: number]
}>()
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex justify-between items-center">
      <div class="flex items-center gap-2">
        <span class="text-sm text-muted-foreground text-xs">[{{ contest.problemCount || 0 }}]</span>
      </div>
      <Button v-if="canUpdate" variant="outline" size="sm" @click="emit('addProblem')">
        <IconPlus class="mr-1.5 h-3.5 w-3.5" />
        <span class="uppercase tracking-wider">{{ $t('contests.detail.addProblem') }}</span>
      </Button>
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-border dark:border-foreground">
      <Table class="w-full">
        <TableHeader>
          <TableRow>
            <TableHead class="w-[60px]"> # </TableHead>
            <TableHead>
              {{ $t('contests.detail.problem') }}
            </TableHead>
            <TableHead class="w-[80px]">
              {{ $t('contests.detail.score') }}
            </TableHead>
            <TableHead class="w-[50px]"> </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="(problemId, index) in contest.problemIds || []" :key="problemId">
            <TableCell>
              {{ String(index + 1).padStart(2, '0') }}
            </TableCell>
            <TableCell>
              <span class="font-medium text-sm text-foreground">
                {{ $t('contests.detail.problem') }} #{{ problemId }}
              </span>
            </TableCell>
            <TableCell>
              <span class="font-mono text-sm text-foreground tabular-nums">—</span>
            </TableCell>
            <TableCell>
              <Button
                v-if="canUpdate"
                variant="ghost"
                size="icon"
                class="w-8"
                @click="emit('removeProblem', problemId)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="!contest.problemIds?.length">
            <TableCell colspan="4" class="h-24 whitespace-normal break-words text-center">
              <span class="text-sm text-muted-foreground">{{
                $t(
                  contest.problemCount > 0
                    ? 'contests.detail.problemDetailsUnavailable'
                    : 'contests.detail.noProblemsAdded',
                )
              }}</span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
