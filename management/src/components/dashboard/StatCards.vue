<script setup lang="ts">
import type { Component } from 'vue'
import { IconTrendingDown, IconTrendingUp, IconMinus } from '@tabler/icons-vue'

export interface StatItem {
  title: string
  value: string
  change: string
  trend: 'up' | 'down' | 'neutral'
  description: string
  icon?: Component
  href?: string
}

defineProps<{
  stats: StatItem[]
}>()

const getTrendIcon = (trend: string) => {
  switch (trend) {
    case 'up':
      return IconTrendingUp
    case 'down':
      return IconTrendingDown
    default:
      return IconMinus
  }
}

const getTrendStyles = (trend: string) => {
  switch (trend) {
    case 'up':
      return {
        icon: 'text-[var(--status-success)]',
        badge:
          'border-[var(--status-success)]/30 bg-[var(--status-success)]/10 text-[var(--status-success)]',
      }
    case 'down':
      return {
        icon: 'text-[var(--status-error)]',
        badge:
          'border-[var(--status-error)]/30 bg-[var(--status-error)]/10 text-[var(--status-error)]',
      }
    default:
      return {
        icon: 'text-[var(--silver-400)]',
        badge: 'border-[var(--silver-300)]/30 bg-[var(--silver-100)] text-[var(--silver-500)]',
      }
  }
}
</script>

<template>
  <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
    <component
      :is="stat.href ? 'a' : 'div'"
      v-for="(stat, index) in stats"
      :key="index"
      :href="stat.href"
      class="group relative overflow-hidden rounded-lg border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card p-5 shadow-float precision-card"
      :class="{ 'cursor-pointer': stat.href }"
    >
      <!-- Background decoration icon -->
      <div
        v-if="stat.icon"
        class="absolute -bottom-3 -right-3 h-20 w-20 opacity-[0.03] group-hover:opacity-[0.06] transition-opacity duration-300"
      >
        <component :is="stat.icon" class="h-full w-full" stroke-width="1" />
      </div>

      <!-- Card content -->
      <div class="relative z-10 space-y-3">
        <!-- Title -->
        <p
          class="text-xs font-medium uppercase tracking-widest text-[var(--silver-400)] dark:text-[var(--silver-500)]"
        >
          {{ stat.title }}
        </p>

        <!-- Value and trend -->
        <div class="flex items-baseline gap-3">
          <span class="text-3xl font-medium font-data tabular-nums tracking-tight text-foreground">
            {{ stat.value }}
          </span>

          <!-- Trend badge -->
          <span
            class="inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded border"
            :class="getTrendStyles(stat.trend).badge"
          >
            <component
              :is="getTrendIcon(stat.trend)"
              class="h-3 w-3"
              :class="getTrendStyles(stat.trend).icon"
              stroke-width="1.5"
            />
            {{ stat.change }}
          </span>
        </div>

        <!-- Divider -->
        <div class="precision-divider"></div>

        <!-- Description -->
        <p class="text-xs text-[var(--silver-400)] dark:text-[var(--silver-500)]">
          {{ stat.description }}
        </p>
      </div>
    </component>
  </div>
</template>
