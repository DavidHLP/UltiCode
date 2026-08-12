<script setup lang="ts">
import type { Component } from 'vue'
import { Card } from '@/components/ui/card'

export interface AnalyticsOverviewItem {
  label: string
  value: string
  change?: string
  changeStyle?: Record<string, string>
}

export interface AnalyticsOverviewGroup {
  title: string
  icon: Component
  items: AnalyticsOverviewItem[]
}

defineProps<{
  groups: AnalyticsOverviewGroup[]
}>()
</script>

<template>
  <Card
    data-testid="analytics-overview"
    class="gap-0 py-0 overflow-hidden rounded-none border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/60 shadow-sm"
  >
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4">
      <section
        v-for="(group, groupIndex) in groups"
        :key="group.title"
        class="min-w-0 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/40 md:[&:nth-last-child(-n+2)]:border-b-0 xl:border-b-0 xl:border-r xl:last:border-r-0"
      >
        <header
          class="flex items-center gap-2 px-4 py-3 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]/40 bg-[var(--surface)] dark:bg-[var(--surface-highlight)]/10"
        >
          <component :is="group.icon" class="size-4 text-[var(--accent-primary)]" />
          <h2 class="text-xxs font-bold font-mono uppercase tracking-wide text-foreground">
            {{ group.title }}
          </h2>
          <span class="ml-auto text-2xs font-data text-[var(--foreground-muted)]">
            {{ String(groupIndex + 1).padStart(2, '0') }}
          </span>
        </header>

        <dl class="divide-y divide-[var(--surface-highlight)] dark:divide-[var(--border-subtle)]/20 px-4">
          <div
            v-for="item in group.items"
            :key="item.label"
            class="flex min-h-9 items-center justify-between gap-3 py-1.5"
          >
            <dt class="truncate text-xxs font-mono text-[var(--foreground-muted)]">
              {{ item.label }}
            </dt>
            <dd class="flex shrink-0 items-center gap-1.5 font-data tabular-nums">
              <span class="text-xs font-bold text-foreground">{{ item.value }}</span>
              <span
                v-if="item.change"
                class="border px-1 py-0.5 text-2xs font-bold font-mono"
                :style="item.changeStyle"
              >
                {{ item.change }}
              </span>
            </dd>
          </div>
        </dl>
      </section>
    </div>
  </Card>
</template>
