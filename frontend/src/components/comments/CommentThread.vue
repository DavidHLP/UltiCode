<script setup lang="ts">
import type { ForumComment } from "@/types/forum";
import { Lock, MessageSquare } from "lucide-vue-next";
import CommentNode from "./CommentNode.vue";
import CommentForm from "./CommentForm.vue";
import { buildCommentTree } from "./comment-tree-builder";
import { ref, computed } from "vue";
import { fetchCurrentUserId } from "@/utils/auth";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  comments: ForumComment[];
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
  const userId = fetchCurrentUserId();
  return buildCommentTree(props.comments, {
    currentUserId: userId || undefined,
    postAuthorUsername: props.postAuthorUsername,
  });
});
</script>

<template>
  <div class="space-y-6 px-4 sm:px-6 pb-8">
    <div v-if="!isCommenting && !props.isLocked" class="mb-8">
      <div
        class="w-full rounded-2xl border border-muted-foreground/10 bg-muted/30 px-5 py-3.5 text-sm text-muted-foreground/70 cursor-text hover:bg-muted/50 hover:border-primary/20 transition-all duration-200 flex items-center gap-3 group"
        @click="isCommenting = true"
      >
        <div
          class="h-8 w-8 rounded-xl bg-background border border-muted-foreground/10 flex items-center justify-center group-hover:text-primary group-hover:border-primary/20 transition-all"
        >
          <MessageSquare class="h-4 w-4" />
        </div>
        <span class="font-bold tracking-tight">{{
          t("forum.comments.joinConversation")
        }}</span>
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
        class="flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-amber-600 bg-amber-50 px-3 py-1.5 rounded-lg border border-amber-100"
      >
        <Lock class="h-3 w-3" /> {{ t("forum.comments.threadLocked") }}
      </div>
    </div>

    <div class="space-y-8">
      <div
        v-if="commentTree.length === 0"
        class="flex flex-col items-center justify-center py-16 text-center"
      >
        <div class="p-5 rounded-3xl bg-muted/30 mb-4">
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
