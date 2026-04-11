<!-- console/src/views/recommendations/components/ProblemCard.vue -->
<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import type { RecommendItem } from "@/types/recommendation";

const props = defineProps<{
  item: RecommendItem;
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
</script>

<template>
  <article class="group precision-card terminal-card p-4 flex flex-col gap-3 cursor-pointer" tabindex="0" role="link" @click="$router.push(`/problems/${item.slug}`)" @keyup.enter.prevent="$router.push(`/problems/${item.slug}`)">
    <header class="flex items-start justify-between gap-4">
      <div class="flex-1 min-w-0">
        <h3 class="text-base font-semibold text-foreground group-hover:text-primary transition-colors leading-tight truncate">
          {{ item.title }}
        </h3>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <span :class="['terminal-badge', difficultyBadgeClass]">
            {{ item.difficulty }}
          </span>
          <span
            v-for="tag in item.tags.slice(0, 3)"
            :key="tag"
            class="rounded-full bg-muted/80 px-2 py-0.5 text-[11px] text-muted-foreground capitalize"
          >
            {{ tag }}
          </span>
          <span v-if="item.tags.length > 3" class="text-[11px] text-muted-foreground">
            +{{ item.tags.length - 3 }}
          </span>
        </div>
      </div>
      <div class="text-right shrink-0 flex flex-col items-end gap-0.5">
        <span class="terminal-label">推荐指数</span>
        <span class="terminal-kv-value text-lg text-primary">
          {{ item.score.toFixed(2) }}
        </span>
      </div>
    </header>
    <p v-if="item.reason" class="text-xs text-muted-foreground leading-relaxed line-clamp-2">
      {{ item.reason }}
    </p>
  </article>
</template>
