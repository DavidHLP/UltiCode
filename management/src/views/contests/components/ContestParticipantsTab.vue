<script setup lang="ts">
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { IconUsers } from '@tabler/icons-vue'
import type { Contest } from '@/api/admin/contests'

defineProps<{
  contest: Contest
}>()
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center gap-2">

      <span class="terminal-comment text-xs">[{{ contest.participantCount || 0 }}]</span>
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
              {{ $t('contests.detail.user') }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!contest.participantCount">
            <TableCell colspan="2" class="h-24 text-center">
              <span class="terminal-comment">{{ $t('contests.detail.noParticipantsYet') }}</span>
            </TableCell>
          </TableRow>
          <TableRow v-else>
            <TableCell colspan="2" class="h-16 text-center">
              <div class="flex items-center justify-center gap-2">
                <IconUsers class="h-4 w-4 text-[var(--silver-400)]" />
                <span class="font-data text-sm text-[var(--foreground)] tabular-nums">
                  {{ contest.participantCount }} {{ $t('contests.detail.participants') }}
                </span>
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
