<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'

import type { Component } from 'vue'
import {
  Users as IconUsers,
  FileText as IconFileText,
  Trophy as IconTrophy,
  Server as IconServer,
} from '@lucide/vue'

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
    class="flex flex-col gap-1 border border-border dark:border-border/60 bg-card p-1 shadow-sm rounded-lg"
  >
    <Button
      variant="outline"
      v-for="item in items || defaultItems"
      :key="item.id"
      @click="selectItem(item.id)"
      class="group relative flex items-center gap-3 px-3 py-2.5 text-left"
      :class="
        activeItem === item.id
          ? 'bg-accent dark:bg-foreground/45 border border-primary/40'
          : 'border border-transparent hover:bg-accent/50 dark:hover:bg-foreground/20 hover:border-border dark:hover:border-border/40'
      "
    >
      <!-- Active indicator -->
      <div
        v-if="activeItem === item.id"
        class="absolute left-0 top-0 w-1 h-full rounded-lg bg-primary"
      />

      <!-- Icon -->
      <div
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border transition-colors duration-200"
        :class="
          activeItem === item.id
            ? 'border-primary bg-primary/10 text-primary'
            : 'border-border dark:border-border text-muted-foreground group-hover:border-border group-hover:text-foreground'
        "
      >
        <component :is="item.icon" class="h-4 w-4" stroke-width="1.5" />
      </div>

      <!-- Label -->
      <span
        class="text-sm font-medium transition-colors duration-200"
        :class="
          activeItem === item.id
            ? 'text-primary font-semibold'
            : 'text-muted-foreground group-hover:text-foreground'
        "
      >
        {{ $t(item.label) }}
      </span>
    </Button>
  </nav>
</template>
