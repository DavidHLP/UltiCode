<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import type { ForumComment, ForumThread } from "@/types/forum";
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
import { ref, watch, computed, onMounted, onBeforeUnmount } from "vue";
import { useRoute, RouterLink, useRouter } from "vue-router";
import { ArrowLeft, MessageSquare, Flag, List } from "lucide-vue-next";
import { toast } from "vue-sonner";
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
import ReportDialog from "@/components/ReportDialog.vue";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAvatar } from "@/composables/useAvatar";
import { formatRelativeTime } from "@/utils/date";

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

// Vote Handling
import { vote, VoteTargetType } from "@/api/vote";

const isOwner = () => {
  const userId = useAuthStore().fetchCurrentUserId();
  return !!userId && thread.value?.author?.id === userId;
};

const reportDialogRef = ref<{ open: () => void } | null>(null);

const handleReport = () => {
  reportDialogRef.value?.open();
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
    const res = await vote(
      VoteTargetType.FORUM_COMMENT,
      String(commentId),
      type,
    );

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

function handleThreadSave(isSaved: boolean) {
  if (thread.value) {
    thread.value.isSaved = isSaved;
  }
}

// --- Author Info & Avatar ---
const authorUsername = computed(() => thread.value?.author?.username);
const authorAvatar = computed(() => thread.value?.author?.avatar);
const { normalizedAvatar: authorAvatarUrl } = useAvatar(
  authorUsername,
  authorAvatar,
);

const authorInitial = computed(() => {
  if (!thread.value?.author?.username) return "?";
  return thread.value.author.username.charAt(0).toUpperCase();
});

const createdAgo = computed(() =>
  thread.value ? formatRelativeTime(thread.value.createdAt) : "",
);

// --- Table of Contents & Scroll Spy ---
const getPostContent = () => {
  if (!thread.value) return "";
  // Prefer explicit body, then excerpt (current backend stores the full
  // markdown under `excerpt`), then text-typed media attachments.
  if (thread.value.body) return thread.value.body;
  if (thread.value.excerpt) return thread.value.excerpt;
  if (thread.value.media) {
    const m = thread.value.media as {
      type?: string;
      markdown?: string;
      body?: string;
    };
    if (m && m.type === "text") {
      return m.markdown || m.body || "";
    } else if (Array.isArray(m)) {
      const textMedia = m.find(
        (item: { type?: string; markdown?: string; body?: string }) =>
          item.type === "text",
      );
      if (textMedia) return textMedia.markdown || textMedia.body || "";
    }
  }
  return "";
};

const slugifyHeading = (text: string) =>
  text
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, "-");

const headings = computed(() => {
  const content = getPostContent();
  const lines = content.split("\n");
  const result: { id: string; text: string; level: number; exists: boolean }[] =
    [];
  let codeBlock = false;
  const seenIds = new Set<string>();

  const pushUnique = (entry: {
    id: string;
    text: string;
    level: number;
    exists: boolean;
  }) => {
    let id = entry.id;
    let suffix = 2;
    while (seenIds.has(id)) {
      id = `${entry.id}-${suffix++}`;
    }
    seenIds.add(id);
    result.push({ ...entry, id });
  };

  // First pass: explicit ## / ### markdown headings (rendered as <h2>/<h3> by
  // renderMarkdown, which assigns a matching `id`).
  lines.forEach((line: string) => {
    const trimmed = line.trim();
    if (trimmed.startsWith("```")) {
      codeBlock = !codeBlock;
      return;
    }
    if (codeBlock) return;
    const match = trimmed.match(/^(#{2,3})\s+(.+)$/);
    if (match) {
      const level = match[1].length;
      const text = match[2].trim();
      pushUnique({
        id: slugifyHeading(text),
        text,
        level,
        exists: true,
      });
    }
  });

  // Fallback pass: when there are few/zero headings, harvest bold lead-ins
  // and short standalone bold lines so short posts still get a useful TOC.
  if (result.length < 2) {
    lines.forEach((line: string) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("```")) return;
      if (trimmed.startsWith("#")) return;
      const boldLead = trimmed.match(/^\*\*([^*][^*]*?):\*\*\s*$/);
      if (boldLead) {
        const text = boldLead[1].trim();
        pushUnique({
          id: slugifyHeading(text),
          text,
          level: 3,
          exists: false,
        });
        return;
      }
      const standaloneBold = trimmed.match(/^\*\*([^*][^*]*?)\*\*\s*$/);
      if (standaloneBold) {
        const text = standaloneBold[1].trim();
        if (text.length <= 40) {
          pushUnique({
            id: slugifyHeading(text),
            text,
            level: 3,
            exists: false,
          });
        }
      }
    });
  }

  // Always append comments section
  pushUnique({
    id: "comments-section",
    text: t("forum.comments.title"),
    level: 2,
    exists: true,
  });

  return result;
});

const activeHeadingId = ref<string>("");
let spyObserver: IntersectionObserver | null = null;

const setupScrollSpy = () => {
  if (spyObserver) {
    spyObserver.disconnect();
  }

  spyObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          activeHeadingId.value = entry.target.id;
        }
      });
    },
    {
      root: null,
      rootMargin: "-80px 0px -50% 0px",
      threshold: 0.1,
    },
  );

  headings.value.forEach((h) => {
    const el = document.getElementById(h.id);
    if (el) {
      spyObserver?.observe(el);
    }
  });
};

watch(
  () => headings.value,
  () => {
    setTimeout(setupScrollSpy, 250);
  },
  { immediate: true, deep: true },
);

// --- Responsive Layout Container Query / Resize Observer ---
const wrapperRef = ref<HTMLElement | null>(null);
const containerWidth = ref(0);
const isWideLayout = computed(() => containerWidth.value >= 900);
const showMobileTOC = ref(false);

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  setTimeout(setupScrollSpy, 500);

  if (wrapperRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        containerWidth.value = entry.contentRect.width;
      }
    });
    resizeObserver.observe(wrapperRef.value);
  }
});

onBeforeUnmount(() => {
  if (spyObserver) {
    spyObserver.disconnect();
  }
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

const scrollToHeading = (id: string) => {
  let el = document.getElementById(id);
  if (!el) {
    // Fallback entries aren't real headings; locate the matching rendered
    // <strong> text and scroll/highlight it instead.
    const heading = headings.value.find((h) => h.id === id);
    if (heading && heading.exists === false) {
      const strongs = document.querySelectorAll(
        ".markdown-view strong, .markdown-view p",
      );
      for (const node of Array.from(strongs)) {
        if (
          (node.textContent || "").trim().replace(/[:：]\s*$/, "") ===
          heading.text
        ) {
          el = node as HTMLElement;
          break;
        }
      }
    }
  }
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
    activeHeadingId.value = id;
    if (el.tagName !== "H2" && el.tagName !== "H3") {
      el.classList.add("ring-2", "ring-[var(--solarized-blue)]", "rounded-sm");
      window.setTimeout(() => {
        el?.classList.remove(
          "ring-2",
          "ring-[var(--solarized-blue)]",
          "rounded-sm",
        );
      }, 1500);
    }
  }
};

const handleMobileTOCClick = (id: string) => {
  scrollToHeading(id);
  showMobileTOC.value = false;
};
</script>

<template>
  <div
    ref="wrapperRef"
    class="mx-auto flex w-full max-w-7xl items-start gap-6 px-4 py-8 relative"
  >
    <main
      class="min-w-0 space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500"
      :class="[isWideLayout ? 'max-w-4xl flex-1' : 'w-full']"
    >
      <div v-if="isLoading" class="space-y-6">
        <ForumPostSkeleton />
        <div class="space-y-4 pl-4 border-l border-border/40">
          <div class="flex gap-3" v-for="i in 3" :key="i">
            <Skeleton class="h-8 w-8 rounded-none" />
            <div class="space-y-2 flex-1">
              <Skeleton class="h-4 w-32" />
              <Skeleton class="h-4 w-full" />
            </div>
          </div>
        </div>
      </div>

      <template v-else-if="thread">
        <div class="space-y-4">
          <div class="terminal-card overflow-hidden w-full">
            <div
              class="flex flex-wrap items-center gap-2 px-4 sm:px-6 pt-4"
            >
              <!-- Back Link: placed in the toolbar so the right sidebar's
                   Author Profile card shares the thread card's top edge -->
              <RouterLink
                to="/forum"
                class="inline-flex h-8 px-3 items-center justify-center rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] text-[var(--solarized-base01)] dark:text-[var(--solarized-base0)] hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-200)] hover:text-[var(--solarized-base03)] dark:hover:text-foreground hover:border-[var(--silver-300)] active:bg-[var(--silver-200)] dark:active:bg-[var(--silver-300)] active:scale-[0.98] transition-all font-bold text-xs shadow-sm"
              >
                <ArrowLeft class="h-3.5 w-3.5 mr-1.5" />
                <span>{{ t("forum.feedback.backToDiscussions") }}</span>
              </RouterLink>
              <!-- Mobile Table of Contents button (narrow container only) -->
              <div v-if="!isWideLayout && headings.length > 1" class="relative">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  class="h-8 px-2 text-xs font-semibold hover:bg-[var(--surface-sunken)] rounded-none"
                  @click="showMobileTOC = !showMobileTOC"
                >
                  <List class="mr-1 h-3.5 w-3.5" />
                  目录
                </Button>

                <!-- Floating TOC Dropdown Popup -->
                <div
                  v-if="showMobileTOC"
                  class="absolute right-0 top-9 z-50 w-48 bg-[var(--card)] border border-border p-3 shadow-lg flex flex-col gap-2 rounded-none select-none font-mono"
                >
                  <div
                    class="text-2xs font-bold uppercase tracking-wider text-muted-foreground/85 border-b border-border/50 pb-1"
                  >
                    {{ t("forum.sidebar.toc") }}
                  </div>
                  <nav class="flex flex-col gap-1 max-h-60 overflow-y-auto">
                    <button
                      v-for="h in headings"
                      :key="h.id"
                      type="button"
                      class="text-left text-xs py-1 px-1.5 transition-all duration-150 border-l-2 hover:bg-[var(--surface-sunken)] hover:text-foreground cursor-pointer select-none"
                      :class="[
                        activeHeadingId === h.id
                          ? 'border-[var(--solarized-blue)] text-[var(--solarized-blue)] font-bold bg-[var(--solarized-blue)]/5'
                          : 'border-transparent text-muted-foreground font-medium',
                      ]"
                      :style="{
                        paddingLeft: h.level === 3 ? '1rem' : '0.25rem',
                      }"
                      @click="handleMobileTOCClick(h.id)"
                    >
                      {{ h.text }}
                    </button>
                  </nav>
                </div>
              </div>

              <template v-if="isOwner()">
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
              </template>
              <Button
                v-else
                variant="ghost"
                size="sm"
                class="h-8 px-2 text-xs text-muted-foreground hover:text-destructive"
                @click="handleReport"
              >
                <Flag class="mr-1 h-3.5 w-3.5" />
                举报
              </Button>
            </div>
            <ThreadContent
              :thread="thread"
              @vote="handleThreadVote"
              @save="handleThreadSave"
            />
            <div
              id="comments-section"
              class="px-4 sm:px-6 py-4 border-t border-border/50"
            >
              <h2
                class="text-sm font-bold tracking-tight flex items-center gap-2"
              >
                <MessageSquare class="h-4 w-4" />
                {{ t("forum.comments.title") }}
                <span class="text-muted-foreground font-normal">
                  ({{ countThreadComments(thread.comments) }})
                </span>
              </h2>
            </div>
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
        class="rounded-none border border-dashed border-destructive/40 bg-destructive/5 p-6 text-sm text-destructive"
      >
        {{ t("forum.comments.failedToLoad") }}
      </div>
    </main>

    <!-- Right Sidebar Column: details & sticky TOC -->
    <aside
      v-if="isWideLayout && thread"
      class="flex flex-col gap-4 w-60 shrink-0 self-stretch sticky top-14 max-h-[calc(100vh-4rem)] overflow-y-auto select-none"
    >
      <!-- Author Profile Card -->
      <div
        class="terminal-card overflow-hidden bg-[var(--card)] border border-border p-4 flex flex-col gap-3 w-full max-w-[16rem]"
      >
        <div class="flex items-center gap-2">
          <Avatar class="h-9 w-9 border border-border/40 shrink-0">
            <AvatarImage
              :src="authorAvatarUrl"
              :alt="thread.author?.username"
            />
            <AvatarFallback
              class="text-xs font-semibold bg-primary/10 text-primary"
            >
              {{ authorInitial }}
            </AvatarFallback>
          </Avatar>
          <div class="flex flex-col min-w-0">
            <span
              class="font-bold text-xs text-[var(--solarized-base03)] dark:text-foreground truncate leading-tight"
            >
              u/{{ thread.author?.username || "unknown" }}
            </span>
            <span
              class="text-2xs text-muted-foreground truncate leading-none mt-0.5"
              v-if="thread.author?.karma !== undefined"
            >
              Reputation: {{ thread.author.karma }}
            </span>
          </div>
        </div>

        <div
          class="text-2xs text-muted-foreground/80 flex flex-col gap-1 border-t border-border/50 pt-2 font-mono"
        >
          <div>发布于：{{ createdAgo }}</div>
          <div v-if="thread.community" class="truncate">
            社区：r/{{ thread.community.name }}
          </div>
          <div
            v-if="thread.flair"
            class="flex items-center gap-1.5 flex-wrap mt-1"
          >
            <span
              class="inline-block px-1.5 py-0.5 bg-[var(--surface-sunken)] border border-border text-2xs text-[var(--solarized-base01)]"
            >
              {{ thread.flair.text }}
            </span>
          </div>
        </div>
      </div>

      <!-- Table of Contents Widget -->
      <div
        v-if="headings.length > 1"
        class="terminal-card overflow-hidden bg-[var(--card)] border border-border p-4 self-stretch w-full max-w-[16rem]"
      >
        <div
          class="text-2xs font-bold uppercase tracking-wider text-muted-foreground/80 mb-2.5 border-b border-border/50 pb-1.5 select-none font-mono"
        >
          {{ t("forum.sidebar.toc") }}
        </div>
        <nav class="flex flex-col gap-1">
          <button
            v-for="h in headings"
            :key="h.id"
            type="button"
            class="text-left text-xs font-mono py-1 px-2 transition-all duration-150 border-l-2 select-none hover:bg-[var(--surface-sunken)] hover:text-foreground cursor-pointer"
            :class="[
              activeHeadingId === h.id
                ? 'border-[var(--solarized-blue)] text-[var(--solarized-blue)] font-bold bg-[var(--solarized-blue)]/5'
                : 'border-transparent text-muted-foreground font-medium',
            ]"
            :style="{ paddingLeft: h.level === 3 ? '1.25rem' : '0.5rem' }"
            @click="scrollToHeading(h.id)"
          >
            {{ h.text }}
          </button>
        </nav>
      </div>
    </aside>
  </div>

  <ReportDialog
    v-if="thread"
    ref="reportDialogRef"
    entity-type="forum_post"
    :entity-id="thread.id"
  />
</template>
