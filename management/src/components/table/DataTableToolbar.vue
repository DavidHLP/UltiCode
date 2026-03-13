<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { IconRefresh, IconCircleXFilled } from '@tabler/icons-vue'

export interface FilterOption {
  value: string
  label: string
}

export interface Filter {
  modelValue: string
  placeholder: string
  options: FilterOption[]
  width?: string
}

defineProps<{
  searchModelValue: string
  searchPlaceholder: string
  searchWidth?: string
  filters?: Filter[]
  loading?: boolean
  onRefresh?: () => void
}>()

const emit = defineEmits<{
  'update:searchModelValue': [value: string]
  'update:filter': [index: number, value: string | number]
}>()

function updateSearch(value: string | number) {
  emit('update:searchModelValue', String(value))
}

function updateFilter(index: number, value: string | number) {
  emit('update:filter', index, value)
}
</script>

<template>
  <div class="flex items-center gap-2 flex-wrap">
    <Input
      :model-value="searchModelValue"
      @update:model-value="updateSearch"
      :placeholder="searchPlaceholder"
      :class="[
        'h-8 font-data text-xs border-[var(--silver-300)] focus:border-[var(--accent-electric)]',
        searchWidth || 'min-w-[150px] w-full lg:w-[250px]',
      ]"
    >
      <template #trailing>
        <button
          v-if="searchModelValue"
          @click="updateSearch('')"
          class="opacity-70 hover:opacity-100 text-[var(--silver-500)]"
        >
          <IconCircleXFilled class="h-3.5 w-3.5" />
        </button>
      </template>
    </Input>

    <Select
      v-for="(filter, index) in filters"
      :key="index"
      :model-value="filter.modelValue"
      @update:model-value="(value) => updateFilter(index, value as string)"
    >
      <SelectTrigger
        :class="[
          'h-8 font-data text-xs border-[var(--silver-300)] focus:border-[var(--accent-electric)]',
          filter.width || 'w-[140px]',
        ]"
      >
        <SelectValue :placeholder="filter.placeholder" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem v-for="option in filter.options" :key="option.value" :value="option.value">
          {{ option.label }}
        </SelectItem>
      </SelectContent>
    </Select>

    <Button
      v-if="onRefresh"
      variant="terminal"
      size="icon"
      class="h-8 w-8 border-[var(--silver-300)]"
      @click="onRefresh"
      :title="$t('common.refresh')"
    >
      <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
    </Button>

    <slot name="extra-actions" />
  </div>
</template>
