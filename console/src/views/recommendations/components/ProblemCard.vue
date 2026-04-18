<!-- console/src/views/recommendations/components/ProblemCard.vue -->
<script setup lang="ts">
import { computed } from "vue";
import { TrendingUp } from "lucide-vue-next";
import type { RecommendItem } from "@/types/recommendation";

const props = defineProps<{
  item: RecommendItem;
  rank?: number;
}>();

const difficultyBadgeClass = computed(() => {
  switch (props.item.difficulty.toLowerCase()) {
    case "easy":
      return "terminal-badge-success";
    case "medium":
      return "terminal-badge-warning";
    case "hard":
      return "terminal-badge-error";
    default:
      return "terminal-badge-neutral";
  }
});

const scoreColor = computed(() => {
  const score = props.item.score;
  if (score >= 80) return "var(--terminal-green)";
  if (score >= 60) return "var(--terminal-amber)";
  return "var(--accent-electric)";
});

const isHot = computed(() => props.item.score >= 70);
</script>

<template>
  <article
    class="group precision-card terminal-card p-4 md:p-5 flex items-center gap-4 cursor-pointer"
    tabindex="0"
    role="link"
    @click="$router.push(`/problems/${item.slug}`)"
    @keyup.enter.prevent="$router.push(`/problems/${item.slug}`)"
  >
    <!-- Rank indicator -->
    <div
      v-if="rank"
      class="shrink-0 w-9 h-9 flex items-center justify-center border border-border bg-muted/50"
    >
      <span
        class="font-mono text-sm font-semibold"
        :class="rank <= 3 ? 'text-primary' : 'text-muted-foreground'"
      >
        {{ String(rank).padStart(2, '0') }}
      </span>
    </div>

    <!-- Content -->
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2">
        <h3
          class="text-sm md:text-base font-semibold text-foreground group-hover:text-primary transition-colors leading-tight truncate"
        >
          {{ item.title }}
        </h3>
        <span
          v-if="isHot"
          class="shrink-0 inline-flex items-center gap-1 terminal-badge terminal-badge-electric"
        >
          <TrendingUp class="h-3 w-3" />
          HOT
        </span>
      </div>
      <div class="mt-2 flex flex-wrap items-center gap-1.5">
        <span :class="['terminal-badge', difficultyBadgeClass]">
          {{ item.difficulty }}
        </span>
        <span
          v-for="tag in item.tags.slice(0, 3)"
          :key="tag"
          class="text-[11px] px-1.5 py-0.5 text-muted-foreground bg-muted/60 capitalize"
        >
          {{ tag }}
        </span>
        <span v-if="item.tags.length > 3" class="text-[11px] text-muted-foreground">
          +{{ item.tags.length - 3 }}
        </span>
      </div>
      <p
        v-if="item.reason"
        class="mt-2 text-xs text-muted-foreground leading-relaxed line-clamp-1"
      >
        {{ item.reason }}
      </p>
    </div>

    <!-- Score -->
    <div class="shrink-0 flex flex-col items-end gap-1">
      <span class="terminal-label">Score</span>
      <span
        class="font-mono text-lg font-semibold tabular-nums"
        :style="{ color: scoreColor }"
      >
        {{ item.score.toFixed(1) }}
      </span>
    </div>
  </article>
</template>
