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
        class="cursor-pointer px-2.5 py-1 text-xxs font-medium transition-all duration-200 rounded-none"
        :class="
          isTagSelected(tag, modelValue)
            ? 'border-[var(--accent-electric)]/30 bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/18'
            : 'border-border/40 bg-[var(--surface-sunken)]/40 text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--surface-sunken)] hover:text-foreground'
        "
        @click="toggleTag(tag, modelValue)"
      >
        {{ tag }}
      </Badge>
      <CollapsibleTrigger as-child>
        <Button
          variant="ghost"
          size="sm"
          class="gap-1 h-6 text-2xs font-mono text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:text-foreground rounded-none px-2 hover:bg-[var(--surface-sunken)] cursor-pointer"
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
          class="cursor-pointer px-2.5 py-1 text-2xs font-medium transition-all duration-200 rounded-none"
          :class="
            isTagSelected(tag, modelValue)
              ? 'border-[var(--accent-electric)]/30 bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/18'
              : 'border-border/40 bg-[var(--surface-sunken)]/40 text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:bg-[var(--surface-sunken)] hover:text-foreground'
          "
          @click="toggleTag(tag, modelValue)"
        >
          {{ tag }}
        </Badge>
      </div>
    </CollapsibleContent>
  </Collapsible>
</template>
