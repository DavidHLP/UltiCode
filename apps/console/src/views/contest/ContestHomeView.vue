<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { Trophy } from "@lucide/vue";
import { useContestBrowseStore } from "@/stores/contestBrowse";
import { useContestRankingStore } from "@/stores/contestRanking";
import { storeToRefs } from "pinia";
import { Separator } from "@ulticode/design-system/ui/separator";
import UpcomingContests from "./components/UpcomingContests.vue";
import RunningContests from "./components/RunningContests.vue";
import GlobalRanking from "./components/GlobalRanking.vue";
import PastContests from "./components/PastContests.vue";
import { usePastContestsPager } from "@/composables/contest/usePastContestsPager";
import { useI18n } from "vue-i18n";

const contestStore = useContestBrowseStore();
const rankingStore = useContestRankingStore();
const pastPager = usePastContestsPager();
const { t } = useI18n();

// Use store state
const { upcomingContests, runningContests, pastContests } =
  storeToRefs(contestStore);
const {
  globalRankings,
  loadingRankings,
  loadingMoreRankings,
  loadMoreRankingsError,
  hasMoreRankings,
  error: rankingError,
} = storeToRefs(rankingStore);
const {
  page: currentPage,
  totalPages,
  loading: pastLoading,
  error: pastError,
} = pastPager;

const initialLoading = ref(true);

const isLoading = computed(() => initialLoading.value || loadingRankings.value);

function retryRankings(): void {
  void rankingStore.loadGlobalRankings().catch(() => {
    // The ranking store owns and exposes the request error state.
  });
}

function retryPastPage(): void {
  void pastPager.loadPage().catch(() => {
    // The pager owns and exposes the transition error state.
  });
}

// Load data
onMounted(async () => {
  try {
    await Promise.all([
      contestStore.loadContests(),
      pastPager.loadInitialPage(),
      rankingStore.loadGlobalRankings(),
    ]);
  } catch (error) {
    console.error("Failed to load contest data:", error);
  } finally {
    initialLoading.value = false;
  }
});
function loadMoreRankings(): void {
  void rankingStore.loadMoreGlobalRankings().catch(() => {
    // Preserve loaded rows; the store exposes the pagination error for retry.
  });
}
</script>

<template>
  <div
    class="@container/contest-home max-w-7xl mx-auto w-full min-w-0 space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <Trophy class="h-8 w-8 text-status-warning-mark" />
          <h1 class="text-3xl font-bold tracking-tight text-foreground">
            {{ t("contest.list.mainTitle") }}
          </h1>
        </div>
        <p class="text-muted-foreground">
          {{ t("contest.list.subtitle") }}
        </p>
      </div>
    </div>

    <Separator />

    <div
      v-if="isLoading"
      class="flex flex-col items-center justify-center py-20"
    >
      <div
        class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
      ></div>
      <p class="mt-4 text-sm text-muted-foreground">
        {{ t("contest.list.loading") }}
      </p>
    </div>

    <div v-else class="space-y-8">
      <RunningContests :contests="runningContests" />
      <UpcomingContests :contests="upcomingContests" />
      <div
        class="grid min-w-0 gap-8 @7xl/contest-home:grid-cols-[minmax(27rem,1fr)_minmax(0,2fr)]"
      >
        <div class="min-w-0">
          <GlobalRanking
            :rankings="globalRankings"
            :has-more="hasMoreRankings"
            :loading-more="loadingMoreRankings"
            :load-more-error="Boolean(loadMoreRankingsError)"
            @load-more="loadMoreRankings"
            :error="Boolean(rankingError)"
            @retry="retryRankings"
          />
        </div>
        <div class="min-w-0">
          <PastContests
            :contests="pastContests"
            :loading="pastLoading"
            :error="pastError"
            v-model:currentPage="currentPage"
            :totalPages="totalPages"
            @retry="retryPastPage"
          />
        </div>
      </div>
    </div>
  </div>
</template>
