<script setup lang="ts">
import { cn } from "@/lib/utils";

defineProps<{
  title: string;
  value: number | string;
  subtitle?: string;
  icon?: string;
  trend?: {
    value: number;
    label: string;
  };
  color?: "default" | "green" | "blue" | "purple" | "orange";
}>();

const colorClasses = {
  default: "text-foreground",
  green: "text-green-600 dark:text-green-400",
  blue: "text-blue-600 dark:text-blue-400",
  purple: "text-purple-600 dark:text-purple-400",
  orange: "text-orange-600 dark:text-orange-400",
};
</script>

<template>
  <div class="rounded-lg border bg-card p-4 transition-all hover:shadow-md">
    <div class="flex items-start justify-between">
      <div class="space-y-1">
        <p class="text-sm font-medium text-muted-foreground">{{ title }}</p>
        <p :class="cn('text-2xl font-bold', colorClasses[color || 'default'])">
          {{ value }}
        </p>
        <p v-if="subtitle" class="text-xs text-muted-foreground">
          {{ subtitle }}
        </p>
      </div>
      <span v-if="icon" class="text-2xl">{{ icon }}</span>
    </div>

    <div v-if="trend" class="mt-2 flex items-center gap-1">
      <span
        :class="
          cn(
            'text-xs font-medium',
            trend.value >= 0 ? 'text-green-600' : 'text-red-600',
          )
        "
      >
        {{ trend.value >= 0 ? "+" : "" }}{{ trend.value }}%
      </span>
      <span class="text-xs text-muted-foreground">{{ trend.label }}</span>
    </div>
  </div>
</template>
