<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { ThumbsUp, ThumbsDown } from "lucide-vue-next";
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
  <div class="flex items-center select-none font-mono">
    <template v-if="!isPreview">
      <!-- Upvote Button -->
      <Button
        type="button"
        variant="ghost"
        class="h-8 px-3 rounded-none text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] hover:text-[var(--solarized-base03)] dark:hover:text-foreground transition-all flex items-center gap-1.5 cursor-pointer select-none font-bold"
        :class="{
          'text-[var(--solarized-yellow)]! bg-[var(--solarized-yellow)]/10! hover:bg-[var(--solarized-yellow)]/15! hover:text-[var(--solarized-yellow)]!':
            userVote === 1,
          'opacity-50 cursor-default pointer-events-none': readonly,
        }"
        @click.stop="handleVote(1)"
      >
        <ThumbsUp
          class="h-3.5 w-3.5 active:scale-125 transition-transform"
          :class="{ 'fill-current': userVote === 1 }"
        />
        <span class="text-xxs tabular-nums font-mono font-bold">{{
          formatCount(likes)
        }}</span>
      </Button>

      <!-- Internal Separator -->
      <div
        class="h-4 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)] flex-none"
      ></div>

      <!-- Downvote Button -->
      <Button
        type="button"
        variant="ghost"
        class="h-8 px-3 rounded-none text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] hover:text-[var(--solarized-base03)] dark:hover:text-foreground transition-all flex items-center gap-1.5 cursor-pointer select-none font-bold"
        :class="{
          'text-[var(--solarized-violet)]! bg-[var(--solarized-violet)]/10! hover:bg-[var(--solarized-violet)]/15! hover:text-[var(--solarized-violet)]!':
            userVote === -1,
          'opacity-50 cursor-default pointer-events-none': readonly,
        }"
        @click.stop="handleVote(-1)"
      >
        <ThumbsDown
          class="h-3.5 w-3.5 active:scale-125 transition-transform"
          :class="{ 'fill-current': userVote === -1 }"
        />
        <span class="text-xxs tabular-nums font-mono font-bold">{{
          formatCount(dislikes)
        }}</span>
      </Button>
    </template>

    <template v-else>
      <!-- Preview Mode: Two Static Buttons -->
      <div
        class="h-8 px-3 rounded-none text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] flex items-center gap-1.5 select-none font-bold text-xs"
      >
        <ThumbsUp class="h-3.5 w-3.5" />
        <span class="text-xxs tabular-nums font-mono font-bold">{{
          formatCount(likes)
        }}</span>
      </div>

      <div
        class="h-4 w-px bg-[var(--silver-200)] dark:bg-[var(--silver-300)] flex-none"
      ></div>

      <div
        class="h-8 px-3 rounded-none text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] flex items-center gap-1.5 select-none font-bold text-xs"
      >
        <ThumbsDown class="h-3.5 w-3.5" />
        <span class="text-xxs tabular-nums font-mono font-bold">{{
          formatCount(dislikes)
        }}</span>
      </div>
    </template>
  </div>
</template>
