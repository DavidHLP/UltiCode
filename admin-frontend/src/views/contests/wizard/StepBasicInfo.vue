<script setup lang="ts">
import { FormLabel, FormDescription } from '@/components/ui/form'
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
import { useI18n } from 'vue-i18n'

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

const { t } = useI18n()

function updateField(field: string, value: string | number | bigint | ContestType | null) {
  if (value === null) return

  emit('update:formData', {
    ...props.formData,
    [field]: value,
  })
}
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-2">
      <FormLabel>{{ t('contests.basics.title') }}</FormLabel>
      <Input
        :model-value="formData.title"
        @update:model-value="updateField('title', $event)"
        :placeholder="t('contests.basics.titlePlaceholder')"
      />
      <FormDescription>{{ t('contests.basics.titleDescription') }}</FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>{{ t('contests.basics.slug') }}</FormLabel>
      <Input
        :model-value="formData.slug"
        @update:model-value="updateField('slug', $event)"
        :placeholder="t('contests.basics.slugPlaceholder')"
      />
      <FormDescription>{{ t('contests.basics.slugDescription') }}</FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>{{ t('contests.basics.type') }}</FormLabel>
      <Select
        :model-value="formData.type"
        @update:model-value="updateField('type', $event as ContestType)"
      >
        <SelectTrigger>
          <SelectValue :placeholder="t('contests.basics.typePlaceholder')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem :value="ContestType.PUBLIC">{{
            t('contests.basics.type.PUBLIC')
          }}</SelectItem>
          <SelectItem :value="ContestType.PRIVATE">{{
            t('contests.basics.type.PRIVATE')
          }}</SelectItem>
          <SelectItem :value="ContestType.VIRTUAL">{{
            t('contests.basics.type.VIRTUAL')
          }}</SelectItem>
        </SelectContent>
      </Select>
      <FormDescription>
        {{ t('contests.basics.typeDescription') }}
      </FormDescription>
    </div>

    <div class="space-y-2">
      <FormLabel>{{ t('contests.basics.description') }}</FormLabel>
      <Textarea
        :model-value="formData.description"
        @update:model-value="updateField('description', $event)"
        :placeholder="t('contests.basics.descriptionPlaceholder')"
        rows="4"
      />
    </div>
  </div>
</template>
