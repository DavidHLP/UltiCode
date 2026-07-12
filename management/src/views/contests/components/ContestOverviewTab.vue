<script setup lang="ts">
import { IconCalendar, IconClock, IconTrophy, IconUsers } from '@tabler/icons-vue'
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
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">details</span>
        </div>
        <div class="p-4 space-y-4">
          <div class="space-y-2">
            <span class="terminal-label">{{ $t('contests.detail.description') }}</span>
            <p class="text-sm text-[var(--foreground)] whitespace-pre-wrap font-data">
              {{ contest.description || $t('contests.detail.noDescription') }}
            </p>
          </div>
          <div class="border-t border-[var(--silver-200)] dark:border-[var(--silver-700)] pt-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <span class="terminal-label">{{ $t('contests.detail.slug') }}</span>
                <p class="font-data text-sm text-[var(--terminal-cyan)]">{{ contest.slug }}</p>
              </div>
              <div>
                <span class="terminal-label">{{ $t('contests.detail.visibility') }}</span>
                <p class="font-data text-sm">
                  <span v-if="contest.isVisible" class="text-[var(--terminal-green)]">
                    {{ $t('contests.detail.statusPublished') }}
                  </span>
                  <span v-else class="text-[var(--silver-400)]">{{
                    $t('contests.detail.statusHidden')
                  }}</span>
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Stats & Schedule Card - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">stats_and_schedule</span>
        </div>
        <div class="p-4 space-y-4">
          <!-- Schedule Info -->
          <div class="space-y-3">
            <div
              class="flex items-center justify-between border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] pb-2"
            >
              <div class="flex items-center gap-2">
                <IconCalendar class="h-4 w-4 text-[var(--silver-400)]" />
                <span class="font-data text-xs text-[var(--silver-400)] uppercase">{{
                  $t('contests.detail.startTime')
                }}</span>
              </div>
              <span class="font-data text-sm tabular-nums">
                {{ formatDateTimeByLocale(contest.startTime) }}
              </span>
            </div>
            <div
              class="flex items-center justify-between border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] pb-2"
            >
              <div class="flex items-center gap-2">
                <IconClock class="h-4 w-4 text-[var(--silver-400)]" />
                <span class="font-data text-xs text-[var(--silver-400)] uppercase">{{
                  $t('contests.detail.duration')
                }}</span>
              </div>
              <span class="font-data text-sm tabular-nums">
                {{ contest.duration }} {{ $t('common.minutes') }}
              </span>
            </div>
          </div>

          <!-- Stats Grid -->
          <div class="grid grid-cols-2 gap-3 pt-2">
            <div
              class="flex items-center gap-3 p-3 border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
            >
              <IconTrophy class="h-5 w-5 text-[var(--terminal-amber)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ contest.problemCount || 0 }}
                </div>
                <div class="terminal-label">{{ $t('contests.detail.problems') }}</div>
              </div>
            </div>
            <div
              class="flex items-center gap-3 p-3 border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
            >
              <IconUsers class="h-5 w-5 text-[var(--terminal-cyan)]" />
              <div>
                <div class="font-data text-lg tabular-nums text-[var(--foreground)]">
                  {{ contest.participantCount || 0 }}
                </div>
                <div class="terminal-label">{{ $t('contests.detail.participants') }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
