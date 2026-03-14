<!-- console/src/views/recommendations/components/ProblemCard.vue -->
<script setup lang="ts">
import { computed } from "vue";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import type { RecommendItem } from "@/types/recommendation";

const props = defineProps<{
  item: RecommendItem;
}>();

const difficultyVariant = computed(() => {
  switch (props.item.difficulty.toLowerCase()) {
    case "easy":
      return "default";
    case "medium":
      return "secondary";
    case "hard":
      return "destructive";
    default:
      return "outline";
  }
});

const difficultyClass = computed(() => {
  switch (props.item.difficulty.toLowerCase()) {
    case "easy":
      return "text-green-600";
    case "medium":
      return "text-yellow-600";
    case "hard":
      return "text-red-600";
    default:
      return "";
  }
});
</script>

<template>
  <Card class="hover:border-primary/50 transition-colors">
    <CardHeader class="pb-2">
      <div class="flex items-start justify-between gap-4">
        <div class="flex-1">
          <CardTitle class="text-lg">
            <RouterLink
              :to="`/problems/${item.slug}`"
              class="hover:text-primary transition-colors"
            >
              {{ item.title }}
            </RouterLink>
          </CardTitle>
          <div class="mt-2 flex flex-wrap gap-2">
            <Badge :variant="difficultyVariant" :class="difficultyClass">
              {{ item.difficulty }}
            </Badge>
            <Badge
              v-for="tag in item.tags.slice(0, 3)"
              :key="tag"
              variant="outline"
            >
              {{ tag }}
            </Badge>
            <Badge v-if="item.tags.length > 3" variant="outline">
              +{{ item.tags.length - 3 }}
            </Badge>
          </div>
        </div>
        <div class="text-right shrink-0">
          <span class="text-xs text-muted-foreground">推荐指数</span>
          <div class="text-lg font-semibold text-primary">
            {{ item.score.toFixed(2) }}
          </div>
        </div>
      </div>
    </CardHeader>
    <CardContent>
      <p class="text-sm text-muted-foreground">{{ item.reason }}</p>
    </CardContent>
  </Card>
</template>
