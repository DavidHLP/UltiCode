<script setup lang="ts">
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { Contest } from '@/api/admin/contests'

defineProps<{
  contest: Contest
}>()
</script>

<template>
  <div class="border rounded-md">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{{ $t('contests.detail.user') }}</TableHead>
          <TableHead>{{ $t('contests.detail.joinedAt') }}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow v-for="p in contest.participants" :key="p.id">
          <TableCell class="font-medium">
            <div class="flex items-center gap-2">
              <span>{{ p.user.username }}</span>
              <span v-if="p.user.name" class="text-muted-foreground"> ({{ p.user.name }}) </span>
            </div>
          </TableCell>
          <TableCell class="text-muted-foreground"> - </TableCell>
        </TableRow>
        <TableRow v-if="!contest.participants?.length">
          <TableCell colspan="2" class="h-24 text-center text-muted-foreground">
            {{ $t('contests.detail.noParticipantsYet') }}
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  </div>
</template>
