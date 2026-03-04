<script setup lang="ts">
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  formData: {
    start_time: string
    duration: number
    is_published: boolean
    [key: string]: unknown
  }
}>()

const emit = defineEmits<{
  (e: 'update:formData', value: unknown): void
}>()

const { t } = useI18n()

function updateField(field: string, value: string | number | boolean) {
  emit('update:formData', {
    ...props.formData,
    [field]: value,
  })
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
        :model-value="formData.start_time"
        @update:model-value="updateField('start_time', $event)"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
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
        :model-value="formData.duration"
        @update:model-value="updateField('duration', Number($event))"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
      />
      <span class="terminal-comment text-xs">{{
        t('contests.scheduleStep.durationDescription')
      }}</span>
    </div>

    <!-- Publish Toggle - Terminal Style -->
    <div
      class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] p-4 bg-[var(--surface-sunken)]"
    >
      <div class="flex items-center gap-4">
        <Switch
          id="is_published"
          :checked="formData.is_published"
          @update:checked="updateField('is_published', $event)"
          class="data-[state=checked]:bg-[var(--terminal-green)]"
        />
        <div class="space-y-1">
          <Label for="is_published" class="font-data text-xs uppercase tracking-wider">
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
