<script setup lang="ts">
import type { AchievementProgress } from "@/types/achievement";
import {
  AchievementCategoryLabels,
  AchievementCategoryColors,
} from "@/types/achievement";
import AchievementBadge from "./AchievementBadge.vue";
import { cn } from "@/lib/utils";
import { Check, Calendar } from "lucide-vue-next";

const props = defineProps<{
  achievement: AchievementProgress;
  showProgress?: boolean;
}>();

const categoryLabel = computed(
  () =>
    AchievementCategoryLabels[
      props.achievement.category as keyof typeof AchievementCategoryLabels
    ] || props.achievement.category,
);

const categoryColor = computed(
  () =>
    AchievementCategoryColors[
      props.achievement.category as keyof typeof AchievementCategoryColors
    ] || "text-foreground-muted",
);

const formattedDate = computed(() => {
  if (!props.achievement.earnedAt) return null;
  return new Date(props.achievement.earnedAt).toLocaleDateString();
});

const progressText = computed(() => {
  if (props.achievement.earned) return "Completed";
  return `${props.achievement.progress} / ${props.achievement.target}`;
});

const progressPercentage = computed(() =>
  props.achievement.target > 0
    ? Math.min(
        100,
        (props.achievement.progress / props.achievement.target) * 100,
      )
    : 0,
);
</script>

<template>
  <div
    :class="
      cn(
        'relative flex items-start gap-4 rounded-none border p-4 transition-all',
        achievement.earned
          ? 'border-primary/30 bg-primary/5'
          : 'border-border bg-card hover:border-border/80',
      )
    "
  >
    <AchievementBadge
      :achievement="achievement"
      :show-progress="showProgress"
      size="md"
    />

    <div class="flex-1 space-y-1">
      <div class="flex items-start justify-between gap-2">
        <h4 class="font-medium leading-tight">
          {{ achievement.name }}
        </h4>
        <span :class="cn('text-xs font-medium', categoryColor)">
          +{{ achievement.points }} pts
        </span>
      </div>

      <p class="text-sm text-muted-foreground">
        {{ achievement.description }}
      </p>

      <!-- Category and progress -->
      <div class="flex items-center gap-3 text-xs">
        <span :class="cn('font-medium', categoryColor)">
          {{ categoryLabel }}
        </span>

        <template v-if="achievement.earned && formattedDate">
          <span class="flex items-center gap-1 text-[var(--foreground-strong)]">
            <Check class="h-3 w-3" />
            Earned
          </span>
          <span class="flex items-center gap-1 text-muted-foreground">
            <Calendar class="h-3 w-3" />
            {{ formattedDate }}
          </span>
        </template>

        <template v-else-if="showProgress">
          <span class="text-muted-foreground">{{ progressText }}</span>
        </template>
      </div>

      <!-- Progress bar for unearned -->
      <div
        v-if="!achievement.earned && showProgress"
        class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-muted"
      >
        <div
          class="h-full rounded-full bg-primary transition-all duration-500"
          :style="{ width: `${progressPercentage}%` }"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { computed } from "vue";
export default {
  name: "AchievementCard",
};
</script>
