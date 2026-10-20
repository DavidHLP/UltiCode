<script setup lang="ts">
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@ulticode/design-system/ui/table'
import { Users as IconUsers } from '@lucide/vue'

import type { Contest } from '@/api/admin/contests'

defineProps<{
  contest: Contest
}>()
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center gap-2">
      <span class="text-sm text-muted-foreground text-xs"
        >[{{ contest.participantCount || 0 }}]</span
      >
    </div>

    <!-- Table - Terminal Style -->
    <div class="border border-border dark:border-foreground">
      <Table class="w-full">
        <TableHeader>
          <TableRow>
            <TableHead class="w-[60px]"> # </TableHead>
            <TableHead>
              {{ $t('contests.detail.user') }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-if="!contest.participantCount">
            <TableCell colspan="2" class="h-24 text-center">
              <span class="text-sm text-muted-foreground">{{
                $t('contests.detail.noParticipantsYet')
              }}</span>
            </TableCell>
          </TableRow>
          <TableRow v-else>
            <TableCell colspan="2" class="h-16 text-center">
              <div class="flex items-center justify-center gap-2">
                <IconUsers class="h-4 w-4 text-muted-foreground" />
                <span class="font-mono text-sm text-foreground tabular-nums">
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
