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
      return {
        topBorder: 'border-t-4 border-t-[var(--solarized-blue)]',
        iconBg:
          'border-[var(--solarized-blue)]/30 bg-[var(--solarized-blue)]/8 text-[var(--solarized-blue)] group-hover:bg-[var(--solarized-blue)]/15',
        valueColor: 'text-[var(--solarized-blue)]',
      }
    case 1: // Total Problems
      return {
        topBorder: 'border-t-4 border-t-[var(--solarized-cyan)]',
        iconBg:
          'border-[var(--solarized-cyan)]/30 bg-[var(--solarized-cyan)]/8 text-[var(--solarized-cyan)] group-hover:bg-[var(--solarized-cyan)]/15',
        valueColor: 'text-[var(--solarized-cyan)]',
      }
    case 2: // Active Contests
      return {
        topBorder: 'border-t-4 border-t-[var(--solarized-yellow)]',
        iconBg:
          'border-[var(--solarized-yellow)]/30 bg-[var(--solarized-yellow)]/8 text-[var(--solarized-yellow)] group-hover:bg-[var(--solarized-yellow)]/15',
        valueColor: 'text-[var(--solarized-yellow)]',
      }
    case 3: // Flagged Content
      return {
        topBorder: 'border-t-4 border-t-[var(--status-error)]',
        iconBg:
          'border-[var(--status-error)]/30 bg-[var(--status-error)]/8 text-[var(--status-error)] group-hover:bg-[var(--status-error)]/15',
        valueColor: 'text-[var(--status-error)]',
      }
    default:
      return {
        topBorder: 'border-t-4 border-t-[var(--silver-300)]',
        iconBg:
          'border-[var(--silver-300)] bg-[var(--silver-200)]/8 text-[var(--silver-500)] group-hover:bg-[var(--silver-200)]/15',
        valueColor: 'text-foreground',
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
      class="group relative block overflow-hidden rounded-none border-2 border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-card p-0 shadow-float transition-all duration-300 hover:-translate-y-1 hover:border-[var(--accent-primary)] hover:shadow-float-hover"
      :class="[getCardAccent(index).topBorder, { 'cursor-pointer': stat.href }]"
    >
      <!-- Retro Terminal Window Header -->
      <div
        class="flex items-center justify-between px-3 py-1.5 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--silver-100)] dark:bg-[var(--silver-200)]/35 text-[var(--silver-500)] font-mono text-[9px] uppercase tracking-wider font-bold"
      >
        <div class="flex items-center gap-1 shrink-0">
          <span class="size-1.5 bg-[var(--silver-300)] dark:bg-[var(--silver-400)]"></span>
          <span class="size-1.5 bg-[var(--silver-300)] dark:bg-[var(--silver-400)]"></span>
          <span class="size-1.5 bg-[var(--silver-300)] dark:bg-[var(--silver-400)]"></span>
        </div>
        <span class="truncate mx-2">{{ stat.title }}</span>
        <span class="opacity-30 select-none shrink-0 font-normal">SYS_V1.0</span>
      </div>

      <!-- Card content -->
      <div class="p-4 space-y-3.5">
        <div class="flex items-center justify-between gap-4">
          <div class="space-y-1">
            <!-- Numeric Value -->
            <span
              class="text-3.5xl font-extrabold font-data tabular-nums tracking-tight leading-none block"
              :class="getCardAccent(index).valueColor"
            >
              {{ stat.value }}
            </span>
          </div>

          <!-- Color-Coded Icon Block -->
          <div v-if="stat.icon">
            <div
              class="flex h-11 w-11 shrink-0 items-center justify-center border rounded-none transition-all duration-300"
              :class="getCardAccent(index).iconBg"
            >
              <component :is="stat.icon" class="h-5.5 w-5.5" stroke-width="1.8" />
            </div>
          </div>
        </div>

        <!-- Divider -->
        <div class="precision-divider !my-2"></div>

        <!-- Footer Stats Info -->
        <div class="flex items-center gap-2 flex-wrap text-xs">
          <!-- Trend badge -->
          <span
            class="inline-flex items-center gap-1 text-[10px] font-mono font-bold px-1.5 py-0.5 rounded-none border"
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
