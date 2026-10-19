<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from '@ulticode/design-system/ui/select'
import {
  RefreshCw as IconRefresh,
  CircleX as IconCircleXFilled,
  Search as IconSearch,
} from '@lucide/vue'

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
      <IconSearch class="absolute left-2.5 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
      <Input
        :model-value="searchModelValue"
        @update:model-value="updateSearch"
        :placeholder="searchPlaceholder"
        class="pl-8 pr-8 !text-xs w-full"
      />
      <Button
        variant="outline"
        v-if="searchModelValue"
        @click="updateSearch('')"
        class="absolute right-2.5 focus:outline-none"
      >
        <IconCircleXFilled class="h-3.5 w-3.5" />
      </Button>
    </div>

    <Select
      v-for="(filter, index) in filters"
      :key="index"
      :model-value="filter.modelValue"
      @update:model-value="(value) => updateFilter(index, value as string)"
    >
      <SelectTrigger
        size="sm"
        :aria-label="filter.placeholder"
        :class="[
          'h-8 bg-muted border-border dark:border-border focus:border-primary',
          filter.width || 'w-[140px]',
        ]"
      >
        <SelectValue :placeholder="filter.placeholder">
          {{
            filter.options.find((option) => option.value === filter.modelValue)?.label ??
            filter.modelValue
          }}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectGroup
          ><SelectItem v-for="option in filter.options" :key="option.value" :value="option.value">
            {{ option.label }}
          </SelectItem></SelectGroup
        >
      </SelectContent>
    </Select>

    <Button
      v-if="onRefresh"
      variant="outline"
      size="icon"
      class="w-8"
      @click="onRefresh"
      :title="$t('common.refresh')"
    >
      <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
    </Button>

    <slot name="extra-actions" />
  </div>
</template>
