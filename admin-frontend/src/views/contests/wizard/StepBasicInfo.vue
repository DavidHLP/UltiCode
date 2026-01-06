<script setup lang="ts">
import {
  FormLabel,
  FormDescription,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ContestType } from '@/api/admin/contests'

// Define the form fields/types expected by parent form context
// But typically wizard steps are just parts of a larger form or define their own schema
// Using simple props/emits for now, or assume provide/inject of form context if using VeeValidate across steps
// For simplicity: props bound to v-model of the formData part

const props = defineProps<{
  formData: {
    title: string
    slug: string
    description: string
    type: ContestType
    [key: string]: unknown
  }
}>()

const emit = defineEmits<{
  (e: 'update:formData', value: unknown): void
}>()

function updateField(field: string, value: string | ContestType) {
  emit('update:formData', {
    ...props.formData,
    [field]: value,
  })
}
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-2">
      <FormLabel>Title</FormLabel>
      <Input
        :model-value="formData.title"
        @update:model-value="updateField('title', $event)"
        placeholder="Weekly Contest 101"
      />
      <FormDescription>The display name of the contest.</FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>Slug</FormLabel>
      <Input
        :model-value="formData.slug"
        @update:model-value="updateField('slug', $event)"
        placeholder="weekly-contest-101"
      />
      <FormDescription>Unique URL identifier for the contest.</FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>Type</FormLabel>
      <Select
        :model-value="formData.type"
        @update:model-value="updateField('type', $event)"
      >
        <SelectTrigger>
          <SelectValue placeholder="Select type" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem :value="ContestType.PUBLIC">Public</SelectItem>
          <SelectItem :value="ContestType.PRIVATE">Private</SelectItem>
          <SelectItem :value="ContestType.VIRTUAL">Virtual</SelectItem>
        </SelectContent>
      </Select>
      <FormDescription>
        Public contests are visible to everyone. Private requires invitation.
      </FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>Description</FormLabel>
      <Textarea
        :model-value="formData.description"
        @update:model-value="updateField('description', $event)"
        placeholder="Contest details and rules..."
        rows="4"
      />
    </div>
  </div>
</template>
