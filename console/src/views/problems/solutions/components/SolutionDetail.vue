<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import type { SolutionFeedItem } from "@/types/solution";
import type { SolutionComment } from "@/types/comment";
import MarkdownView from "@/components/markdown/MarkdownView.vue";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAvatar } from "@/composables/useAvatar";
import { Badge } from "@/components/ui/badge";
import { computed, ref, watch, onMounted, onBeforeUnmount } from "vue";
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
import { toast } from "vue-sonner";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";
import { formatRelativeTime } from "@/utils/date";
import { Button } from "@/components/ui/button";
import { useRouter } from "vue-router";
import { Pencil, Trash2, Flag, List } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { useErrorHandler } from "@/composables/useErrorHandler";
import ReportDialog from "@/components/ReportDialog.vue";

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

const { normalizedAvatar: authorAvatarUrl } = useAvatar(
  computed(() => props.item.author.username),
  computed(() => props.item.author.avatar),
);

const topicLabel = computed(
  () =>
    props.item.topicName ||
    props.item.topicTranslated ||
    props.item.topic ||
    t("forum.post.flair"),
);

const formattedDate = computed(() => formatRelativeTime(props.item.created_at));

const languageLabel = computed(() => {
  const lang = props.item.language;
  if (!lang) return "";
  return lang.charAt(0).toUpperCase() + lang.slice(1).toLowerCase();
});

const comments = ref<SolutionComment[]>([]);
const localStats = ref<{ likes: number; dislikes: number }>({
  likes: 0,
  dislikes: 0,
});
const userVote = ref<0 | 1 | -1>(0);
const isOwner = computed(() => {
  const userId = useAuthStore().fetchCurrentUserId();
  return (
    Boolean(userId) &&
    props.item.id !== "follow-up" &&
    props.item.authorId === userId
  );
});

const reportDialogRef = ref<{ open: () => void } | null>(null);

const handleReport = () => {
  reportDialogRef.value?.open();
};

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

// --- Table of Contents & Scroll Spy ---
const headings = computed(() => {
  const content = props.item.content ?? "";
  const lines = content.split("\n");
  const result: { id: string; text: string; level: number }[] = [];
  let codeBlock = false;

  lines.forEach((line) => {
    const trimmed = line.trim();
    if (trimmed.startsWith("```")) {
      codeBlock = !codeBlock;
    }
    if (codeBlock) return;

    // Match ## heading or ### heading
    const match = trimmed.match(/^(#{2,3})\s+(.+)$/);
    if (match) {
      const level = match[1].length;
      const text = match[2].trim();
      const id = text.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, "-");
      result.push({ id, text, level });
    }
  });

  // Always append comments section if comments exist or is follow-up
  result.push({
    id: "comments-section",
    text: t("forum.comments.title"),
    level: 2,
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
  const el = document.getElementById(id);
  if (el) {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
    activeHeadingId.value = id;
  }
};

const handleMobileTOCClick = (id: string) => {
  scrollToHeading(id);
  showMobileTOC.value = false;
};

const loadComments = async () => {
  if (!props.item.id || props.item.id === "follow-up") {
    comments.value = [];
    return;
  }
  try {
    comments.value = await fetchSolutionComments(props.item.id);
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
  if (!useAuthStore().isAuthenticated) {
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
  if (!useAuthStore().isAuthenticated) {
    toast.error(t("problem.solutions.loginToVote"));
    return;
  }
  try {
    const res = await vote(
      VoteTargetType.SOLUTION_COMMENT,
      String(commentId),
      voteType,
    );

    // Recursive helper to find and update comment immutably
    const updateCommentInList = (
      list: SolutionComment[],
    ): SolutionComment[] => {
      return list.map((comment) => {
        if (comment.id === commentId) {
          return {
            ...comment,
            likes: res.likes,
            dislikes: res.dislikes,
            userVote: res.userVote,
          };
        }
        if (comment.replies && comment.replies.length > 0) {
          return { ...comment, replies: updateCommentInList(comment.replies) };
        }
        return comment;
      });
    };

    comments.value = updateCommentInList(comments.value);
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
  <div
    ref="wrapperRef"
    class="solution-detail-page-wrapper w-full p-0 py-2 relative min-h-[300px]"
  >
    <div
      class="w-full flex flex-col lg:flex-row gap-6 items-start justify-center max-w-6xl mx-auto px-1 lg:px-4 relative"
    >
      <!-- Left Main Column: Elegant Reading Card -->
      <article
        class="group w-full bg-[var(--card)] border border-border shadow-sm p-6 md:p-8 relative rounded-none flex flex-col gap-5"
        :class="[isWideLayout ? 'flex-1 min-w-0 max-w-3xl' : 'w-full']"
      >
        <!-- Header: Author & Metadata (visible on all screens, but primary on mobile) -->
        <header class="flex items-start gap-4 border-b border-border/50 pb-5">
          <Avatar class="h-10 w-10 border border-border/50 shrink-0">
            <AvatarImage :src="authorAvatarUrl" :alt="props.item.author.name" />
            <AvatarFallback
              class="text-xs font-semibold text-white"
              :style="{ backgroundColor: props.item.author.avatarColor }"
            >
              {{ authorInitial }}
            </AvatarFallback>
          </Avatar>

          <div class="flex-1 min-w-0 flex flex-col gap-1">
            <div
              class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs leading-none"
            >
              <span
                class="font-bold text-[var(--solarized-base03)] dark:text-foreground"
              >
                {{ props.item.author.name }}
              </span>
              <span class="truncate text-muted-foreground max-w-[120px]">
                {{ props.item.author.role }}
              </span>
              <span class="text-muted-foreground/60 select-none">·</span>
              <span class="text-muted-foreground">
                {{ formattedDate }}
              </span>
              <Badge
                v-if="props.item.flair"
                variant="secondary"
                class="rounded-none bg-[var(--terminal-amber)]/20 px-2 py-0.5 text-2xs font-semibold uppercase tracking-wide text-[var(--terminal-amber)] hover:bg-[var(--terminal-amber)]/20 border border-[var(--terminal-amber)]/30 select-none"
              >
                {{ props.item.flair }}
              </Badge>
            </div>

            <div class="flex flex-wrap items-center gap-2 mt-1">
              <Badge
                variant="secondary"
                class="rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2 py-0.5 text-2xs capitalize text-[var(--silver-600)] bg-[var(--surface-sunken)] select-none font-medium"
              >
                {{ languageLabel }}
              </Badge>
              <Badge
                variant="secondary"
                class="rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2 py-0.5 text-2xs capitalize text-[var(--silver-600)] bg-[var(--surface-sunken)] select-none font-medium"
              >
                {{ topicLabel }}
              </Badge>
              <Badge
                v-for="badge in props.item.badges"
                :key="badge"
                variant="outline"
                class="rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2 py-0.5 text-2xs font-medium text-[var(--silver-500)] bg-transparent select-none"
              >
                {{ badge }}
              </Badge>
            </div>
          </div>

          <!-- Quick Owner/Moderator Actions -->
          <div class="ml-auto flex items-center gap-1">
            <!-- Mobile Table of Contents button (narrow container only) -->
            <div v-if="!isWideLayout && headings.length > 1" class="relative">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                class="h-7 px-2 text-xs font-semibold hover:bg-[var(--surface-sunken)] rounded-none"
                @click="showMobileTOC = !showMobileTOC"
              >
                <List class="mr-1 h-3.5 w-3.5" />
                目录
              </Button>

              <!-- Floating TOC Dropdown Popup -->
              <div
                v-if="showMobileTOC"
                class="absolute right-0 top-8 z-50 w-48 bg-[var(--card)] border border-border p-3 shadow-lg flex flex-col gap-2 rounded-none select-none font-mono"
              >
                <div
                  class="text-2xs font-bold uppercase tracking-wider text-muted-foreground/85 border-b border-border/50 pb-1"
                >
                  文章大纲
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
                    :style="{ paddingLeft: h.level === 3 ? '1rem' : '0.25rem' }"
                    @click="handleMobileTOCClick(h.id)"
                  >
                    {{ h.text }}
                  </button>
                </nav>
              </div>
            </div>

            <template v-if="isOwner">
              <Button
                variant="ghost"
                size="sm"
                class="h-7 px-2 text-xs font-semibold hover:bg-[var(--surface-sunken)] rounded-none"
                @click="handleEditSolution"
              >
                <Pencil class="mr-1 h-3.5 w-3.5" />
                {{ t("common.actions.edit") }}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                class="h-7 px-2 text-xs font-semibold text-destructive hover:text-destructive hover:bg-destructive/10 rounded-none"
                @click="handleDeleteSolution"
              >
                <Trash2 class="mr-1 h-3.5 w-3.5" />
                {{ t("common.actions.delete") }}
              </Button>
            </template>
            <Button
              v-else
              variant="ghost"
              size="sm"
              class="h-7 px-2 text-xs text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-none"
              @click="handleReport"
            >
              <Flag class="mr-1 h-3.5 w-3.5" />
              举报
            </Button>
          </div>
        </header>

        <!-- Solution Content -->
        <section class="space-y-4 text-sm leading-relaxed">
          <!-- Solution Title -->
          <h1
            class="text-xl font-bold tracking-tight text-[var(--solarized-base03)] dark:text-foreground mb-4 leading-tight"
          >
            {{ props.item.title }}
          </h1>

          <!-- Markdown Body -->
          <MarkdownView
            :content="props.item.content ?? ''"
            :editor-id="`solution-${props.item.id}`"
          />

          <!-- Tags -->
          <div v-if="props.item.tags.length" class="flex flex-wrap gap-2 pt-4">
            <Badge
              v-for="tag in props.item.tags"
              :key="tag"
              variant="secondary"
              class="rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2.5 py-0.5 text-2xs text-[var(--silver-500)] bg-[var(--surface-sunken)] font-mono select-none"
            >
              # {{ tag }}
            </Badge>
          </div>

          <!-- Bottom Actions (Vote, Share, Save) -->
          <div class="border-t border-border/50 pt-4 mt-6">
            <PostActions
              :vote="{
                likes: localStats.likes,
                dislikes: localStats.dislikes,
                userVote: userVote,
              }"
              :config="{
                views: { show: true, count: props.item.stats?.views ?? 0 },
                comments: {
                  show: true,
                  count: props.item.stats?.comments ?? 0,
                  text: t('forum.comments.title'),
                  icon: 'message-circle',
                },
                share: { show: true, text: t('forum.actions.share') },
                save: { show: true, text: t('forum.actions.save') },
              }"
              @vote="handleSolutionVote"
            />
          </div>
        </section>

        <!-- Comments Thread Area -->
        <div id="comments-section" class="mt-8 border-t border-border/60 pt-6">
          <div class="flex items-center gap-2 mb-4">
            <div class="h-4 w-1 bg-[var(--solarized-blue)]"></div>
            <h3
              class="text-sm font-bold text-[var(--solarized-base03)] dark:text-foreground uppercase tracking-wide select-none"
            >
              {{ t("forum.comments.title") }}
            </h3>
          </div>
          <CommentThread
            :comments="comments"
            comment-type="solution"
            :is-locked="false"
            @submit="handleCommentSubmit"
            @vote="handleCommentVote"
            @edit="handleCommentEdit"
            @delete="handleCommentDelete"
          />
        </div>
      </article>

      <!-- Right Column: Sticky Navigation Sidebar (Hidden on narrow container widths) -->
      <aside
        v-if="isWideLayout"
        class="flex flex-col gap-4 w-52 shrink-0 sticky top-4 select-none"
      >
        <!-- Author Profile Sidebar Widget -->
        <div
          class="bg-[var(--card)] border border-border p-4 shadow-sm flex flex-col gap-3"
        >
          <div class="flex items-center gap-2">
            <Avatar class="h-9 w-9 border border-border/40 shrink-0">
              <AvatarImage
                :src="authorAvatarUrl"
                :alt="props.item.author.name"
              />
              <AvatarFallback
                class="text-xs font-semibold text-white"
                :style="{ backgroundColor: props.item.author.avatarColor }"
              >
                {{ authorInitial }}
              </AvatarFallback>
            </Avatar>
            <div class="flex flex-col min-w-0">
              <span
                class="font-bold text-xs text-[var(--solarized-base03)] dark:text-foreground truncate leading-tight"
              >
                {{ props.item.author.name }}
              </span>
              <span
                class="text-2xs text-muted-foreground truncate leading-none mt-0.5"
              >
                {{ props.item.author.role }}
              </span>
            </div>
          </div>

          <div
            class="text-2xs text-muted-foreground/80 flex flex-col gap-1 border-t border-border/50 pt-2 font-mono"
          >
            <div>发布：{{ formattedDate }}</div>
            <div class="flex items-center gap-1.5 flex-wrap mt-1">
              <span
                class="inline-block px-1.5 py-0.5 bg-[var(--surface-sunken)] border border-border text-2xs text-[var(--solarized-base01)] capitalize"
              >
                {{ languageLabel }}
              </span>
              <span
                class="inline-block px-1.5 py-0.5 bg-[var(--surface-sunken)] border border-border text-2xs text-[var(--solarized-base01)] truncate max-w-[80px]"
              >
                {{ topicLabel }}
              </span>
            </div>
          </div>
        </div>

        <!-- Table of Contents (TOC) Widget -->
        <div
          v-if="headings.length > 1"
          class="bg-[var(--card)] border border-border shadow-sm p-4"
        >
          <div
            class="text-2xs font-bold uppercase tracking-wider text-muted-foreground/80 mb-2.5 border-b border-border/50 pb-1.5 select-none font-mono"
          >
            文章大纲
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
  </div>

  <ReportDialog
    v-if="props.item.id !== 'follow-up'"
    ref="reportDialogRef"
    entity-type="solution"
    :entity-id="props.item.id"
  />
</template>
