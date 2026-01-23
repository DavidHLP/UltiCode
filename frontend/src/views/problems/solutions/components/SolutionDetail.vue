<script setup lang="ts">
import type { SolutionFeedItem } from "@/types/solution";
import type { ForumComment } from "@/types/forum";
import MarkdownView from "@/components/markdown/MarkdownView.vue";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { computed, ref, watch } from "vue";
import { CommentThread } from "@/components/comments";
import {
  fetchSolutionComments,
  createSolutionComment,
  updateSolutionComment,
  deleteSolutionComment,
  recordSolutionView,
  deleteSolution,
} from "@/api/solution";
import { vote, VoteTargetType } from "@/api/vote";
import { PostActions } from "@/components/edge-operations";
import "highlight.js/styles/atom-one-dark.css";
import { toast } from "vue-sonner";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";
import { fetchCurrentUserId, isAuthenticated } from "@/utils/auth";
import { Button } from "@/components/ui/button";
import { useRouter } from "vue-router";
import { Pencil, Trash2 } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { useErrorHandler } from "@/composables/useErrorHandler";

const props = defineProps<{
  item: SolutionFeedItem;
}>();

const emit = defineEmits<{
  deleted: [id: string];
}>();

const router = useRouter();
const { t } = useI18n();
const { handleError } = useErrorHandler();

const authorInitial = computed(
  () => props.item.author.name.charAt(0)?.toUpperCase() ?? "?",
);

const topicLabel = computed(
  () =>
    props.item.topicName ||
    props.item.topicTranslated ||
    props.item.topic ||
    t("forum.post.flair"),
);

const comments = ref<ForumComment[]>([]);
const localStats = ref<{ likes: number; dislikes: number }>({
  likes: 0,
  dislikes: 0,
});
const userVote = ref<0 | 1 | -1>(0);
const isOwner = computed(() => {
  const userId = fetchCurrentUserId();
  return (
    Boolean(userId) &&
    props.item.id !== "follow-up" &&
    props.item.authorId === userId
  );
});

watch(
  () => props.item,
  (newItem) => {
    localStats.value = resolveVoteCounts(
      newItem.likes,
      newItem.dislikes,
      newItem.stats,
    );
    userVote.value = resolveUserVote(newItem.userVote);
  },
  { immediate: true, deep: true },
);

const loadComments = async () => {
  if (!props.item.id || props.item.id === "follow-up") {
    comments.value = [];
    return;
  }
  try {
    const userId = fetchCurrentUserId();
    comments.value = await fetchSolutionComments(
      props.item.id,
      userId || undefined,
    );
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.solutions.error.commentsLoadFailed",
      logToConsole: true,
      resetState: () => {
        comments.value = [];
      },
    });
  }
};

const handleCommentSubmit = async (content: string, parentId?: string) => {
  try {
    if (!props.item.id || props.item.id === "follow-up") return;
    await createSolutionComment(props.item.id, content, parentId);
    await loadComments();
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.solutions.error.commentPostFailed",
      logToConsole: true,
    });
  }
};

const handleCommentEdit = async (
  commentId: string | number,
  content: string,
) => {
  try {
    await updateSolutionComment(String(commentId), content);
    await loadComments();
    toast.success(t("problem.solutions.commentUpdated"));
  } catch (error) {
    handleError(error, {
      fallbackMessage: "forum.messages.commentEditFailed",
      logToConsole: true,
    });
  }
};

const handleCommentDelete = async (commentId: string | number) => {
  try {
    await deleteSolutionComment(String(commentId));
    await loadComments();
    toast.success(t("problem.solutions.commentDeleted"));
  } catch (error) {
    handleError(error, {
      fallbackMessage: "forum.messages.commentDeleteFailed",
      logToConsole: true,
    });
  }
};

const handleSolutionVote = async (voteType: 1 | -1) => {
  if (!isAuthenticated()) {
    toast.error(t("problem.solutions.loginToVote"));
    return;
  }
  try {
    if (!props.item.id || props.item.id === "follow-up") return;

    const res = await vote(VoteTargetType.SOLUTION, props.item.id, voteType);
    localStats.value = { likes: res.likes, dislikes: res.dislikes };
    userVote.value = res.userVote;
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.solutions.error.voteFailed",
      logToConsole: true,
    });
  }
};

const handleEditSolution = () => {
  if (props.item.id && props.item.id !== "follow-up") {
    router.push({ name: "solution-edit", params: { id: props.item.id } });
  }
};

const handleDeleteSolution = async () => {
  if (!props.item.id || props.item.id === "follow-up") return;
  const confirmed = window.confirm(t("problem.solutions.deleteConfirm"));
  if (!confirmed) return;
  try {
    await deleteSolution(props.item.id);
    toast.success(t("problem.solutions.solutionDeleted"));
    emit("deleted", props.item.id);
  } catch (error) {
    handleError(error, {
      fallbackMessage: "personal.messages.saveFailed",
      logToConsole: true,
    });
  }
};

const handleCommentVote = async (
  commentId: string | number,
  voteType: 1 | -1,
) => {
  if (!isAuthenticated()) {
    toast.error(t("problem.solutions.loginToVote"));
    return;
  }
  try {
    const res = await vote(
      VoteTargetType.SOLUTION_COMMENT,
      String(commentId),
      voteType,
    );

    // Recursive helper to find and update comment
    const updateComment = (list: ForumComment[]) => {
      for (const comment of list) {
        if (comment.id === commentId) {
          comment.likes = res.likes;
          comment.dislikes = res.dislikes;
          comment.userVote = res.userVote;
          return true;
        }
        if (comment.replies && comment.replies.length > 0) {
          if (updateComment(comment.replies)) return true;
        }
      }
      return false;
    };

    updateComment(comments.value);
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.solutions.error.commentVoteFailed",
      logToConsole: true,
    });
  }
};

watch(
  () => props.item.id,
  async (newId) => {
    loadComments();
    if (newId && newId !== "follow-up") {
      try {
        await recordSolutionView(newId);
      } catch (e) {
        handleError(e, {
          fallbackMessage: "problem.solutions.error.viewRecordFailed",
          logToConsole: true,
        });
      }
    }
  },
  { immediate: true },
);
</script>

<template>
  <article
    class="group flex flex-col gap-3 border-b border-border px-0 py-5 last:border-b-0"
  >
    <header class="flex items-start gap-3">
      <Avatar class="h-10 w-10 border border-border/50">
        <AvatarFallback
          class="text-xs font-semibold text-white"
          :style="{ backgroundColor: props.item.author.avatarColor }"
        >
          {{ authorInitial }}
        </AvatarFallback>
      </Avatar>

      <div class="flex flex-1 flex-col gap-1.5 text-sm leading-tight">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-semibold text-foreground">
            {{ props.item.author.name }}
          </span>
          <span class="truncate text-muted-foreground">
            {{ props.item.author.role }}
          </span>
          <span class="text-xs text-muted-foreground">
            · {{ props.item.created_at }}
          </span>
          <Badge
            v-if="props.item.flair"
            variant="secondary"
            class="rounded-full bg-orange-100 px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-orange-700 hover:bg-orange-100"
          >
            {{ props.item.flair }}
          </Badge>
          <div v-if="isOwner" class="ml-auto flex items-center gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              class="h-7 px-2 text-xs"
              @click="handleEditSolution"
            >
              <Pencil class="mr-1 h-3.5 w-3.5" />
              {{ t("common.actions.edit") }}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              class="h-7 px-2 text-xs text-destructive hover:text-destructive"
              @click="handleDeleteSolution"
            >
              <Trash2 class="mr-1 h-3.5 w-3.5" />
              {{ t("common.actions.delete") }}
            </Button>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <Badge
            variant="secondary"
            class="rounded-full px-2.5 py-0.5 text-[11px] capitalize"
          >
            {{ props.item.language }}
          </Badge>
          <Badge
            variant="secondary"
            class="rounded-full px-2.5 py-0.5 text-[11px] capitalize"
          >
            {{ topicLabel }}
          </Badge>
          <Badge
            v-for="badge in props.item.badges"
            :key="badge"
            variant="outline"
            class="rounded-full px-2.5 py-0.5 text-[11px] font-medium"
          >
            {{ badge }}
          </Badge>
        </div>
      </div>
    </header>

    <section class="space-y-4 text-sm leading-relaxed">
      <!-- Markdown 内容展示 -->
      <MarkdownView
        :content="props.item.content"
        :editor-id="`solution-${props.item.id}`"
      />

      <!-- 标签 -->
      <div v-if="props.item.tags.length" class="flex flex-wrap gap-2">
        <Badge
          v-for="tag in props.item.tags"
          :key="tag"
          variant="secondary"
          class="rounded-full px-3 py-1 text-xs"
        >
          {{ tag }}
        </Badge>
      </div>

      <!-- 统计信息和操作 -->
      <PostActions
        :vote="{
          likes: localStats.likes,
          dislikes: localStats.dislikes,
          userVote: userVote,
        }"
        :config="{
          views: { show: true, count: props.item.stats.views },
          comments: {
            show: true,
            count: props.item.stats.comments,
            text: t('forum.comments.title'),
            icon: 'message-circle',
          },
          share: { show: true, text: t('forum.actions.share') },
          save: { show: true, text: t('forum.actions.save') },
        }"
        class="border-t pt-4"
        @vote="handleSolutionVote"
      />
    </section>

    <!-- Comments Section -->
    <div class="mt-4">
      <Separator class="mb-4" />
      <h3 class="text-sm font-semibold mb-4">
        {{ t("forum.comments.title") }}
      </h3>
      <CommentThread
        :comments="comments"
        :is-locked="false"
        @submit="handleCommentSubmit"
        @vote="handleCommentVote"
        @edit="handleCommentEdit"
        @delete="handleCommentDelete"
      />
    </div>
  </article>
</template>
