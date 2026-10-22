<script setup lang="ts">
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@ulticode/design-system/ui/collapsible";
import { ChevronDown } from "@lucide/vue";

defineProps<{
  popularTags: string[];
  otherTags: string[];
  modelValue: string[];
  showMoreLabel?: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string[]];
}>();

function toggleTag(tag: string, currentTags: string[]) {
  const newTags = currentTags.includes(tag)
    ? currentTags.filter((t) => t !== tag)
    : [...currentTags, tag];
  emit("update:modelValue", newTags);
}

function isTagSelected(tag: string, currentTags: string[]) {
  return currentTags.includes(tag);
}
</script>

<template>
  <Collapsible class="w-full space-y-2.5">
    <div class="flex flex-wrap items-center gap-1.5">
      <Badge
        v-for="tag in popularTags"
        :key="tag"
        as="button"
        type="button"
        :aria-pressed="isTagSelected(tag, modelValue)"
        variant="outline"
        class="min-h-7 cursor-pointer px-2.5 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        :class="
          isTagSelected(tag, modelValue)
            ? 'border-border bg-accent text-foreground hover:bg-accent/80'
            : 'border-transparent bg-transparent text-muted-foreground hover:bg-muted hover:text-foreground'
        "
        @click="toggleTag(tag, modelValue)"
      >
        {{ tag }}
      </Badge>
      <CollapsibleTrigger v-if="otherTags.length" as-child>
        <Button
          variant="ghost"
          size="sm"
          class="gap-1 px-2 text-xs text-muted-foreground cursor-pointer"
        >
          {{ showMoreLabel || "Show more" }}
          <ChevronDown class="h-2.5 w-2.5" />
        </Button>
      </CollapsibleTrigger>
    </div>
    <CollapsibleContent class="animate-slide-down">
      <div class="flex flex-wrap gap-1.5 pt-1.5">
        <Badge
          v-for="tag in otherTags"
          :key="tag"
          as="button"
          type="button"
          :aria-pressed="isTagSelected(tag, modelValue)"
          variant="outline"
          class="min-h-7 cursor-pointer px-2.5 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          :class="
            isTagSelected(tag, modelValue)
              ? 'border-border bg-accent text-foreground hover:bg-accent/80'
              : 'border-transparent bg-transparent text-muted-foreground hover:bg-muted hover:text-foreground'
          "
          @click="toggleTag(tag, modelValue)"
        >
          {{ tag }}
        </Badge>
      </div>
    </CollapsibleContent>
  </Collapsible>
</template>
