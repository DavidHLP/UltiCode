<script setup lang="ts">
import { computed } from "vue";
import type { SolutionFeedItem } from "@/types/solution";
import { PostActions } from "@/components/edge-operations";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";
import { formatRelativeTime } from "@/utils/date";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  item: SolutionFeedItem;
}>();

const emit = defineEmits<{
  select: [item: SolutionFeedItem];
}>();

const { t } = useI18n();

const authorInitial = computed(
  () => props.item.author.name.charAt(0)?.toUpperCase() ?? "?",
);

const languageLabel = computed(() => {
  const lang = props.item.language || props.item.languageFilter;
  if (!lang) return t("problem.submissions.language");
  return lang.charAt(0).toUpperCase() + lang.slice(1).toLowerCase();
});

const topicLabel = computed(
  () =>
    props.item.topicName ||
    props.item.topicTranslated ||
    props.item.topic ||
    t("forum.post.flair"),
);

const formattedDate = computed(() => formatRelativeTime(props.item.created_at));

const handleSelect = () => emit("select", props.item);

const userVote = computed(() => resolveUserVote(props.item.userVote));
const voteCounts = computed(() =>
  resolveVoteCounts(props.item.likes, props.item.dislikes, props.item.stats),
);
</script>

<template>
  <article
    class="group flex cursor-pointer flex-col gap-2 bg-card/70 p-3"
    tabindex="0"
    role="button"
    @click="handleSelect"
    @keyup.enter.prevent="handleSelect"
  >
    <header class="flex items-start gap-2">
      <img
        v-if="props.item.author.avatar"
        :src="props.item.author.avatar"
        class="h-9 w-9 rounded-full border border-border object-cover"
        alt="Avatar"
      />
      <div
        v-else
        class="flex h-9 w-9 items-center justify-center rounded-full text-[11px] font-semibold text-white"
        :style="{ backgroundColor: props.item.author.avatarColor }"
      >
        {{ authorInitial }}
      </div>
      <div class="flex flex-1 flex-col gap-1 text-[12px] leading-tight">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-semibold text-foreground">{{
            props.item.author.name
          }}</span>
          <span class="truncate text-muted-foreground">{{
            props.item.author.role
          }}</span>
          <span class="text-xs text-muted-foreground"
            >· {{ formattedDate }}</span
          >
          <span
            v-if="props.item.flair"
            class="rounded-full bg-[var(--terminal-amber)]/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-[var(--terminal-amber)]"
          >
            {{ props.item.flair }}
          </span>
        </div>
        <div
          class="flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground"
        >
          <span class="rounded-full bg-muted/80 px-2 py-0.5 capitalize">{{
            languageLabel
          }}</span>
          <span class="rounded-full bg-muted/80 px-2 py-0.5 capitalize">{{
            topicLabel
          }}</span>
          <span
            v-for="badge in props.item.badges"
            :key="badge"
            class="rounded-full border border-border px-2 py-0.5 font-medium"
          >
            {{ badge }}
          </span>
        </div>
      </div>
    </header>

    <section class="space-y-1">
      <div class="space-y-1">
        <p
          v-if="props.item.highlight"
          class="text-[10px] font-medium uppercase tracking-wide text-muted-foreground"
        >
          {{ props.item.highlight }}
        </p>
        <h3 class="text-sm font-semibold text-foreground">
          {{ props.item.title }}
        </h3>
      </div>
      <p class="line-clamp-2 text-xs text-muted-foreground">
        {{ props.item.summary }}
      </p>
      <!-- Tags -->
      <div
        v-if="props.item.tags && props.item.tags.length"
        class="flex flex-wrap gap-1"
      >
        <span
          v-for="tag in props.item.tags.slice(0, 3)"
          :key="tag"
          class="rounded-full bg-muted/80 px-2 py-0.5 text-[10px] capitalize text-muted-foreground"
        >
          {{ tag }}
        </span>
        <span
          v-if="props.item.tags.length > 3"
          class="text-[10px] text-muted-foreground"
        >
          +{{ props.item.tags.length - 3 }}
        </span>
      </div>
    </section>

    <footer class="mt-2">
      <PostActions
        :vote="{
          likes: voteCounts.likes,
          dislikes: voteCounts.dislikes,
          userVote: userVote,
        }"
        :config="{
          views: { show: true, count: props.item.stats?.views ?? 0 },
          comments: {
            show: true,
            count: props.item.stats?.comments ?? 0,
            variant: 'simple',
            icon: 'message-circle',
          },
          share: { show: false },
          save: { show: false },
        }"
        class="text-[10px]"
      />
    </footer>
  </article>
</template>
