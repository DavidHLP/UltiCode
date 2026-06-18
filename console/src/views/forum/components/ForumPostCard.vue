<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import type { ForumFlairType, ForumPost } from "@/types/forum";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";

import { AspectRatio } from "@/components/ui/aspect-ratio";
import { Link as LinkIcon } from "lucide-vue-next";
import { PostActions } from "@/components/edge-operations";
import { computed, ref, watch } from "vue";
import { useAvatar } from "@/composables/useAvatar";
import { RouterLink, useRouter } from "vue-router";
import { renderMarkdown } from "@/utils/markdown";
import { sanitizeHtml } from "@/utils/sanitize";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";
import { toast } from "vue-sonner";
import { toggleBookmark, BookmarkType } from "@/api/bookmark";
import { recordForumShare } from "@/api/forum";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  post: ForumPost;
}>();

const { t, locale } = useI18n();

const localStats = ref({
  ...(props.post.stats || {
    comments: 0,
    likes: 0,
    dislikes: 0,
    saves: 0,
    shares: 0,
    views: 0,
  }),
});
watch(
  () => props.post.stats,
  (newStats) => {
    if (newStats) {
      localStats.value = { ...newStats };
    }
  },
  { deep: true },
);

const router = useRouter();
const emit = defineEmits<{
  (e: "vote", postId: string, type: 1 | -1): void;
  (e: "save", postId: string, isSaved: boolean): void;
}>();

const flairClasses: Record<ForumFlairType, string> = {
  announcement:
    "bg-[oklch(0.6545_0.1340_85.7_/_0.12)] text-[var(--terminal-amber)]",
  discussion:
    "bg-[oklch(0.6149_0.1394_244.9_/_0.12)] text-[var(--accent-electric)]",
  showcase:
    "bg-[oklch(0.5924_0.2025_355.9_/_0.12)] text-[var(--terminal-purple)]",
  question:
    "bg-[oklch(0.6444_0.1508_118.6_/_0.12)] text-[var(--terminal-green)]",
  hiring: "bg-[oklch(0.6545_0.1340_85.7_/_0.12)] text-[var(--terminal-amber)]",
};

const userInitials = computed(() => {
  if (!props.post.author?.username) {
    return "?";
  }
  const parts = props.post.author.username.split(/[\s_-]/);
  return parts
    .map((part: string) => part.charAt(0).toUpperCase())
    .join("")
    .slice(0, 2);
});

const { normalizedAvatar } = useAvatar(
  computed(() => props.post.author?.username),
  computed(() => props.post.author?.avatar),
);

const createdAgo = computed(() => formatRelativeTime(props.post.createdAt));

type PostMediaItem = {
  kind?: string;
  ratio?: number;
  src?: string;
  alt?: string;
  caption?: string;
  thumbnail?: string;
  title?: string;
  domain?: string;
  description?: string;
  url?: string;
  body?: string;
  markdown?: string;
  controls?: boolean;
  autoplay?: boolean;
  poster?: string;
  duration?: string;
  question?: string;
  options?: Array<{ id: string; label: string; votes: number }>;
  totalVotes?: number;
  closesAt?: string;
};

const media = computed(() => {
  const m = props.post.media as unknown as
    | PostMediaItem
    | Array<PostMediaItem>
    | undefined;

  if (Array.isArray(m)) {
    return m.find((item) => item.kind === "image" || item.kind === "video");
  }
  return m;
});

const commentsDisplay = computed(() =>
  formatCount(localStats.value.comments ?? 0),
);

const savesDisplay = computed(() => formatCount(localStats.value.saves ?? 0));

const userVote = computed(() =>
  resolveUserVote(props.post.userVote, props.post.voteState),
);

const voteCounts = computed(() =>
  resolveVoteCounts(undefined, undefined, localStats.value),
);

function formatCount(value: number) {
  if (value >= 1000) {
    return `${(value / 1000).toFixed(1).replace(/\.0$/, "")}k`;
  }
  return value.toString();
}

const relativeTimeFormatter = computed(
  () =>
    new Intl.RelativeTimeFormat(locale.value, {
      numeric: "auto",
    }),
);

function formatRelativeTime(value: string) {
  const date = new Date(value);
  const diffMs = date.getTime() - Date.now();
  const ranges: [Intl.RelativeTimeFormatUnit, number][] = [
    ["year", 1000 * 60 * 60 * 24 * 365],
    ["month", 1000 * 60 * 60 * 24 * 30],
    ["week", 1000 * 60 * 60 * 24 * 7],
    ["day", 1000 * 60 * 60 * 24],
    ["hour", 1000 * 60 * 60],
    ["minute", 1000 * 60],
  ];

  for (const [unit, amountMs] of ranges) {
    const delta = diffMs / amountMs;
    if (Math.abs(delta) >= 1) {
      return relativeTimeFormatter.value.format(Math.round(delta), unit);
    }
  }

  return t("common.time.now");
}

function handleCommentClick() {
  router.push({ name: "forum-thread", params: { postId: props.post.id } });
}

async function handleShare() {
  const url = `${window.location.origin}/forum/detailed/${props.post.id}`;
  try {
    await navigator.clipboard.writeText(url);
    await recordForumShare(props.post.id);
    localStats.value.shares = (localStats.value.shares || 0) + 1;
    toast.success(t("forum.messages.linkCopied"));
  } catch (error) {
    console.error("Failed to copy link", error);
    toast.error(t("forum.messages.copyFailed"));
  }
}

async function handleSave() {
  if (!useAuthStore().isAuthenticated) {
    toast.error(t("forum.messages.loginToSave"));
    return;
  }
  try {
    const res = await toggleBookmark(BookmarkType.FORUM_POST, props.post.id);
    emit("save", props.post.id, res.isFavorited);
    localStats.value.saves = Math.max(
      0,
      (localStats.value.saves || 0) + (res.isFavorited ? 1 : -1),
    );
    toast.success(
      res.isFavorited ? t("forum.messages.saved") : t("forum.messages.unsaved"),
    );
  } catch (error) {
    console.error("Failed to toggle save", error);
    toast.error(t("forum.messages.saveFailed"));
  }
}

function handleCardClick(event: MouseEvent) {
  // If the user is selecting text, ignore navigation
  const selection = window.getSelection();
  if (selection && selection.toString()) {
    return;
  }

  const target = event.target as HTMLElement;

  // Do not navigate if user clicked on any interactive element
  if (
    target.closest(
      "a, button, [role='button'], input, select, textarea, video, audio, .avatar-trigger, .font-bold",
    )
  ) {
    return;
  }

  router.push({ name: "forum-thread", params: { postId: props.post.id } });
}
</script>

<template>
  <div
    class="terminal-card hover:bg-muted/10 transition-all duration-300 hover:shadow-md hover:-translate-y-0.5 cursor-pointer"
    @click="handleCardClick"
  >
    <div class="flex gap-4 p-5 sm:p-6">
      <div class="min-w-0 flex-1 space-y-3">
        <!-- Header -->
        <header
          class="flex items-center gap-2 text-xs font-mono text-[var(--solarized-base01)] dark:text-[var(--silver-400)]"
        >
          <Avatar
            class="h-9 w-9 rounded-none border border-silver avatar-trigger cursor-pointer"
            v-if="post.community?.icon"
          >
            <AvatarImage
              :src="post.community.icon"
              :alt="post.community.name"
            />
            <AvatarFallback class="text-xs">{{
              post.community.name.charAt(0).toUpperCase()
            }}</AvatarFallback>
          </Avatar>
          <Avatar
            class="h-9 w-9 rounded-none border border-silver avatar-trigger cursor-pointer"
            v-else
          >
            <AvatarImage
              :src="normalizedAvatar"
              :alt="post.author?.username || 'user'"
            />
            <AvatarFallback class="text-xs">{{ userInitials }}</AvatarFallback>
          </Avatar>
          <span class="flex items-center gap-1">
            <span
              v-if="post.community"
              class="font-bold text-[var(--accent-electric)] hover:underline cursor-pointer"
            >
              r/{{ post.community.name }}
            </span>
            <span
              v-else
              class="font-bold text-[var(--solarized-base01)] dark:text-[var(--silver-400)] hover:underline cursor-pointer"
            >
              u/{{ post.author?.username || "?" }}
            </span>
            <span class="text-muted-foreground/60">•</span>
            <span>{{ createdAgo }}</span>
            <span v-if="post.community" class="text-muted-foreground/60"
              >•</span
            >
            <span v-if="post.community"
              >{{ t("forum.post.postedBy") }} u/{{
                post.author?.username || "?"
              }}</span
            >
          </span>
          <Badge
            v-if="post.flair"
            variant="secondary"
            :class="[
              'ml-auto sm:ml-2 rounded-none px-2 py-0 text-2xs h-5',
              flairClasses[post.flair.type],
            ]"
          >
            {{ props.post.flair?.text }}
          </Badge>
        </header>

        <!-- Body -->
        <section class="space-y-2">
          <RouterLink
            :to="{ name: 'forum-thread', params: { postId: post.id } }"
            class="block"
          >
            <h3
              class="text-base sm:text-lg font-bold leading-snug text-foreground hover:text-[var(--accent-electric)] hover:underline transition-colors"
            >
              {{ post.title }}
            </h3>
          </RouterLink>

          <!-- Text Content / Excerpt -->
          <div
            v-if="
              post.excerpt &&
              !(media?.kind === 'image' || media?.kind === 'video')
            "
            class="markdown-view"
          >
            <div
              class="text-sm leading-relaxed text-muted-foreground line-clamp-3 prose prose-sm dark:prose-invert prose-p:my-0 prose-headings:my-0 prose-ul:my-0 prose-ol:my-0 max-w-none"
              v-html="sanitizeHtml(renderMarkdown(post.excerpt))"
            ></div>
          </div>

          <!-- Media -->
          <div
            v-if="media"
            class="mt-2 overflow-hidden rounded-none border border-silver bg-[var(--surface-sunken)]/50"
          >
            <AspectRatio
              v-if="media.kind === 'image'"
              :ratio="media.ratio ?? 16 / 9"
              class="w-full"
            >
              <img
                :src="media.src"
                :alt="media.alt ?? post.title"
                class="h-full w-full object-contain bg-black/5"
              />
            </AspectRatio>

            <div
              v-else-if="media.kind === 'link'"
              class="flex h-24 w-full overflow-hidden bg-background"
            >
              <div
                class="flex-1 p-3 flex flex-col justify-between overflow-hidden"
              >
                <div class="text-sm font-medium truncate">
                  {{ media.title ?? media.url }}
                </div>
                <div class="text-xs text-muted-foreground truncate">
                  {{ media.description }}
                </div>
                <div
                  class="flex items-center gap-1 text-xs text-[var(--accent-electric)]"
                >
                  <LinkIcon class="w-3 h-3" />
                  {{ media.domain }}
                </div>
              </div>
              <div v-if="media.thumbnail" class="w-32 h-full bg-muted">
                <img
                  :src="media.thumbnail"
                  class="w-full h-full object-cover"
                />
              </div>
            </div>

            <div v-else-if="media.kind === 'video'" class="bg-black">
              <AspectRatio :ratio="16 / 9">
                <video
                  :src="media.src"
                  class="h-full w-full"
                  :poster="media.thumbnail"
                  controls
                  playsinline
                />
              </AspectRatio>
            </div>

            <div
              v-else-if="
                media.kind === 'text' ||
                (media.markdown && media.markdown.length)
              "
              class="markdown-view"
            >
              <div
                class="prose prose-sm dark:prose-invert max-w-none p-3 text-sm leading-relaxed"
                v-html="
                  sanitizeHtml(
                    renderMarkdown(media.markdown || media.body || ''),
                  )
                "
              ></div>
            </div>
          </div>
        </section>

        <!-- Footer (Buttons) -->
        <div class="flex items-center gap-2 pt-1">
          <PostActions
            :vote="{
              likes: voteCounts.likes,
              dislikes: voteCounts.dislikes,
              userVote: userVote,
            }"
            :config="{
              comments: {
                show: true,
                count: commentsDisplay,
                text: t('forum.comments.title'),
                icon: 'message-square',
              },
              share: { show: true, text: t('forum.actions.share') },
              save: {
                show: true,
                isSaved: post.isSaved,
                count: savesDisplay,
                text: t('forum.actions.save'),
              },
            }"
            @vote="(type: 1 | -1) => emit('vote', post.id, type)"
            @comment="handleCommentClick"
            @share="handleShare"
            @save="handleSave"
          />
        </div>
      </div>
    </div>
  </div>
</template>
