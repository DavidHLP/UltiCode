<script setup lang="ts">
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { IconPlus, IconTrash } from '@tabler/icons-vue'
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
        <span class="terminal-comment text-xs">[{{ contest.problemCount || 0 }}]</span>
      </div>
      <Button
        v-if="canUpdate"
        variant="terminal"
        size="sm"
        class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
        @click="emit('addProblem')"
      >
        <IconPlus class="mr-1.5 h-3.5 w-3.5" />
        <span class="uppercase tracking-wider">{{ $t('contests.detail.addProblem') }}</span>
      </Button>
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
              #
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]"
            >
              {{ $t('contests.detail.problem') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[80px]"
            >
              {{ $t('contests.detail.score') }}
            </TableHead>
            <TableHead
              class="font-data text-2xs uppercase tracking-widest text-[var(--silver-500)] w-[50px]"
            >
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="(problemId, index) in contest.problemIds || []"
            :key="problemId"
            class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)]"
          >
            <TableCell class="font-data text-xs text-[var(--accent-electric)]">
              {{ String(index + 1).padStart(2, '0') }}
            </TableCell>
            <TableCell>
              <span class="font-medium text-sm text-[var(--foreground)]">
                {{ $t('contests.detail.problem') }} #{{ problemId }}
              </span>
            </TableCell>
            <TableCell>
              <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">—</span>
            </TableCell>
            <TableCell>
              <Button
                v-if="canUpdate"
                variant="ghost"
                size="icon"
                class="h-8 w-8 text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
                @click="emit('removeProblem', problemId)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="!contest.problemIds?.length">
            <TableCell colspan="4" class="h-24 text-center">
              <span class="terminal-comment">{{ $t('contests.detail.noProblemsAdded') }}</span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
