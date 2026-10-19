<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";

import { useAuthStore } from "@/stores/auth";
import type { ForumComment, SolutionComment } from "@/types/comment";
import { Lock, MessageSquare } from "@lucide/vue";
import CommentNode from "./CommentNode.vue";
import CommentForm from "./CommentForm.vue";
import {
  buildCommentTree,
  buildSolutionCommentTree,
} from "./comment-tree-builder";
import { ref, computed, nextTick } from "vue";
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
const commentPrompt = ref<HTMLElement | null>(null);

async function cancelComment() {
  isCommenting.value = false;
  await nextTick();
  commentPrompt.value?.querySelector("button")?.focus({ preventScroll: true });
}

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
  <div class="min-w-0 space-y-6 pb-8">
    <div
      v-if="!isCommenting && !props.isLocked"
      ref="commentPrompt"
      class="mb-8"
    >
      <Button
        variant="outline"
        type="button"
        :aria-label="t('forum.comments.joinConversation')"
        class="group flex h-auto min-h-8 w-full min-w-0 whitespace-normal cursor-text items-center gap-2 px-3 py-3 text-left focus-visible:outline-none"
        @click="isCommenting = true"
      >
        <span
          class="flex size-8 shrink-0 items-center justify-center rounded-sm border border-input bg-card text-muted-foreground transition-colors duration-200 group-hover:border-primary group-hover:text-primary"
        >
          <MessageSquare class="size-4" aria-hidden="true" />
        </span>
        <span
          class="min-w-0 break-words font-mono text-sm text-muted-foreground transition-colors duration-200 group-hover:text-foreground"
          >{{ t("forum.comments.joinConversation") }}</span
        >
      </Button>
    </div>

    <div v-if="isCommenting" class="space-y-3 mb-8">
      <CommentForm
        :on-cancel="cancelComment"
        @submit="
          (content) => {
            emit('submit', content);
            isCommenting = false;
          }
        "
      />

      <div
        v-if="isLocked"
        class="flex items-center gap-2 text-xs font-black uppercase tracking-widest text-foreground bg-status-warning-mark/10 px-3 py-1.5 rounded-lg border border-status-warning-mark/30"
      >
        <Lock class="h-3 w-3" /> {{ t("forum.comments.threadLocked") }}
      </div>
    </div>

    <div class="space-y-8">
      <div
        v-if="commentTree.length === 0"
        class="flex flex-col items-center justify-center py-16 text-center"
      >
        <div class="p-5 rounded-lg bg-muted/30 mb-4">
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
