<script setup lang="ts">
import type { Component } from 'vue'
import { IconUsers, IconFileText, IconTrophy, IconCreditCard, IconServer } from '@tabler/icons-vue'

export interface NavItem {
  id: string
  label: string
  icon: Component
}

defineProps<{
  activeItem: string
  items?: NavItem[]
}>()

const emit = defineEmits<{
  (e: 'update:activeItem', value: string): void
}>()

const defaultItems: NavItem[] = [
  { id: 'user_activity', label: 'analytics.nav.userActivity', icon: IconUsers },
  { id: 'problem_completion', label: 'analytics.nav.problemCompletion', icon: IconFileText },
  { id: 'contest_participation', label: 'analytics.nav.contestParticipation', icon: IconTrophy },
  { id: 'revenue', label: 'analytics.nav.revenue', icon: IconCreditCard },
  { id: 'performance', label: 'analytics.nav.performance', icon: IconServer },
]

function selectItem(id: string) {
  emit('update:activeItem', id)
}
</script>

<template>
  <nav class="flex flex-col gap-1">
    <button
      v-for="item in (items || defaultItems)"
      :key="item.id"
      @click="selectItem(item.id)"
      class="group relative flex items-center gap-3 px-3 py-2.5 rounded-lg text-left transition-all duration-200"
      :class="
        activeItem === item.id
          ? 'bg-[var(--accent-primary)]/10 border border-[var(--accent-primary)]/30'
          : 'border border-transparent hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]/50 hover:border-[var(--silver-200)] dark:hover:border-[var(--silver-300)]'
      "
    >
      <!-- Active indicator -->
      <div
        v-if="activeItem === item.id"
        class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-6 rounded-r bg-[var(--accent-primary)]"
      />

      <!-- Icon -->
      <div
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border transition-colors duration-200"
        :class="
          activeItem === item.id
            ? 'border-[var(--accent-primary)]/40 bg-[var(--accent-primary)]/10 text-[var(--accent-primary)]'
            : 'border-[var(--silver-200)] dark:border-[var(--silver-300)] text-[var(--silver-400)] group-hover:border-[var(--silver-300)] group-hover:text-[var(--silver-500)]'
        "
      >
        <component :is="item.icon" class="h-4 w-4" stroke-width="1.5" />
      </div>

      <!-- Label -->
      <span
        class="text-sm font-medium transition-colors duration-200"
        :class="
          activeItem === item.id
            ? 'text-foreground'
            : 'text-[var(--silver-500)] group-hover:text-foreground'
        "
      >
        {{ $t(item.label) }}
      </span>
    </button>
  </nav>
</template>
