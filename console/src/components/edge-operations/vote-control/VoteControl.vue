<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { ArrowBigUp } from "lucide-vue-next";
import { computed } from "vue";

const props = defineProps<{
  likes: number;
  dislikes: number;
  userVote?: 1 | -1 | 0; // 1: upvoted, -1: downvoted, 0: neutral
  readonly?: boolean;
}>();

const emit = defineEmits<{
  (e: "vote", type: 1 | -1): void;
}>();

const isPreview = computed(() => props.userVote === undefined);

// Helper to format large numbers
const formatCount = (value: number) => {
  if (value >= 1000) return `${(value / 1000).toFixed(1).replace(/\.0$/, "")}k`;
  return value.toString();
};

const handleVote = (type: 1 | -1) => {
  if (props.readonly) return;
  emit("vote", type);
};

defineOptions({
  name: "VoteControl",
});
</script>

<template>
  <div
    class="flex items-center rounded-full bg-muted/50 px-1 h-8 border border-transparent hover:border-border/50 transition-colors"
  >
    <template v-if="!isPreview">
      <!-- Upvote -->
      <Button
        variant="ghost"
        size="icon"
        class="h-7 w-7 rounded-full hover:bg-background/80 hover:text-[var(--terminal-amber)] transition-all"
        :class="{
          'text-[var(--terminal-amber)] bg-background shadow-sm': userVote === 1,
          'cursor-default hover:bg-transparent hover:text-inherit': readonly,
        }"
        :disabled="readonly"
        @click.stop="handleVote(1)"
      >
        <ArrowBigUp
          class="h-4 w-4 transition-transform active:scale-125"
          :class="{ 'fill-current': userVote === 1 }"
        />
      </Button>

      <!-- Likes Count -->
      <span
        class="text-[11px] font-bold px-1.5 min-w-[1.5rem] text-center select-none"
        :class="{
          'text-[var(--terminal-amber)]': userVote === 1,
          'text-muted-foreground': userVote !== 1,
        }"
      >
        {{ formatCount(likes) }}
      </span>

      <!-- Separator -->
      <div class="h-3 w-px bg-border/40 mx-0.5"></div>

      <!-- Dislikes Count -->
      <span
        class="text-[11px] font-bold px-1.5 min-w-[1.5rem] text-center select-none"
        :class="{
          'text-[var(--accent-electric)]': userVote === -1,
          'text-muted-foreground': userVote !== -1,
        }"
      >
        {{ formatCount(dislikes) }}
      </span>

      <!-- Downvote -->
      <Button
        variant="ghost"
        size="icon"
        class="h-7 w-7 rounded-full hover:bg-background/80 hover:text-[var(--accent-electric)] transition-all"
        :class="{
          'text-[var(--accent-electric)] bg-background shadow-sm': userVote === -1,
          'cursor-default hover:bg-transparent hover:text-inherit': readonly,
        }"
        :disabled="readonly"
        @click.stop="handleVote(-1)"
      >
        <ArrowBigUp
          class="h-4 w-4 rotate-180 transition-transform active:scale-125"
          :class="{ 'fill-current': userVote === -1 }"
        />
      </Button>
    </template>

    <template v-else>
      <!-- Preview Mode -->
      <div class="flex items-center gap-1.5 px-2">
        <span class="text-[11px] font-bold text-muted-foreground/70">
          {{ formatCount(likes) }}
        </span>
        <div class="h-3 w-px bg-border/40 mx-0.5"></div>
        <span class="text-[11px] font-bold text-muted-foreground/70">
          {{ formatCount(dislikes) }}
        </span>
      </div>
    </template>
  </div>
</template>
