<script setup lang="ts">
import type { ForumFlairType, ForumPost } from "@/types/forum";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";

import { AspectRatio } from "@/components/ui/aspect-ratio";
import { Link as LinkIcon } from "lucide-vue-next";
import { PostActions } from "@/components/edge-operations";
import { computed, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { renderMarkdown } from "@/utils/markdown";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";
import { toast } from "vue-sonner";
import { toggleBookmark, BookmarkType } from "@/api/bookmark";
import { isAuthenticated } from "@/utils/auth";
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
  announcement: "bg-amber-100 text-amber-700",
  discussion: "bg-blue-100 text-blue-700",
  showcase: "bg-purple-100 text-purple-700",
  question: "bg-emerald-100 text-emerald-700",
  hiring: "bg-orange-100 text-orange-700",
};

const userInitials = computed(() => {
  const parts = props.post.author.username.split(/[\s_-]/);
  return parts
    .map((part: string) => part.charAt(0).toUpperCase())
    .join("")
    .slice(0, 2);
});
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
  resolveVoteCounts(props.post.likes, props.post.dislikes, localStats.value),
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
  if (!isAuthenticated()) {
    toast.error(t("forum.messages.loginToSave"));
    return;
  }
  try {
    const res = await toggleBookmark(BookmarkType.FORUM_POST, props.post.id);
    emit("save", props.post.id, res.isSaved);
    localStats.value.saves = Math.max(
      0,
      (localStats.value.saves || 0) + (res.isSaved ? 1 : -1),
    );
    toast.success(
      res.isSaved ? t("forum.messages.saved") : t("forum.messages.unsaved"),
    );
  } catch (error) {
    console.error("Failed to toggle save", error);
    toast.error(t("forum.messages.saveFailed"));
  }
}
</script>

<template>
  <div
    class="bg-card text-card-foreground hover:bg-muted/30 transition-colors border-b border-border/40 last:border-0"
  >
    <div class="flex gap-3 px-4 py-3 sm:gap-4 sm:px-6">
      <div class="min-w-0 flex-1 space-y-2">
        <!-- Header -->
        <header class="flex items-center gap-2 text-xs text-muted-foreground">
          <Avatar
            class="h-9 w-9 rounded-full border border-border/40"
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
            class="h-9 w-9 rounded-full border border-border/40"
            v-else-if="post.author.avatar"
          >
            <AvatarImage
              :src="post.author.avatar"
              :alt="post.author.username"
            />
            <AvatarFallback class="text-xs">{{ userInitials }}</AvatarFallback>
          </Avatar>
          <span class="flex items-center gap-1">
            <span
              v-if="post.community"
              class="font-bold text-foreground hover:underline cursor-pointer"
            >
              r/{{ post.community.name }}
            </span>
            <span
              v-else
              class="font-bold text-foreground hover:underline cursor-pointer"
            >
              u/{{ post.author.username }}
            </span>
            <span class="text-muted-foreground/60">•</span>
            <span>{{ createdAgo }}</span>
            <span v-if="post.community" class="text-muted-foreground/60"
              >•</span
            >
            <span v-if="post.community"
              >{{ t("forum.post.postedBy") }} u/{{ post.author.username }}</span
            >
          </span>
          <Badge
            v-if="post.flair"
            variant="secondary"
            :class="[
              'ml-auto sm:ml-2 rounded-full px-2 py-0 text-[10px] h-5',
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
              class="text-base sm:text-lg font-medium leading-snug text-foreground hover:underline decoration-2 decoration-transparent hover:decoration-current transition-all"
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
            class="text-sm text-muted-foreground line-clamp-[20] prose prose-sm dark:prose-invert prose-p:my-0 prose-headings:my-0 prose-ul:my-0 prose-ol:my-0 max-w-none"
            v-html="renderMarkdown(post.excerpt)"
          ></div>

          <!-- Media -->
          <div
            v-if="media"
            class="mt-2 overflow-hidden rounded-xl border border-border/40 bg-muted/40"
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
                <div class="flex items-center gap-1 text-xs text-blue-500">
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
              class="prose prose-sm dark:prose-invert max-w-none p-3"
              v-html="renderMarkdown(media.markdown || media.body || '')"
            ></div>
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
