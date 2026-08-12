<script setup lang="ts">
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { ChevronDown } from "lucide-vue-next";

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
        class="cursor-pointer rounded-md px-2.5 py-1 text-xxs font-medium transition-all duration-200"
        :class="
          isTagSelected(tag, modelValue)
          ? 'border-border-control bg-surface-highlight text-foreground-strong hover:bg-surface-highlight/80'
          : 'border-border-subtle bg-surface-sunken text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong'
        "
        @click="toggleTag(tag, modelValue)"
      >
        {{ tag }}
      </Badge>
      <CollapsibleTrigger as-child>
        <Button
          variant="ghost"
          size="sm"
          class="gap-1 h-6 rounded-md px-2 text-2xs font-mono text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong cursor-pointer"
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
          variant="outline"
          class="cursor-pointer rounded-md px-2.5 py-1 text-2xs font-medium transition-all duration-200"
          :class="
            isTagSelected(tag, modelValue)
            ? 'border-border-control bg-surface-highlight text-foreground-strong hover:bg-surface-highlight/80'
            : 'border-border-subtle bg-surface-sunken text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong'
          "
          @click="toggleTag(tag, modelValue)"
        >
          {{ tag }}
        </Badge>
      </div>
    </CollapsibleContent>
  </Collapsible>
</template>
