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
        class="cursor-pointer px-2 py-0.5 text-[11px] border-silver text-muted-foreground hover:bg-[var(--surface-sunken)] hover:text-foreground transition-colors rounded-none"
        :class="{
          'bg-[var(--accent-electric)] text-[var(--background)] border-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/90 hover:text-[var(--background)]':
            isTagSelected(tag, modelValue),
        }"
        @click="toggleTag(tag, modelValue)"
      >
        {{ tag }}
      </Badge>
      <CollapsibleTrigger as-child>
        <Button
          variant="ghost"
          size="sm"
          class="gap-1 h-6 text-[10px] text-muted-foreground hover:text-foreground rounded-none px-2"
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
          class="cursor-pointer px-2 py-0.5 text-[10px] font-normal border-silver text-muted-foreground hover:bg-[var(--surface-sunken)] hover:text-foreground transition-colors rounded-none"
          :class="{
            'bg-[var(--accent-electric)] text-[var(--background)] border-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/90 hover:text-[var(--background)]':
              isTagSelected(tag, modelValue),
          }"
          @click="toggleTag(tag, modelValue)"
        >
          {{ tag }}
        </Badge>
      </div>
    </CollapsibleContent>
  </Collapsible>
</template>
