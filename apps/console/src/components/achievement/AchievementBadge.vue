<script setup lang="ts">
import { computed } from "vue";
import type { AchievementProgress } from "@/types/achievement";
import {
  TierLabels,
  TierColors,
  AchievementCategoryColors,
} from "@/types/achievement";
import { cn } from "@/lib/utils";
import { Trophy, Lock } from "lucide-vue-next";

const props = defineProps<{
  achievement: AchievementProgress;
  showProgress?: boolean;
  size?: "sm" | "md" | "lg";
}>();

const sizeClasses = {
  sm: "w-12 h-12",
  md: "w-16 h-16",
  lg: "w-24 h-24",
};

const iconSizes = {
  sm: "h-6 w-6",
  md: "h-8 w-8",
  lg: "h-12 w-12",
};

const progressPercentage = computed(() =>
  props.achievement.target > 0
    ? Math.min(
        100,
        (props.achievement.progress / props.achievement.target) * 100,
      )
    : 0,
);

const tierGradient = computed(
  () =>
    TierColors[props.achievement.tier as keyof typeof TierColors] ||
    TierColors[1],
);

const categoryColor = computed(
  () =>
    AchievementCategoryColors[
      props.achievement.category as keyof typeof AchievementCategoryColors
    ] || "text-muted-foreground",
);
</script>

<template>
  <div class="group relative flex flex-col items-center">
    <div
      :class="
        cn(
          'relative flex items-center justify-center rounded-full transition-all duration-300',
          sizeClasses[size || 'md'],
          achievement.earned ? `${tierGradient} shadow-lg` : 'bg-muted',
          !achievement.earned && 'opacity-50',
        )
      "
    >
      <template v-if="achievement.earned">
        <Trophy :class="cn('text-white', iconSizes[size || 'md'])" />
      </template>
      <template v-else>
        <Lock :class="cn('text-muted-foreground', iconSizes[size || 'md'])" />
      </template>

      <!-- Progress ring for unearned -->
      <svg
        v-if="!achievement.earned && showProgress"
        class="absolute inset-0 -rotate-90"
        viewBox="0 0 100 100"
      >
        <circle
          cx="50"
          cy="50"
          r="45"
          fill="none"
          stroke="currentColor"
          stroke-width="6"
          class="text-muted"
        />
        <circle
          cx="50"
          cy="50"
          r="45"
          fill="none"
          stroke="currentColor"
          stroke-width="6"
          :stroke-dasharray="2 * Math.PI * 45"
          :stroke-dashoffset="2 * Math.PI * 45 * (1 - progressPercentage / 100)"
          class="text-primary transition-all duration-500"
        />
      </svg>
    </div>

    <!-- Points badge -->
    <span
      v-if="achievement.points > 0"
      :class="
        cn(
          'absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-2xs font-bold text-primary-foreground',
          size === 'lg' && 'h-6 text-xs',
        )
      "
    >
      {{ achievement.points }}
    </span>

    <!-- Tier label -->
    <span
      :class="
        cn(
          'mt-1 text-2xs font-medium uppercase tracking-wide',
          categoryColor,
        )
      "
    >
      {{ TierLabels[achievement.tier as keyof typeof TierLabels] || "Bronze" }}
    </span>
  </div>
</template>
