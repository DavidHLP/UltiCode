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

const getCardAccent = (index: number) => {
  switch (index) {
    case 0: // Total Users
      return 'var(--solarized-blue)'
    case 1: // Total Problems
      return 'var(--solarized-cyan)'
    case 2: // Active Contests
      return 'var(--solarized-yellow)'
    case 3: // Flagged Content
      return 'var(--status-error)'
    default:
      return 'var(--silver-400)'
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
      data-testid="dashboard-stat-card"
      class="dashboard-stat-card group relative block overflow-hidden rounded-none p-0"
      :class="{ 'cursor-pointer': stat.href }"
      :style="{ '--stat-accent': getCardAccent(index) }"
    >
      <!-- Retro Terminal Window Header -->
      <div
        class="dashboard-stat-card__header flex items-center justify-between px-3 py-1.5 font-mono text-2xs uppercase tracking-wider font-bold"
      >
        <div class="flex items-center gap-1 shrink-0">
          <span class="dashboard-stat-card__dot size-1.5"></span>
          <span class="dashboard-stat-card__dot size-1.5"></span>
          <span class="dashboard-stat-card__dot size-1.5"></span>
        </div>
        <span class="dashboard-stat-card__title truncate mx-2">{{ stat.title }}</span>
        <span class="dashboard-stat-card__version select-none shrink-0 font-normal">SYS_V1.0</span>
      </div>

      <!-- Card content -->
      <div class="p-4 space-y-3.5">
        <div class="flex items-center justify-between gap-4">
          <div class="space-y-1">
            <!-- Numeric Value -->
            <span
              class="dashboard-stat-card__value text-3.5xl font-extrabold font-data tabular-nums tracking-tight leading-none block"
            >
              {{ stat.value }}
            </span>
          </div>

          <!-- Color-Coded Icon Block -->
          <div v-if="stat.icon">
            <div
              class="dashboard-stat-card__icon flex h-11 w-11 shrink-0 items-center justify-center border rounded-none transition-colors duration-300"
            >
              <component :is="stat.icon" class="h-5.5 w-5.5" stroke-width="1.8" />
            </div>
          </div>
        </div>

        <!-- Divider -->
        <div class="dashboard-stat-card__divider !my-2"></div>

        <!-- Footer Stats Info -->
        <div class="flex items-center gap-2 flex-wrap text-xs">
          <!-- Trend badge -->
          <span
            class="inline-flex items-center gap-1 text-2xs font-mono font-bold px-1.5 py-0.5 rounded-none border"
            :class="getTrendStyles(stat.trend).badge"
          >
            <component
              :is="getTrendIcon(stat.trend)"
              class="h-3 w-3"
              :class="getTrendStyles(stat.trend).icon"
              stroke-width="2"
            />
            {{ stat.change }}
          </span>

          <!-- Description -->
          <span class="text-xs text-[var(--silver-500)] font-medium">
            {{ stat.description }}
          </span>
        </div>
      </div>
    </component>
  </div>
</template>

<style scoped>
.dashboard-stat-card {
  border: 1px solid color-mix(in oklch, var(--silver-200) 68%, transparent);
  border-top: 3px solid var(--stat-accent);
  background: var(--card);
  box-shadow: var(--shadow-float);
  color: var(--card-foreground);
  transition:
    transform var(--transition-normal),
    border-color var(--transition-normal),
    box-shadow var(--transition-normal);
}

.dashboard-stat-card:hover {
  border-color: color-mix(in oklch, var(--stat-accent) 65%, var(--silver-200));
  border-top-color: var(--stat-accent);
  box-shadow: var(--shadow-float-hover);
  transform: translateY(-2px);
}

.dashboard-stat-card__header {
  border-bottom: 1px solid color-mix(in oklch, var(--silver-200) 58%, transparent);
  background: color-mix(in oklch, var(--stat-accent) 5%, var(--surface-sunken));
  color: var(--muted-foreground);
}

.dashboard-stat-card__title {
  color: var(--foreground);
}

.dashboard-stat-card__version {
  color: color-mix(in oklch, var(--muted-foreground) 55%, transparent);
}

.dashboard-stat-card__dot {
  background: color-mix(in oklch, var(--stat-accent) 42%, var(--silver-200));
}

.dashboard-stat-card__value {
  color: var(--stat-accent);
}

.dashboard-stat-card__icon {
  border-color: color-mix(in oklch, var(--stat-accent) 42%, transparent);
  background: color-mix(in oklch, var(--stat-accent) 8%, var(--card));
  color: var(--stat-accent);
}

.dashboard-stat-card:hover .dashboard-stat-card__icon {
  background: color-mix(in oklch, var(--stat-accent) 14%, var(--card));
}

.dashboard-stat-card__divider {
  height: 1px;
  background: linear-gradient(
    to right,
    transparent,
    color-mix(in oklch, var(--silver-200) 70%, transparent),
    transparent
  );
}

:global(.dark .dashboard-stat-card) {
  border-color: color-mix(in oklch, var(--silver-300) 55%, transparent);
  border-top-color: var(--stat-accent);
}

:global(.dark .dashboard-stat-card:hover) {
  border-color: color-mix(in oklch, var(--stat-accent) 58%, var(--silver-300));
  border-top-color: var(--stat-accent);
}

:global(.dark .dashboard-stat-card__header) {
  border-bottom-color: color-mix(in oklch, var(--silver-300) 48%, transparent);
  background: color-mix(in oklch, var(--stat-accent) 8%, var(--surface-sunken));
}

:global(.dark .dashboard-stat-card__dot) {
  background: color-mix(in oklch, var(--stat-accent) 52%, var(--silver-300));
}

:global(.dark .dashboard-stat-card__icon) {
  border-color: color-mix(in oklch, var(--stat-accent) 48%, transparent);
  background: color-mix(in oklch, var(--stat-accent) 12%, var(--card));
}

:global(.dark .dashboard-stat-card:hover .dashboard-stat-card__icon) {
  background: color-mix(in oklch, var(--stat-accent) 18%, var(--card));
}

:global(.dark .dashboard-stat-card__divider) {
  background: linear-gradient(
    to right,
    transparent,
    color-mix(in oklch, var(--silver-300) 55%, transparent),
    transparent
  );
}
</style>
