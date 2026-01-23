<script setup lang="ts">
import { VoteControl } from "./vote-control";
import ActionItem from "./ActionItem.vue";
import {
  MessageCircle,
  Share2,
  Bookmark,
  BookmarkCheck,
  Eye,
  MessageSquare,
} from "lucide-vue-next";
import { computed } from "vue";

export interface PostActionsConfig {
  comments?: {
    show: boolean;
    count: number | string;
    text?: string; // e.g. "Comments"
    icon?: "message-circle" | "message-square"; // default square for forum?
    variant?: "button" | "simple"; // button (ghost) or simple text/icon
  };
  views?: {
    show: boolean;
    count: number | string;
  };
  share?: {
    show: boolean;
    text?: string;
  };
  save?: {
    show: boolean;
    isSaved?: boolean;
    count?: number | string;
    text?: string;
  };
}

export interface VoteData {
  likes: number;
  dislikes: number;
  userVote?: 0 | 1 | -1;
}

const props = defineProps<{
  vote: VoteData;
  config: PostActionsConfig;
  class?: string;
}>();

const emit = defineEmits<{
  (e: "vote", type: 1 | -1): void;
  (e: "comment"): void;
  (e: "share"): void;
  (e: "save"): void;
}>();

const commentIcon = computed(() => {
  return props.config.comments?.icon === "message-circle"
    ? MessageCircle
    : MessageSquare;
});
</script>

<template>
  <div
    :class="[
      'flex items-center gap-2 text-muted-foreground select-none flex-wrap',
      props.class,
    ]"
  >
    <VoteControl
      :likes="vote.likes"
      :dislikes="vote.dislikes"
      :user-vote="vote.userVote"
      class="origin-left"
      @vote="(t: 1 | -1) => emit('vote', t)"
    />

    <!-- Views -->
    <ActionItem
      v-if="config.views?.show"
      variant="simple"
      :icon="Eye"
      :count="config.views.count"
    />

    <!-- Comments -->
    <ActionItem
      v-if="config.comments?.show"
      :variant="config.comments.variant || 'button'"
      :icon="commentIcon"
      :count="config.comments.count"
      :label="config.comments.text"
      @click="emit('comment')"
    />

    <!-- Share -->
    <ActionItem
      v-if="config.share?.show"
      :icon="Share2"
      :label="config.share.text"
      @click="emit('share')"
    />

    <!-- Save -->
    <ActionItem
      v-if="config.save?.show"
      :icon="config.save.isSaved ? BookmarkCheck : Bookmark"
      :count="config.save.count"
      :label="config.save.text"
      :active="config.save.isSaved"
      :class="{ 'fill-primary/20': config.save.isSaved }"
      @click="emit('save')"
    />
  </div>
</template>
