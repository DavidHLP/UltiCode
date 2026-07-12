import { ref, watch, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import type { ForumComment, ForumThread } from "@/types/forum";
import { useAuthStore } from "@/stores/auth";
import {
  createForumComment,
  deleteForumComment,
  deleteForumPost,
  fetchForumThread,
  recordForumView,
  updateForumComment,
} from "@/api/forum";
import { vote, VoteTargetType } from "@/api/vote";
import { useAvatar } from "@/composables/useAvatar";
import { formatRelativeTime } from "@/shared/datetime-utils/src";

/**
 * Forum thread interaction module — owns the thread read/write state machine
 * behind one seam so {@link ForumThreadView} is left with rendering and the
 * DOM-bound scroll/TOC adapters.
 *
 * Concentrates: thread load (+ view-recording), comment submit/edit/delete
 * with reload reconciliation, thread delete/edit navigation, thread + comment
 * voting with optimistic score reconciliation, save toggle, and author/avatar
 * projection. The view destructures the returned handles; templates bind the
 * same names the inline script used before.
 */
export function useForumThread() {
  const route = useRoute();
  const router = useRouter();
  const { t } = useI18n();

  const thread = ref<ForumThread | null>(null);
  const isLoading = ref(true);
  const deleting = ref(false);

  async function loadThread(postId: string) {
    if (!postId) return;
    isLoading.value = true;
    try {
      thread.value = await fetchForumThread(postId);
    } catch (error) {
      console.error("Failed to load forum thread", error);
      thread.value = null;
    } finally {
      isLoading.value = false;
    }
  }

  watch(
    () => route.params.postId as string,
    (postId) => {
      void loadThread(postId);
      if (postId && useAuthStore().isAuthenticated) {
        recordForumView(postId).catch(() => {
          // Non-critical analytics — silently ignore CSRF rotation / transient failures
        });
      }
    },
    { immediate: true },
  );

  async function onSubmitComment(body: string, parentId?: string | null) {
    if (!useAuthStore().isAuthenticated) {
      toast.error(t("forum.messages.loginToComment"));
      return;
    }
    const postId = route.params.postId as string;
    try {
      await createForumComment(postId, body, parentId);
      await loadThread(postId);
    } catch (error) {
      console.error("Failed to create comment", error);
      toast.error(t("forum.comments.submitFailed"));
    }
  }

  async function onEditComment(commentId: string | number, body: string) {
    if (!useAuthStore().isAuthenticated) {
      toast.error(t("forum.messages.loginToEdit"));
      return;
    }
    try {
      await updateForumComment(String(commentId), body);
      await loadThread(route.params.postId as string);
    } catch (error) {
      console.error("Failed to edit comment", error);
      toast.error(t("forum.messages.commentEditFailed"));
    }
  }

  async function onDeleteComment(commentId: string | number) {
    if (!useAuthStore().isAuthenticated) {
      toast.error(t("forum.messages.loginToDelete"));
      return;
    }
    try {
      await deleteForumComment(String(commentId));
      await loadThread(route.params.postId as string);
    } catch (error) {
      console.error("Failed to delete comment", error);
      toast.error(t("forum.messages.commentDeleteFailed"));
    }
  }

  async function handleDeleteThread() {
    if (!thread.value || deleting.value) return;
    deleting.value = true;
    try {
      await deleteForumPost(thread.value.id);
      toast.success(t("forum.messages.postDeleted"));
      await router.push({ name: "forum-home" });
    } catch (error) {
      console.error("Failed to delete post", error);
      toast.error(t("forum.messages.deleteFailed"));
    } finally {
      deleting.value = false;
    }
  }

  function handleEditThread() {
    if (!thread.value) return;
    router.push({ name: "forum-edit", params: { postId: thread.value.id } });
  }

  const isOwner = () => {
    const userId = useAuthStore().fetchCurrentUserId();
    return !!userId && thread.value?.author?.id === userId;
  };

  async function handleThreadVote(type: 1 | -1) {
    if (!useAuthStore().isAuthenticated) {
      toast.error(t("forum.messages.loginToVote"));
      return;
    }
    if (!thread.value) return;
    try {
      const res = await vote(VoteTargetType.FORUM_POST, thread.value.id, type);
      if (thread.value.stats) {
        thread.value.stats.score = (res.likes || 0) - (res.dislikes || 0);
        thread.value.stats.likes = res.likes;
        thread.value.stats.dislikes = res.dislikes;
      }
      thread.value.voteState =
        res.userVote === 1
          ? "upvoted"
          : res.userVote === -1
            ? "downvoted"
            : "neutral";
      thread.value.userVote = res.userVote;
    } catch (error) {
      console.error("Failed to vote thread", error);
    }
  }

  async function handleCommentVote(commentId: string | number, type: 1 | -1) {
    if (!useAuthStore().isAuthenticated) {
      toast.error(t("forum.messages.loginToVote"));
      return;
    }
    if (!thread.value?.comments) return;
    try {
      const res = await vote(VoteTargetType.FORUM_COMMENT, String(commentId), type);
      const comment = findCommentById(thread.value.comments, String(commentId));
      if (comment) {
        comment.likes = res.likes;
        comment.dislikes = res.dislikes;
        comment.userVote = res.userVote;
      }
    } catch (error) {
      console.error("Failed to vote comment", error);
    }
  }

  function handleThreadSave(isSaved: boolean) {
    if (thread.value) {
      thread.value.isSaved = isSaved;
    }
  }

  // --- Author Info & Avatar projection ---
  const authorUsername = computed(() => thread.value?.author?.username);
  const authorAvatar = computed(() => thread.value?.author?.avatar);
  const { normalizedAvatar: authorAvatarUrl } = useAvatar(authorUsername, authorAvatar);
  const authorInitial = computed(() => {
    if (!thread.value?.author?.username) return "?";
    return thread.value.author.username.charAt(0).toUpperCase();
  });
  const createdAgo = computed(() =>
    thread.value ? formatRelativeTime(thread.value.createdAt) : "",
  );

  return {
    thread,
    isLoading,
    deleting,
    onSubmitComment,
    onEditComment,
    onDeleteComment,
    handleDeleteThread,
    handleEditThread,
    isOwner,
    handleThreadVote,
    handleCommentVote,
    handleThreadSave,
    countThreadComments,
    authorUsername,
    authorAvatarUrl,
    authorInitial,
    createdAgo,
  };
}

function findCommentById(
  comments: ForumComment[],
  commentId: string,
): ForumComment | undefined {
  for (const comment of comments) {
    if (comment.id === commentId) return comment;
    const match = findCommentById(comment.replies ?? [], commentId);
    if (match) return match;
  }
  return undefined;
}

function countThreadComments(comments: ForumComment[] = []): number {
  return comments.reduce(
    (total, comment) => total + 1 + countThreadComments(comment.replies ?? []),
    0,
  );
}
