<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { Input } from '@ulticode/design-system/ui/input'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from '@ulticode/design-system/ui/select'
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
      <span class="text-sm text-muted-foreground">basic_info</span>
    </div>

    <!-- Title Field -->
    <div class="space-y-2">
      <FieldLabel>{{ t('contests.basics.title') }}</FieldLabel>
      <Input
        :model-value="slice.title"
        @update:model-value="updateTitle($event)"
        :placeholder="t('contests.basics.titlePlaceholder')"
      />
      <span class="text-sm text-muted-foreground text-xs">{{
        t('contests.basics.titleDescription')
      }}</span>
    </div>

    <!-- Slug Field -->
    <div class="space-y-2">
      <FieldLabel>{{ t('contests.basics.slug') }}</FieldLabel>
      <Input
        :model-value="slice.slug"
        @update:model-value="updateSlug($event)"
        :placeholder="t('contests.basics.slugPlaceholder')"
      />
      <span class="text-sm text-muted-foreground text-xs">{{
        t('contests.basics.slugDescription')
      }}</span>
    </div>

    <!-- Type Field -->
    <div class="space-y-2">
      <FieldLabel>{{ t('contests.basics.type') }}</FieldLabel>
      <Select
        :model-value="slice.contestType"
        @update:model-value="updateContestType($event as ContestType)"
      >
        <SelectTrigger>
          <SelectValue :placeholder="t('contests.basics.typePlaceholder')" />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup
            ><SelectItem
              v-for="fmt in CONTEST_FORMATS"
              :key="fmt"
              :value="fmt"
              class="cursor-pointer"
            >
              {{ t(`contests.basics.types.${fmt}`, fmt) }}
            </SelectItem></SelectGroup
          >
        </SelectContent>
      </Select>
      <span class="text-sm text-muted-foreground text-xs">{{
        t('contests.basics.typeDescription')
      }}</span>
    </div>

    <!-- Description Field -->
    <div class="space-y-2">
      <FieldLabel>{{ t('contests.basics.description') }}</FieldLabel>
      <Textarea
        :model-value="slice.description"
        @update:model-value="updateDescription($event)"
        :placeholder="t('contests.basics.descriptionPlaceholder')"
        rows="4"
        class="resize-none"
      />
    </div>
  </div>
</template>
