<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { Input } from '@ulticode/design-system/ui/input'
import { Switch } from '@ulticode/design-system/ui/switch'
import { Label } from '@ulticode/design-system/ui/label'
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
      <span class="text-sm text-muted-foreground">schedule_config</span>
    </div>

    <!-- Start Time Field -->
    <div class="space-y-2">
      <FieldLabel>{{ t('contests.scheduleStep.startTime') }}</FieldLabel>
      <Input
        type="datetime-local"
        :model-value="slice.startTimeLocal"
        @update:model-value="updateStartTime($event)"
      />
      <span class="text-sm text-muted-foreground text-xs">{{
        t('contests.scheduleStep.startTimeDescription')
      }}</span>
    </div>

    <!-- Duration Field -->
    <div class="space-y-2">
      <FieldLabel>{{ t('contests.scheduleStep.duration') }}</FieldLabel>
      <Input
        type="number"
        min="1"
        :model-value="slice.duration"
        @update:model-value="updateDuration($event)"
      />
      <span class="text-sm text-muted-foreground text-xs">{{
        t('contests.scheduleStep.durationDescription')
      }}</span>
    </div>

    <!-- Publish Toggle - Terminal Style -->
    <div class="border border-border dark:border-foreground p-4 bg-muted">
      <div class="flex items-center gap-4">
        <Switch
          id="is_published"
          :checked="slice.isPublished"
          @update:checked="updateIsPublished($event)"
        />
        <div class="space-y-1">
          <Label for="isPublished">
            {{ t('contests.scheduleStep.publishImmediately') }}
          </Label>
          <p class="text-sm text-muted-foreground text-xs">
            {{ t('contests.scheduleStep.publishImmediatelyDescription') }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
