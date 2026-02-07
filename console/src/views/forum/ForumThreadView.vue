<script setup lang="ts">
import type { ForumThread } from "@/types/forum";
import { Skeleton } from "@/components/ui/skeleton";
import ForumPostSkeleton from "@/views/forum/components/ForumPostSkeleton.vue";
import ThreadContent from "@/views/forum/components/ThreadContent.vue";
import { CommentThread } from "@/components/comments";
import {
  fetchForumThread,
  createForumComment,
  updateForumComment,
  deleteForumComment,
  deleteForumPost,
  recordForumView,
} from "@/api/forum";
import { ref, watch } from "vue";
import { useRoute, RouterLink, useRouter } from "vue-router";
import { ArrowLeft } from "lucide-vue-next";
import { toast } from "vue-sonner";
import { fetchCurrentUserId, isAuthenticated } from "@/utils/auth";
import { Button } from "@/components/ui/button";
import { useI18n } from "vue-i18n";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

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
    const userId = fetchCurrentUserId();
    thread.value = await fetchForumThread(postId, userId || undefined);
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
    if (postId) {
      recordForumView(postId).catch((e) =>
        console.error("Failed to record view", e),
      );
    }
  },
  { immediate: true },
);

async function onSubmitComment(body: string, parentId?: string | null) {
  if (!isAuthenticated()) {
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
  if (!isAuthenticated()) {
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
  if (!isAuthenticated()) {
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

// Vote Handling
import { vote, VoteTargetType } from "@/api/vote";

const isOwner = () => {
  const userId = fetchCurrentUserId();
  return !!userId && thread.value?.author?.id === userId;
};

async function handleThreadVote(type: 1 | -1) {
  if (!isAuthenticated()) {
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
  if (!isAuthenticated()) {
    toast.error(t("forum.messages.loginToVote"));
    return;
  }
  if (!thread.value?.comments) return;
  try {
    const res = await vote(
      VoteTargetType.FORUM_COMMENT,
      String(commentId),
      type,
    );

    // Find and update comment in the flat list
    const comment = thread.value.comments.find((c) => c.id === commentId);
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
</script>

<template>
  <div
    class="mx-auto flex w-full max-w-7xl items-start gap-6 px-4 py-8 animate-in fade-in slide-in-from-bottom-4 duration-500"
  >
    <main class="w-full min-w-0 flex-1 space-y-6">
      <div v-if="isLoading" class="space-y-6">
        <ForumPostSkeleton />
        <div class="space-y-4 pl-4 border-l border-border/40">
          <div class="flex gap-3" v-for="i in 3" :key="i">
            <Skeleton class="h-8 w-8 rounded-full" />
            <div class="space-y-2 flex-1">
              <Skeleton class="h-4 w-32" />
              <Skeleton class="h-4 w-full" />
            </div>
          </div>
        </div>
      </div>

      <template v-else-if="thread">
        <div class="relative flex items-start gap-0.25 sm:gap-0.5 xl:block">
          <RouterLink
            to="/forum"
            class="shrink-0 inline-flex h-9 w-9 items-center justify-center rounded-full bg-muted/50 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground mt-4 sm:mt-6 xl:absolute xl:right-full xl:mr-3"
          >
            <ArrowLeft class="h-5 w-5" />
          </RouterLink>

          <div class="flex-1 min-w-0 bg-card sm:rounded-xl overflow-hidden">
            <div
              v-if="isOwner()"
              class="flex flex-wrap items-center justify-end gap-2 px-4 sm:px-6 pt-4"
            >
              <Button variant="outline" size="sm" @click="handleEditThread">
                {{ t("forum.post.edit") }}
              </Button>
              <AlertDialog>
                <AlertDialogTrigger as-child>
                  <Button variant="destructive" size="sm">{{
                    t("forum.post.delete")
                  }}</Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>{{
                      t("forum.post.deleteDialog.title")
                    }}</AlertDialogTitle>
                    <AlertDialogDescription>
                      {{ t("forum.post.deleteDialog.description") }}
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>{{
                      t("forum.post.deleteDialog.cancel")
                    }}</AlertDialogCancel>
                    <AlertDialogAction @click="handleDeleteThread">
                      {{ t("forum.post.deleteDialog.confirm") }}
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>
            <ThreadContent
              :thread="thread"
              @vote="handleThreadVote"
              @save="handleThreadSave"
            />
            <div class="px-4 sm:px-6 bg-muted/10 h-2"></div>
            <CommentThread
              :comments="thread.comments"
              :is-locked="thread.isLocked"
              @submit="onSubmitComment"
              @vote="handleCommentVote"
              @edit="onEditComment"
              @delete="onDeleteComment"
            />
          </div>
        </div>
      </template>

      <div
        v-else
        class="rounded-xl border border-dashed border-destructive/40 bg-destructive/5 p-6 text-sm text-destructive"
      >
        {{ t("forum.comments.failedToLoad") }}
      </div>
    </main>
  </div>
</template>
