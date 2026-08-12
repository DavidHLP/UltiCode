<script setup lang="ts">
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { useI18n } from 'vue-i18n'
import type { ScheduleSlice, SchedulePatch } from './useContestAuthoring'

defineProps<{ slice: ScheduleSlice }>()
const emit = defineEmits<{ (e: 'patch', patch: SchedulePatch): void }>()

const { t } = useI18n()

function updateStartTime(value: string | number): void {
  if (typeof value === 'string') emit('patch', { startTimeLocal: value })
}

function updateDuration(value: string | number): void {
  const n = Number(value)
  if (Number.isFinite(n)) emit('patch', { duration: n })
}

function updateIsPublished(value: boolean): void {
  emit('patch', { isPublished: value })
}
</script>

<template>
  <div class="space-y-6">
    <!-- Section Header -->
    <div class="flex items-center gap-2 mb-4">
      <span class="terminal-comment">schedule_config</span>
    </div>

    <!-- Start Time Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.scheduleStep.startTime') }}</label>
      <Input
        type="datetime-local"
        :model-value="slice.startTimeLocal"
        @update:model-value="updateStartTime($event)"
        class="border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] font-data text-sm focus:border-[var(--primary)]"
      />
      <span class="terminal-comment text-xs">{{
        t('contests.scheduleStep.startTimeDescription')
      }}</span>
    </div>

    <!-- Duration Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.scheduleStep.duration') }}</label>
      <Input
        type="number"
        min="1"
        :model-value="slice.duration"
        @update:model-value="updateDuration($event)"
        class="border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] font-data text-sm focus:border-[var(--primary)]"
      />
      <span class="terminal-comment text-xs">{{
        t('contests.scheduleStep.durationDescription')
      }}</span>
    </div>

    <!-- Publish Toggle - Terminal Style -->
    <div
      class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] p-4 bg-[var(--surface-sunken)]"
    >
      <div class="flex items-center gap-4">
        <Switch
          id="is_published"
          :checked="slice.isPublished"
          @update:checked="updateIsPublished($event)"
          class="data-[state=checked]:bg-[var(--status-success-mark)]"
        />
        <div class="space-y-1">
          <Label for="isPublished" class="font-data text-xs uppercase tracking-wider">
            {{ t('contests.scheduleStep.publishImmediately') }}
          </Label>
          <p class="terminal-comment text-xs">
            {{ t('contests.scheduleStep.publishImmediatelyDescription') }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
