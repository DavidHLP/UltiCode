<template>
  <div class="max-w-4xl mx-auto py-12 px-6">
    <div class="space-y-10">
      <div
        class="flex items-center justify-between border-b border-muted-foreground/10 pb-6"
      >
        <h2 class="text-2xl font-black tracking-tight">
          {{ t("forum.comments.title") }}
          <span class="text-muted-foreground/40 ml-1 font-bold"
            >({{ totalComments }})</span
          >
        </h2>
      </div>

      <div
        v-if="loading"
        class="flex flex-col items-center justify-center py-20 gap-4"
      >
        <Loader2 class="h-10 w-10 animate-spin text-primary/60" />
        <p
          class="text-sm font-bold text-muted-foreground uppercase tracking-widest"
        >
          {{ t("forum.comments.loading") }}
        </p>
      </div>

      <div v-else class="space-y-8">
        <div v-if="commentTree.length > 0" class="space-y-6">
          <CommentNode
            v-for="comment in commentTree"
            :key="comment.id"
            :comment="comment"
            @reply="handleReply"
          />
        </div>

        <div
          v-else
          class="flex flex-col items-center justify-center py-24 text-center"
        >
          <div class="p-6 rounded-3xl bg-muted/30 mb-6">
            <MessageSquare class="h-12 w-12 text-muted-foreground/20" />
          </div>
          <h3 class="text-xl font-black tracking-tight">
            {{ t("forum.comments.silence") }}
          </h3>
          <p class="text-muted-foreground mt-2 max-w-[320px] font-medium">
            {{ t("forum.comments.noCommentsDesc") }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import type { Comment } from "@/types/comment";
import type { ForumComment, ForumThread } from "@/types/forum";
import CommentNode from "./CommentNode.vue";
import { buildCommentTree, countComments } from "./comment-tree-builder";
import { Loader2, MessageSquare } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

defineOptions({
  name: "CommentThreadView",
});

const route = useRoute();
const { t } = useI18n();
const commentTree = ref<Comment[]>([]);
const loading = ref(true);
const threadAuthor = ref<string | undefined>();

const totalComments = computed(() => {
  return countComments(commentTree.value);
});

const fetchComments = async () => {
  const postId = route.params.id as string;
  if (!postId) return;

  try {
    loading.value = true;
    const response = await fetch(`/api/forum/posts/${postId}/thread`);
    if (!response.ok) throw new Error("Failed to fetch thread");

    const data: ForumThread & { comments: ForumComment[] } =
      await response.json();
    if (data && data.comments) {
      threadAuthor.value = data.author?.username;
      commentTree.value = buildCommentTree(data.comments, {
        postAuthorUsername: threadAuthor.value,
      });
    }
  } catch (err) {
    console.error("Error loading comments:", err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchComments();
});

const handleReply = (parentId: number | string, content: string) => {
  console.log("Reply to", parentId, ":", content);
};
</script>
