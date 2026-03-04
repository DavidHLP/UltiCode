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
      <span class="terminal-prompt text-sm">participants</span>
      <span class="terminal-comment text-xs">[{{ contest.participants?.length || 0 }}]</span>
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-[var(--silver-200)] dark:border-[var(--silver-700)]">
      <Table class="terminal-table">
        <TableHeader>
          <TableRow
            class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
          >
            <TableHead
              class="font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)] w-[60px]"
            >
              #
            </TableHead>
            <TableHead
              class="font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]"
            >
              {{ $t('contests.detail.user') }}
            </TableHead>
            <TableHead
              class="font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]"
            >
              {{ $t('contests.detail.joinedAt') }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="(p, index) in contest.participants"
            :key="p.id"
            class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)]"
          >
            <TableCell class="font-data text-xs text-[var(--silver-400)]">
              {{ String(index + 1).padStart(2, '0') }}
            </TableCell>
            <TableCell>
              <div class="flex items-center gap-3">
                <div
                  class="h-8 w-8 border border-[var(--silver-200)] dark:border-[var(--silver-700)] flex items-center justify-center bg-[var(--surface-sunken)]"
                >
                  <IconUsers class="h-4 w-4 text-[var(--silver-400)]" />
                </div>
                <div class="flex flex-col gap-0.5">
                  <span class="font-medium text-sm text-[var(--foreground)]">{{
                    p.user.username
                  }}</span>
                  <span v-if="p.user.name" class="font-data text-xs text-[var(--silver-400)]">
                    {{ p.user.name }}
                  </span>
                </div>
              </div>
            </TableCell>
            <TableCell>
              <span class="font-data text-xs text-[var(--silver-400)]">—</span>
            </TableCell>
          </TableRow>
          <TableRow v-if="!contest.participants?.length">
            <TableCell colspan="3" class="h-24 text-center">
              <span class="terminal-comment">{{ $t('contests.detail.noParticipantsYet') }}</span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
