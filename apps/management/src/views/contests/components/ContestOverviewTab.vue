<script setup lang="ts">
import {
  Calendar as IconCalendar,
  Clock as IconClock,
  Trophy as IconTrophy,
  Users as IconUsers,
} from '@lucide/vue'

import type { Contest } from '@/api/admin/contests'
import { formatDateTimeByLocale } from '@/i18n/utils'

defineProps<{
  contest: Contest
}>()
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-6 md:grid-cols-2">
      <!-- Details Card - Terminal Style -->
      <div class="border border-border dark:border-foreground bg-card">
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">details</span>
        </div>
        <div class="p-4 space-y-4">
          <div class="space-y-2">
            <span class="text-sm font-medium">{{ $t('contests.detail.description') }}</span>
            <p class="text-sm text-foreground whitespace-pre-wrap font-mono">
              {{ contest.description || $t('contests.detail.noDescription') }}
            </p>
          </div>
          <div class="border-t border-border dark:border-foreground pt-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <span class="text-sm font-medium">{{ $t('contests.detail.slug') }}</span>
                <p class="font-mono text-sm text-foreground">{{ contest.slug }}</p>
              </div>
              <div>
                <span class="text-sm font-medium">{{ $t('contests.detail.visibility') }}</span>
                <p class="font-mono text-sm">
                  <span v-if="contest.isVisible" class="text-foreground">
                    {{ $t('contests.detail.statusPublished') }}
                  </span>
                  <span v-else class="text-muted-foreground">{{
                    $t('contests.detail.statusHidden')
                  }}</span>
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Stats & Schedule Card - Terminal Style -->
      <div class="border border-border dark:border-foreground bg-card">
        <div class="border-b border-border dark:border-foreground px-4 py-2 bg-muted">
          <span class="text-sm text-muted-foreground">stats_and_schedule</span>
        </div>
        <div class="p-4 space-y-4">
          <!-- Schedule Info -->
          <div class="space-y-3">
            <div
              class="flex items-center justify-between border-b border-accent dark:border-foreground pb-2"
            >
              <div class="flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-muted-foreground" />
                <span class="font-mono text-xs text-muted-foreground uppercase">{{
                  $t('contests.detail.startTime')
                }}</span>
              </div>
              <span class="font-mono text-sm tabular-nums">
                {{ formatDateTimeByLocale(contest.startTime) }}
              </span>
            </div>
            <div
              class="flex items-center justify-between border-b border-accent dark:border-foreground pb-2"
            >
              <div class="flex items-center gap-2">
                <IconClock class="h-4 w-4 text-muted-foreground" />
                <span class="font-mono text-xs text-muted-foreground uppercase">{{
                  $t('contests.detail.duration')
                }}</span>
              </div>
              <span class="font-mono text-sm tabular-nums">
                {{ contest.duration }} {{ $t('common.minutes') }}
              </span>
            </div>
          </div>

          <!-- Stats Grid -->
          <div class="grid grid-cols-2 gap-3 pt-2">
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <IconTrophy class="h-5 w-5 text-status-warning-mark" />
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ contest.problemCount || 0 }}
                </div>
                <div class="text-sm font-medium">{{ $t('contests.detail.problems') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-border dark:border-foreground bg-muted"
            >
              <IconUsers class="h-5 w-5 text-status-info-mark" />
              <div>
                <div class="font-mono text-lg tabular-nums text-foreground">
                  {{ contest.participantCount || 0 }}
                </div>
                <div class="text-sm font-medium">{{ $t('contests.detail.participants') }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
