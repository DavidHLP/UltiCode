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
import { IconRefresh, IconCircleXFilled, IconSearch } from '@tabler/icons-vue'

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
    <div
      class="relative flex items-center"
      :class="searchWidth || 'min-w-[150px] w-full lg:w-[250px]'"
    >
      <IconSearch
        class="absolute left-2.5 h-3.5 w-3.5 text-[var(--foreground-muted)] pointer-events-none"
      />
      <Input
        variant="terminal"
        :model-value="searchModelValue"
        @update:model-value="updateSearch"
        :placeholder="searchPlaceholder"
        class="h-8 pl-8 pr-8 !text-xs w-full bg-[var(--surface-sunken)] border-[var(--border-subtle)] dark:border-[var(--border-subtle)] focus:border-[var(--primary)]"
      />
      <button
        v-if="searchModelValue"
        @click="updateSearch('')"
        class="absolute right-2.5 opacity-70 hover:opacity-100 text-[var(--foreground-muted)] focus:outline-none transition-opacity"
      >
        <IconCircleXFilled class="h-3.5 w-3.5" />
      </button>
    </div>

    <Select
      v-for="(filter, index) in filters"
      :key="index"
      :model-value="filter.modelValue"
      @update:model-value="(value) => updateFilter(index, value as string)"
    >
      <SelectTrigger
        variant="terminal"
        size="sm"
        :class="[
          'h-8 bg-[var(--surface-sunken)] border-[var(--border-subtle)] dark:border-[var(--border-subtle)] focus:border-[var(--primary)]',
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
      class="h-8 w-8 border-[var(--border-subtle)]"
      @click="onRefresh"
      :title="$t('common.refresh')"
    >
      <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
    </Button>

    <slot name="extra-actions" />
  </div>
</template>
