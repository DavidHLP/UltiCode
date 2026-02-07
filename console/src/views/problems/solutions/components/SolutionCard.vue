<script setup lang="ts">
import { computed } from "vue";
import type { SolutionFeedItem } from "@/types/solution";
import { PostActions } from "@/components/edge-operations";
import { resolveUserVote, resolveVoteCounts } from "@/utils/vote";
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

const languageLabel = computed(
  () =>
    props.item.language ||
    props.item.languageFilter ||
    t("problem.submissions.language"),
);

const topicLabel = computed(
  () =>
    props.item.topicName ||
    props.item.topicTranslated ||
    props.item.topic ||
    t("forum.post.flair"),
);

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
            >· {{ props.item.created_at }}</span
          >
          <span
            v-if="props.item.flair"
            class="rounded-full bg-orange-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-orange-700"
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
    </section>

    <footer class="mt-2">
      <PostActions
        :vote="{
          likes: voteCounts.likes,
          dislikes: voteCounts.dislikes,
          userVote: userVote,
        }"
        :config="{
          views: { show: true, count: props.item.stats.views },
          comments: {
            show: true,
            count: props.item.stats.comments,
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
