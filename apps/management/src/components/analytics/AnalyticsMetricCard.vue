<script setup lang="ts">
import type { Component } from 'vue'
import { IconTrendingDown, IconTrendingUp, IconMinus } from '@tabler/icons-vue'

export interface MetricData {
  title: string
  value: string | number
  change?: string
  trend?: 'up' | 'down' | 'neutral'
  description?: string
  icon?: Component
  prefix?: string
  suffix?: string
}

const props = withDefaults(
  defineProps<{
    metric: MetricData
    size?: 'sm' | 'md' | 'lg'
  }>(),
  {
    size: 'md',
  },
)

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

const getTrendColor = (trend: string) => {
  switch (trend) {
    case 'up':
      return 'var(--status-success-mark)'
    case 'down':
      return 'var(--status-error-mark)'
    default:
      return 'var(--foreground-muted)'
  }
}

const colorMix = (color: string, percentage: number) =>
  `color-mix(in srgb, ${color} ${percentage}%, transparent)`

const sizeClasses = {
  sm: {
    card: 'p-3',
    value: 'text-xl',
    title: 'text-2xs',
    change: 'text-2xs',
  },
  md: {
    card: 'p-4',
    value: 'text-2xl',
    title: 'text-xs',
    change: 'text-xs',
  },
  lg: {
    card: 'p-5',
    value: 'text-3xl',
    title: 'text-xs',
    change: 'text-xs',
  },
}
</script>

<template>
  <div
    class="group relative overflow-hidden rounded-none border-t-2 border-x border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-card shadow-float precision-card hover:border-r-[var(--border-subtle)] hover:border-l-[var(--border-subtle)] hover:border-b-[var(--border-subtle)] dark:hover:border-r-[var(--border-subtle)]/60 dark:hover:border-l-[var(--border-subtle)]/60 dark:hover:border-b-[var(--border-subtle)]/60 transition-all duration-200"
    :class="sizeClasses[props.size].card"
    :style="{
      borderTopColor: getTrendColor(props.metric.trend || 'neutral'),
    }"
  >
    <!-- Background decoration icon -->
    <div
      v-if="props.metric.icon"
      class="absolute -bottom-2 -right-2 h-16 w-16 opacity-[0.03] group-hover:opacity-[0.06] transition-opacity duration-300"
    >
      <component :is="props.metric.icon" class="h-full w-full" stroke-width="1" />
    </div>

    <!-- Card content -->
    <div class="relative z-10 space-y-2">
      <!-- Title -->
      <p
        class="font-medium uppercase tracking-widest text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
        :class="sizeClasses[props.size].title"
      >
        {{ props.metric.title }}
      </p>

      <!-- Value with optional prefix/suffix -->
      <div class="flex items-baseline gap-1">
        <span
          v-if="props.metric.prefix"
          class="font-medium text-[var(--foreground-muted)]"
          :class="sizeClasses[props.size].value"
        >
          {{ props.metric.prefix }}
        </span>
        <span
          class="font-medium font-data tabular-nums tracking-tight text-foreground"
          :class="sizeClasses[props.size].value"
        >
          {{ props.metric.value }}
        </span>
        <span v-if="props.metric.suffix" class="font-normal text-[var(--foreground-muted)] text-sm">
          {{ props.metric.suffix }}
        </span>
      </div>

      <!-- Trend badge -->
      <div v-if="props.metric.change" class="flex items-center gap-2">
        <span
          class="inline-flex items-center gap-1 font-medium px-2 py-0.5 rounded-none border"
          :class="sizeClasses[props.size].change"
          :style="{
            color: getTrendColor(props.metric.trend || 'neutral'),
            borderColor: colorMix(getTrendColor(props.metric.trend || 'neutral'), 25),
            backgroundColor: colorMix(getTrendColor(props.metric.trend || 'neutral'), 6),
          }"
        >
          <component
            :is="getTrendIcon(props.metric.trend || 'neutral')"
            class="h-3 w-3"
            stroke-width="1.5"
          />
          {{ props.metric.change }}
        </span>
      </div>

      <!-- Description -->
      <p
        v-if="props.metric.description"
        class="text-[var(--foreground-muted)] dark:text-[var(--foreground-muted)]"
        :class="sizeClasses[props.size].change"
      >
        {{ props.metric.description }}
      </p>
    </div>
  </div>
</template>
