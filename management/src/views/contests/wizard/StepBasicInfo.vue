<script setup lang="ts">
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
  <div class="space-y-6">
    <!-- Section Header -->
    <div class="flex items-center gap-2 mb-4">
      <span class="terminal-comment">basic_info</span>
    </div>

    <!-- Title Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.title') }}</label>
      <Input
        :model-value="formData.title"
        @update:model-value="updateField('title', $event)"
        :placeholder="t('contests.basics.titlePlaceholder')"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
      />
      <span class="terminal-comment text-xs">{{ t('contests.basics.titleDescription') }}</span>
    </div>

    <!-- Slug Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.slug') }}</label>
      <Input
        :model-value="formData.slug"
        @update:model-value="updateField('slug', $event)"
        :placeholder="t('contests.basics.slugPlaceholder')"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
      />
      <span class="terminal-comment text-xs">{{ t('contests.basics.slugDescription') }}</span>
    </div>

    <!-- Type Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.type') }}</label>
      <Select
        :model-value="formData.type"
        @update:model-value="updateField('type', $event as ContestType)"
      >
        <SelectTrigger
          class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm"
        >
          <SelectValue :placeholder="t('contests.basics.typePlaceholder')" />
        </SelectTrigger>
        <SelectContent class="border-[var(--silver-200)] dark:border-[var(--silver-700)]">
          <SelectItem
            v-for="type in [ContestType.PUBLIC, ContestType.PRIVATE, ContestType.VIRTUAL]"
            :key="type"
            :value="type"
            class="font-data text-xs cursor-pointer"
          >
            {{ t(`contests.basics.types.${type}`) }}
          </SelectItem>
        </SelectContent>
      </Select>
      <span class="terminal-comment text-xs">{{ t('contests.basics.typeDescription') }}</span>
    </div>

    <!-- Description Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.description') }}</label>
      <Textarea
        :model-value="formData.description"
        @update:model-value="updateField('description', $event)"
        :placeholder="t('contests.basics.descriptionPlaceholder')"
        rows="4"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)] resize-none"
      />
    </div>
  </div>
</template>
