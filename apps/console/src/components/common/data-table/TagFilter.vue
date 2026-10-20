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
        variant="outline"
        class="cursor-pointer px-2.5 py-1"
        :class="
          isTagSelected(tag, modelValue)
            ? 'border-input bg-accent text-foreground hover:bg-accent/80'
            : 'border-border bg-muted text-muted-foreground hover:bg-accent hover:text-foreground'
        "
        @click="toggleTag(tag, modelValue)"
      >
        {{ tag }}
      </Badge>
      <CollapsibleTrigger as-child>
        <Button variant="ghost" size="sm" class="gap-1 px-2 cursor-pointer">
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
          variant="outline"
          class="cursor-pointer px-2.5 py-1"
          :class="
            isTagSelected(tag, modelValue)
              ? 'border-input bg-accent text-foreground hover:bg-accent/80'
              : 'border-border bg-muted text-muted-foreground hover:bg-accent hover:text-foreground'
          "
          @click="toggleTag(tag, modelValue)"
        >
          {{ tag }}
        </Badge>
      </div>
    </CollapsibleContent>
  </Collapsible>
</template>
