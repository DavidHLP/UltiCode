<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { computed, ref, watch } from "vue";
import type { SolutionFeedItem } from "@/types/solution";
import SolutionListView from "./SolutionListView.vue";
import SolutionDetail from "./components/SolutionDetail.vue";
import { fetchSolution, fetchSolutionFeed } from "@/api/solution";
import { problemHooks } from "@/hooks/problem-hooks";
import { useI18n } from "vue-i18n";
import { useErrorHandler } from "@/composables/useErrorHandler";

const props = defineProps<{
  problemId: number;
  followUp: string;
}>();

const emit = defineEmits<{
  select: [item: SolutionFeedItem];
}>();

const { t } = useI18n();
const { handleError } = useErrorHandler();
const selectedSolution = ref<SolutionFeedItem | null>(null);
const isLoading = ref(true);
const isLoadingDetail = ref(false);
const feed = ref<Awaited<ReturnType<typeof fetchSolutionFeed>> | null>(null);

watch(
  () => props.problemId,
  async (id) => {
    if (!id) {
      feed.value = null;
      return;
    }
    isLoading.value = true;
    const userId = useAuthStore().fetchCurrentUserId();
    await problemHooks.emit("problem:solutions:load:before", {
      problemId: id,
      userId,
    });
    try {
      feed.value = await fetchSolutionFeed(id);
      await problemHooks.emit("problem:solutions:load:after", {
        problemId: id,
        userId,
        feed: feed.value,
      });
    } catch (error) {
      handleError(error, {
        fallbackMessage: "problem.solutions.error.loadFailed",
        logToConsole: true,
        resetState: () => {
          feed.value = null;
        },
      });
      await problemHooks.emit("problem:solutions:load:error", {
        problemId: id,
        userId,
        error,
      });
    } finally {
      isLoading.value = false;
    }
  },
  { immediate: true },
);
const fallbackSolution = computed<SolutionFeedItem>(() => ({
  id: "follow-up",
  problem_id: props.problemId.toString(),
  title: t("problem.detail.hints"),
  summary: props.followUp,
  highlight: t("problem.detail.hints"),
  flair: "",
  badges: [],
  authorId: "author-fallback",
  author: {
    id: "author-fallback",
    name: t("personal.profile.displayName"),
    username: "editorial",
    role: "System Note",
    avatarColor: "var(--silver-400)",
  },
  tags: [],
  stats: {
    likes: 0,
    dislikes: 0,
    views: 0,
    comments: 0,
  },
  views: 0,
  votes: 0,
  likes: 0,
  comments: 0,
  created_at: "—",
  publishedAt: new Date().toISOString(),
  topic: {
    id: "general",
    name: t("problem.categories.all"),
  },
  languageFilter: "all",
  language: "markdown",
  score: 0,
  content: props.followUp, // Markdown 内容
}));

const handleSelect = async (item: SolutionFeedItem) => {
  isLoadingDetail.value = true;
  try {
    const detail = await fetchSolution(item.id);
    selectedSolution.value = detail;
    emit("select", detail);
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.solutions.error.loadDetailFailed",
      logToConsole: true,
    });
    // Fallback to list item if detail fetch fails
    selectedSolution.value = item;
    emit("select", item);
  } finally {
    isLoadingDetail.value = false;
  }
};

const resetSelectedSolution = () => {
  selectedSolution.value = null;
};

const handleSolutionDeleted = (id: string) => {
  if (!feed.value) return;
  feed.value = {
    ...feed.value,
    items: feed.value.items.filter((item) => item.id !== id),
    total: Math.max(0, feed.value.total - 1),
  };
  selectedSolution.value = null;
};
</script>

<template>
  <div v-if="feed?.items?.length" class="space-y-4">
    <div v-if="selectedSolution || isLoadingDetail" class="space-y-4 px-5 py-4">
      <button
        type="button"
        class="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground transition hover:text-foreground"
        @click="resetSelectedSolution"
      >
        &larr; {{ t("problem.solutions.returnToList") }}
      </button>
      <div v-if="isLoadingDetail" class="text-sm text-muted-foreground">
        {{ t("problem.solutions.loadingDetail") }}
      </div>
      <SolutionDetail
        v-else-if="selectedSolution"
        :item="selectedSolution"
        @deleted="handleSolutionDeleted"
      />
    </div>
    <SolutionListView
      v-else
      :problem-id="props.problemId"
      :items="feed?.items ?? []"
      :follow-up="props.followUp"
      :sort-options="feed?.sortOptions ?? []"
      @select="handleSelect"
    />
  </div>
  <div v-else>
    <div v-if="isLoading" class="px-5 py-4 text-sm text-muted-foreground">
      {{ t("problem.solutions.loading") }}
    </div>
    <SolutionDetail v-else :item="fallbackSolution" />
  </div>
</template>
