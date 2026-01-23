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
  <Collapsible class="w-full space-y-3">
    <div class="flex flex-wrap items-center gap-2">
      <Badge
        v-for="tag in popularTags"
        :key="tag"
        :variant="isTagSelected(tag, modelValue) ? 'default' : 'outline'"
        class="cursor-pointer px-3 py-1 hover:bg-primary/80 hover:text-primary-foreground transition-colors rounded-md"
        :class="{
          'bg-primary text-primary-foreground hover:bg-primary/90':
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
          class="gap-1 h-7 text-xs text-muted-foreground hover:text-foreground rounded-full"
        >
          {{ showMoreLabel || "Show more" }}
          <ChevronDown class="h-3 w-3" />
        </Button>
      </CollapsibleTrigger>
    </div>
    <CollapsibleContent class="animate-slide-down">
      <div class="flex flex-wrap gap-2 pt-2">
        <Badge
          v-for="tag in otherTags"
          :key="tag"
          variant="outline"
          class="cursor-pointer px-2.5 py-0.5 text-[11px] font-normal border-zinc-200 dark:border-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors rounded-md"
          :class="{
            'bg-zinc-900 text-zinc-50 border-zinc-900 hover:bg-zinc-800 dark:bg-zinc-50 dark:text-zinc-900':
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
