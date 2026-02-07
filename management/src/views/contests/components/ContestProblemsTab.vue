<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { IconTrash, IconPlus } from '@tabler/icons-vue'
import type { Contest } from '@/api/admin/contests'

defineProps<{
  contest: Contest
  canUpdate: boolean
}>()

const emit = defineEmits<{
  addProblem: []
  removeProblem: [problemId: string]
}>()
</script>

<template>
  <div class="space-y-4">
    <div class="flex justify-between items-center">
      <h3 class="text-lg font-medium">{{ $t('contests.detail.contestProblems') }}</h3>
      <Button v-if="canUpdate" size="sm" @click="emit('addProblem')">
        <IconPlus class="mr-2 h-4 w-4" />
        {{ $t('contests.detail.addProblem') }}
      </Button>
    </div>
    <div class="border rounded-md">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-[50px]">{{ $t('contests.detail.idx') }}</TableHead>
            <TableHead>{{ $t('contests.detail.problem') }}</TableHead>
            <TableHead>{{ $t('contests.detail.difficulty') }}</TableHead>
            <TableHead>{{ $t('contests.detail.score') }}</TableHead>
            <TableHead class="w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="cp in contest.problems" :key="cp.id">
            <TableCell class="font-medium">{{ cp.problem_index }}</TableCell>
            <TableCell>
              <div class="flex flex-col">
                <span class="font-medium">{{ cp.problem.title }}</span>
                <span class="text-xs text-muted-foreground">{{ cp.problem.slug }}</span>
              </div>
            </TableCell>
            <TableCell>
              <Badge variant="outline" class="capitalize">
                {{ cp.problem.difficulty.toLowerCase() }}
              </Badge>
            </TableCell>
            <TableCell>{{ cp.score }}</TableCell>
            <TableCell>
              <Button
                v-if="canUpdate"
                variant="ghost"
                size="icon"
                class="h-8 w-8 text-destructive"
                @click="emit('removeProblem', cp.problem_id)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
          <TableRow v-if="!contest.problems?.length">
            <TableCell colspan="5" class="h-24 text-center text-muted-foreground">
              {{ $t('contests.detail.noProblemsAdded') }}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
