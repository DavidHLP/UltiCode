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
    class="flex items-center rounded-none h-8 px-0.5 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--silver-50)] dark:bg-[var(--silver-100)] hover:border-[var(--silver-300)] dark:hover:border-[var(--silver-400)] transition-all duration-[var(--duration-fast)] [transition-timing-function:var(--ease-out-expo)]"
  >
    <template v-if="!isPreview">
      <!-- Upvote -->
      <Button
        variant="ghost"
        size="icon"
        class="h-7 w-7 rounded-none hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] hover:text-[var(--terminal-amber)] transition-all duration-[var(--duration-fast)] [transition-timing-function:var(--ease-out-expo)]"
        :class="{
          'text-[var(--terminal-amber)] bg-[var(--silver-100)] dark:bg-[var(--silver-200)]': userVote === 1,
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
        class="font-data text-[11px] font-bold px-1.5 min-w-[1.5rem] text-center select-none tabular-nums"
        :class="{
          'text-[var(--terminal-amber)]': userVote === 1,
          'text-[var(--silver-500)]': userVote !== 1,
        }"
      >
        {{ formatCount(likes) }}
      </span>

      <!-- Separator -->
      <div class="h-3 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)] mx-0.5"></div>

      <!-- Dislikes Count -->
      <span
        class="font-data text-[11px] font-bold px-1.5 min-w-[1.5rem] text-center select-none tabular-nums"
        :class="{
          'text-[var(--accent-electric)]': userVote === -1,
          'text-[var(--silver-500)]': userVote !== -1,
        }"
      >
        {{ formatCount(dislikes) }}
      </span>

      <!-- Downvote -->
      <Button
        variant="ghost"
        size="icon"
        class="h-7 w-7 rounded-none hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] hover:text-[var(--accent-electric)] transition-all duration-[var(--duration-fast)] [transition-timing-function:var(--ease-out-expo)]"
        :class="{
          'text-[var(--accent-electric)] bg-[var(--silver-100)] dark:bg-[var(--silver-200)]': userVote === -1,
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
        <span class="font-data text-[11px] font-bold text-[var(--silver-500)] tabular-nums">
          {{ formatCount(likes) }}
        </span>
        <div class="h-3 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)] mx-0.5"></div>
        <span class="font-data text-[11px] font-bold text-[var(--silver-500)] tabular-nums">
          {{ formatCount(dislikes) }}
        </span>
      </div>
    </template>
  </div>
</template>
