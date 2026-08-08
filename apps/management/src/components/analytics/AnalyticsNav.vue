<script setup lang="ts">
import type { Component } from 'vue'
import { IconUsers, IconFileText, IconTrophy, IconServer } from '@tabler/icons-vue'

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
  { id: 'performance', label: 'analytics.nav.performance', icon: IconServer },
]

function selectItem(id: string) {
  emit('update:activeItem', id)
}
</script>

<template>
  <nav
    class="flex flex-col gap-1 border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 bg-card p-1 shadow-sm rounded-none"
  >
    <button
      v-for="item in items || defaultItems"
      :key="item.id"
      @click="selectItem(item.id)"
      class="group relative flex items-center gap-3 px-3 py-2.5 rounded-none text-left transition-all duration-200"
      :class="
        activeItem === item.id
          ? 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]/45 border border-[var(--accent-primary)]/40'
          : 'border border-transparent hover:bg-[var(--silver-100)]/50 dark:hover:bg-[var(--silver-800)]/20 hover:border-[var(--silver-200)] dark:hover:border-[var(--silver-300)]/40'
      "
    >
      <!-- Active indicator -->
      <div
        v-if="activeItem === item.id"
        class="absolute left-0 top-0 w-1 h-full rounded-none bg-[var(--accent-primary)]"
      />

      <!-- Icon -->
      <div
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-none border transition-colors duration-200"
        :class="
          activeItem === item.id
            ? 'border-[var(--accent-primary)] bg-[var(--accent-primary)]/10 text-[var(--accent-primary)]'
            : 'border-[var(--silver-200)] dark:border-[var(--silver-300)] text-[var(--silver-400)] group-hover:border-[var(--silver-300)] group-hover:text-foreground'
        "
      >
        <component :is="item.icon" class="h-4 w-4" stroke-width="1.5" />
      </div>

      <!-- Label -->
      <span
        class="text-sm font-medium transition-colors duration-200"
        :class="
          activeItem === item.id
            ? 'text-[var(--accent-primary)] font-semibold'
            : 'text-[var(--silver-500)] group-hover:text-foreground'
        "
      >
        {{ $t(item.label) }}
      </span>
    </button>
  </nav>
</template>
