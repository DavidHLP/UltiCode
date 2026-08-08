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
      <div
        class="w-full cursor-text border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] hover:border-[var(--accent-electric)] px-5 py-3.5 transition-all duration-200 flex items-center gap-3 group"
        @click="isCommenting = true"
      >
        <div
          class="h-8 w-8 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)] flex items-center justify-center group-hover:border-[var(--accent-electric)] group-hover:text-[var(--accent-electric)] transition-all duration-200"
        >
          <MessageSquare
            class="h-4 w-4 text-[var(--silver-500)] group-hover:text-[var(--accent-electric)]"
          />
        </div>
        <span
          class="font-mono text-sm text-[var(--silver-500)] group-hover:text-[var(--foreground)] transition-colors duration-200"
          >{{ t("forum.comments.joinConversation") }}</span
        >
      </div>
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
        class="flex items-center gap-2 text-2xs font-black uppercase tracking-widest text-[var(--terminal-amber)] bg-[var(--terminal-amber)]/10 px-3 py-1.5 rounded-none border border-[var(--terminal-amber)]/30"
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
