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
  green:
    "text-foreground-strong decoration-status-success-mark underline decoration-2 underline-offset-4",
  blue: "text-foreground-strong decoration-link-decoration underline decoration-2 underline-offset-4",
  purple:
    "text-foreground-strong decoration-status-special-mark underline decoration-2 underline-offset-4",
  orange:
    "text-foreground-strong decoration-status-warning-mark underline decoration-2 underline-offset-4",
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
      <span
        v-if="icon || $slots.icon"
        class="flex h-9 w-9 items-center justify-center border border-border/50 bg-muted/30"
      >
        <slot name="icon">{{ icon }}</slot>
      </span>
    </div>

    <div v-if="trend" class="mt-2 flex items-center gap-1">
      <span
        :class="
          cn(
            'text-xs font-medium',
            trend.value >= 0
              ? 'text-foreground decoration-status-success-mark underline decoration-2'
              : 'text-foreground decoration-status-error-mark underline decoration-2',
          )
        "
      >
        {{ trend.value >= 0 ? "+" : "" }}{{ trend.value }}%
      </span>
      <span class="text-xs text-muted-foreground">{{ trend.label }}</span>
    </div>
  </div>
</template>
