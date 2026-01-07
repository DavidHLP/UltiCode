<script setup lang="ts">
import { FormLabel, FormDescription } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'

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
      <FormLabel>Start Time</FormLabel>
      <Input
        type="datetime-local"
        :model-value="formData.start_time"
        @update:model-value="updateField('start_time', $event)"
      />
      <FormDescription>When the contest begins.</FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>Duration (Minutes)</FormLabel>
      <Input
        type="number"
        min="1"
        :model-value="formData.duration"
        @update:model-value="updateField('duration', Number($event))"
      />
      <FormDescription>Length of the contest in minutes.</FormDescription>
    </div>

    <div class="flex items-center space-x-2 border p-4 rounded-md bg-muted/20">
      <Switch
        id="is_published"
        :checked="formData.is_published"
        @update:checked="updateField('is_published', $event)"
      />
      <div class="space-y-1">
        <Label for="is_published">Publish Immediately</Label>
        <p class="text-sm text-muted-foreground">
          If enabled, the contest will be visible in the upcoming list immediately.
        </p>
      </div>
    </div>
  </div>
</template>
