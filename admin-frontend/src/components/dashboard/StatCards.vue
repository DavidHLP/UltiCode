<script setup lang="ts">
import type { Component } from 'vue'
import { IconTrendingDown, IconTrendingUp, IconMinus } from '@tabler/icons-vue'

import { Badge } from '@/components/ui/badge'
import { CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

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

const getTrendColor = (trend: string) => {
  switch (trend) {
    case 'up':
      return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-950/30 border-green-200 dark:border-green-800/50'
    case 'down':
      return 'text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/30 border-red-200 dark:border-red-800/50'
    default:
      return 'text-muted-foreground bg-muted/50'
  }
}
</script>

<template>
  <div class="grid grid-cols-1 gap-6 px-4 lg:px-6 md:grid-cols-2 lg:grid-cols-4">
    <component
      :is="stat.href ? 'a' : 'div'"
      v-for="(stat, index) in stats"
      :key="index"
      :href="stat.href"
      class="group relative overflow-hidden rounded-xl border border-border/50 bg-gradient-to-br from-card to-card/50 p-6 shadow-sm transition-all hover:shadow-md hover:-translate-y-0.5"
      :class="{ 'cursor-pointer': stat.href }"
    >
      <!-- Background icon -->
      <div
        v-if="stat.icon"
        class="absolute -bottom-2 -right-2 h-24 w-24 opacity-5 transition-transform group-hover:scale-110 group-hover:rotate-3"
      >
        <component :is="stat.icon" class="h-full w-full" />
      </div>

      <!-- Card content -->
      <CardHeader class="p-0 space-y-2">
        <CardDescription class="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          {{ stat.title }}
        </CardDescription>
        <CardTitle class="text-3xl font-bold tabular-nums tracking-tight">
          {{ stat.value }}
        </CardTitle>

        <!-- Trend badge -->
        <Badge
          variant="outline"
          class="w-fit text-xs font-medium px-2 py-0.5 h-6"
          :class="getTrendColor(stat.trend)"
        >
          <component :is="getTrendIcon(stat.trend)" class="mr-1 h-3 w-3" />
          {{ stat.change }}
        </Badge>
      </CardHeader>

      <!-- Description -->
      <p class="text-sm text-muted-foreground mt-2">
        {{ stat.description }}
      </p>

      <!-- Subtle border accent -->
      <div
        class="absolute inset-x-0 bottom-0 h-0.5 bg-gradient-to-r from-transparent via-primary/20 to-transparent opacity-0 transition-opacity group-hover:opacity-100"
      ></div>
    </component>
  </div>
</template>
