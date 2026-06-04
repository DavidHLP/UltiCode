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
  green: "text-[oklch(0.6444_0.1508_118.6)]",
  blue: "text-[oklch(0.6149_0.1394_244.9)]",
  purple: "text-[oklch(0.5924_0.2025_355.9)]",
  orange: "text-[oklch(0.6545_0.1340_85.7)]",
};
</script>

<template>
  <div class="rounded-none border bg-card p-4 transition-all hover:shadow-md">
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
            trend.value >= 0
              ? 'text-[oklch(0.6444_0.1508_118.6)]'
              : 'text-[oklch(0.5863_0.2064_27.1)]',
          )
        "
      >
        {{ trend.value >= 0 ? "+" : "" }}{{ trend.value }}%
      </span>
      <span class="text-xs text-muted-foreground">{{ trend.label }}</span>
    </div>
  </div>
</template>
