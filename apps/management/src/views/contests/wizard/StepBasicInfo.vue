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
import type { BasicInfoSlice, BasicInfoPatch } from './useContestAuthoring'

const CONTEST_FORMATS: ContestType[] = [ContestType.ICPC, ContestType.IOI, ContestType.CUSTOM]

defineProps<{ slice: BasicInfoSlice }>()
const emit = defineEmits<{ (e: 'patch', patch: BasicInfoPatch): void }>()

const { t } = useI18n()

function updateTitle(value: string | number): void {
  if (typeof value === 'string') emit('patch', { title: value })
}

function updateSlug(value: string | number): void {
  if (typeof value === 'string') emit('patch', { slug: value })
}

function updateDescription(value: string | number): void {
  if (typeof value === 'string') emit('patch', { description: value })
}

function updateContestType(value: ContestType): void {
  emit('patch', { contestType: value })
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
        :model-value="slice.title"
        @update:model-value="updateTitle($event)"
        :placeholder="t('contests.basics.titlePlaceholder')"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
      />
      <span class="terminal-comment text-xs">{{ t('contests.basics.titleDescription') }}</span>
    </div>

    <!-- Slug Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.slug') }}</label>
      <Input
        :model-value="slice.slug"
        @update:model-value="updateSlug($event)"
        :placeholder="t('contests.basics.slugPlaceholder')"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
      />
      <span class="terminal-comment text-xs">{{ t('contests.basics.slugDescription') }}</span>
    </div>

    <!-- Type Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.type') }}</label>
      <Select
        :model-value="slice.contestType"
        @update:model-value="updateContestType($event as ContestType)"
      >
        <SelectTrigger
          class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm"
        >
          <SelectValue :placeholder="t('contests.basics.typePlaceholder')" />
        </SelectTrigger>
        <SelectContent class="border-[var(--silver-200)] dark:border-[var(--silver-700)]">
          <SelectItem
            v-for="fmt in CONTEST_FORMATS"
            :key="fmt"
            :value="fmt"
            class="font-data text-xs cursor-pointer"
          >
            {{ t(`contests.basics.types.${fmt}`, fmt) }}
          </SelectItem>
        </SelectContent>
      </Select>
      <span class="terminal-comment text-xs">{{ t('contests.basics.typeDescription') }}</span>
    </div>

    <!-- Description Field -->
    <div class="space-y-2">
      <label class="terminal-label">{{ t('contests.basics.description') }}</label>
      <Textarea
        :model-value="slice.description"
        @update:model-value="updateDescription($event)"
        :placeholder="t('contests.basics.descriptionPlaceholder')"
        rows="4"
        class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)] resize-none"
      />
    </div>
  </div>
</template>
