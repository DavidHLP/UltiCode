<script setup lang="ts">
import { FormLabel, FormDescription } from '@/components/ui/form'
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
    <div class="space-y-2">
      <FormLabel>{{ t('contests.scheduleStep.startTime') }}</FormLabel>
      <Input
        type="datetime-local"
        :model-value="formData.start_time"
        @update:model-value="updateField('start_time', $event)"
      />
      <FormDescription>{{ t('contests.scheduleStep.startTimeDescription') }}</FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>{{ t('contests.scheduleStep.duration') }}</FormLabel>
      <Input
        type="number"
        min="1"
        :model-value="formData.duration"
        @update:model-value="updateField('duration', Number($event))"
      />
      <FormDescription>{{ t('contests.scheduleStep.durationDescription') }}</FormDescription>
    </div>

    <div class="flex items-center space-x-2 border p-4 rounded-md bg-muted/20">
      <Switch
        id="is_published"
        :checked="formData.is_published"
        @update:checked="updateField('is_published', $event)"
      />
      <div class="space-y-1">
        <Label for="is_published">{{ t('contests.scheduleStep.publishImmediately') }}</Label>
        <p class="text-sm text-muted-foreground">
          {{ t('contests.scheduleStep.publishImmediatelyDescription') }}
        </p>
      </div>
    </div>
  </div>
</template>
