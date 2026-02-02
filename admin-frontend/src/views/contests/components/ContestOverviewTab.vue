<script setup lang="ts">
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { IconCalendar, IconClock, IconTrophy, IconUsers } from '@tabler/icons-vue'
import type { Contest } from '@/api/admin/contests'

defineProps<{
  contest: Contest
}>()
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-6 md:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle class="text-lg">{{ $t('contests.detail.details') }}</CardTitle>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-1">
            <span class="text-sm font-medium text-muted-foreground">{{
              $t('contests.detail.description')
            }}</span>
            <p class="text-sm whitespace-pre-wrap">
              {{ contest.description || $t('contests.detail.noDescription') }}
            </p>
          </div>
          <Separator />
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span class="text-muted-foreground">{{ $t('contests.detail.slug') }}</span>
              <p class="font-mono">{{ contest.slug }}</p>
            </div>
            <div>
              <span class="text-muted-foreground">{{ $t('contests.detail.visibility') }}</span>
              <p>
                {{
                  contest.is_visible
                    ? $t('contests.detail.published')
                    : $t('contests.detail.hidden')
                }}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle class="text-lg">{{ $t('contests.detail.statsAndSchedule') }}</CardTitle>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <IconCalendar class="h-4 w-4 text-muted-foreground" />
              <span class="text-sm">{{ $t('contests.detail.startTime') }}</span>
            </div>
            <span class="text-sm font-medium">
              {{ new Date(contest.start_time).toLocaleString() }}
            </span>
          </div>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <IconClock class="h-4 w-4 text-muted-foreground" />
              <span class="text-sm">{{ $t('contests.detail.duration') }}</span>
            </div>
            <span class="text-sm font-medium"
              >{{ contest.duration_minutes }} {{ $t('common.minutes') }}</span
            >
          </div>
          <Separator />
          <div class="grid grid-cols-2 gap-4 pt-2">
            <div class="flex flex-col items-center p-3 bg-muted/30 rounded-lg">
              <IconTrophy class="h-5 w-5 text-yellow-500 mb-1" />
              <span class="text-2xl font-bold">{{ contest.problems?.length || 0 }}</span>
              <span class="text-xs text-muted-foreground">{{ $t('contests.detail.problems') }}</span>
            </div>
            <div class="flex flex-col items-center p-3 bg-muted/30 rounded-lg">
              <IconUsers class="h-5 w-5 text-blue-500 mb-1" />
              <span class="text-2xl font-bold">{{ contest.participant_count || 0 }}</span>
              <span class="text-xs text-muted-foreground">{{
                $t('contests.detail.participants')
              }}</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
