<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import type { ForumComment, SolutionComment } from "@/types/comment";
import { Lock, MessageSquare } from "lucide-vue-next";
import CommentNode from "./CommentNode.vue";
import CommentForm from "./CommentForm.vue";
import {
  buildCommentTree,
  buildSolutionCommentTree,
} from "./comment-tree-builder";
import { ref, computed } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  comments: ForumComment[] | SolutionComment[];
  commentType?: "forum" | "solution";
  isLocked?: boolean;
  postAuthorUsername?: string;
}>();

const emit = defineEmits<{
  (e: "submit", body: string, parentId?: string): void;
  (e: "vote", commentId: number | string, voteType: 1 | -1): void;
  (e: "edit", commentId: number | string, content: string): void;
  (e: "delete", commentId: number | string): void;
}>();

const { t } = useI18n();
const isCommenting = ref(false);

function handleReply(commentId: string | number, content: string) {
  emit("submit", content, String(commentId));
}

const commentTree = computed(() => {
  const userId = useAuthStore().fetchCurrentUserId();
  const options = {
    currentUserId: userId || undefined,
    postAuthorUsername: props.postAuthorUsername,
  };
  if (props.commentType === "solution") {
    return buildSolutionCommentTree(
      props.comments as SolutionComment[],
      options,
    );
  }
  return buildCommentTree(props.comments as ForumComment[], options);
});
</script>

<template>
  <div class="space-y-6 px-4 sm:px-6 pb-8">
    <div v-if="!isCommenting && !props.isLocked" class="mb-8">
      <button
        type="button"
        :aria-label="t('forum.comments.joinConversation')"
        class="group flex w-full cursor-text items-center gap-3 rounded-md border border-border-control bg-surface-sunken px-4 py-3 text-left shadow-xs transition-colors duration-200 hover:border-primary hover:bg-surface-highlight focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        @click="isCommenting = true"
      >
        <span
          class="flex size-8 shrink-0 items-center justify-center rounded-sm border border-border-control bg-surface-elevated text-foreground-muted transition-colors duration-200 group-hover:border-primary group-hover:text-primary"
        >
          <MessageSquare class="size-4" aria-hidden="true" />
        </span>
        <span
          class="font-data text-sm text-foreground-muted transition-colors duration-200 group-hover:text-foreground-strong"
          >{{ t("forum.comments.joinConversation") }}</span
        >
      </button>
    </div>

    <div v-if="isCommenting" class="space-y-3 mb-8">
      <CommentForm
        :on-cancel="() => (isCommenting = false)"
        @submit="
          (content) => {
            emit('submit', content);
            isCommenting = false;
          }
        "
      />

      <div
        v-if="isLocked"
        class="flex items-center gap-2 text-2xs font-black uppercase tracking-widest text-foreground-strong bg-[var(--status-warning-mark)]/10 px-3 py-1.5 rounded-none border border-[var(--status-warning-mark)]/30"
      >
        <Lock class="h-3 w-3" /> {{ t("forum.comments.threadLocked") }}
      </div>
    </div>

    <div class="space-y-8">
      <div
        v-if="commentTree.length === 0"
        class="flex flex-col items-center justify-center py-16 text-center"
      >
        <div class="p-5 rounded-none bg-muted/30 mb-4">
          <MessageSquare class="h-10 w-10 text-muted-foreground/30" />
        </div>
        <h4 class="text-lg font-black tracking-tight">
          {{ t("forum.comments.noComments") }}
        </h4>
        <p class="text-sm text-muted-foreground mt-1 max-w-[280px]">
          {{ t("forum.comments.beFirst") }}
        </p>
      </div>
      <div class="space-y-6">
        <CommentNode
          v-for="comment in commentTree"
          :key="comment.id"
          :comment="comment"
          @reply="
            (id: number | string, content: string) => handleReply(id, content)
          "
          @vote="(id: number | string, type: 1 | -1) => emit('vote', id, type)"
          @edit="
            (id: number | string, content: string) => emit('edit', id, content)
          "
          @delete="(id: number | string) => emit('delete', id)"
        />
      </div>
    </div>
  </div>
</template>
