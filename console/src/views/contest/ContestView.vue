<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { Trophy } from "lucide-vue-next";
import { useRouter, useRoute } from "vue-router";
import { useContestStore } from "@/stores/contest";
import { storeToRefs } from "pinia";
import { Separator } from "@/components/ui/separator";
import UpcomingContests from "./components/UpcomingContests.vue";
import RunningContests from "./components/RunningContests.vue";
import GlobalRanking from "./components/GlobalRanking.vue";
import PastContests from "./components/PastContests.vue";
import MyContests from "./components/MyContests.vue";
import { useI18n } from "vue-i18n";

const router = useRouter();
const route = useRoute();
const contestStore = useContestStore();
const { t } = useI18n();

defineProps<{
  tab?: string;
}>();

// Use store state
const {
  upcomingContests,
  runningContests,
  pastContests,
  pastContestsTotal,
  globalRankings,
  loadingContests,
  loadingRankings,
} = storeToRefs(contestStore);

const currentPage = ref(1);
const pageSize = 10;
const initialLoading = ref(true);

const isLoading = computed(() => initialLoading.value || loadingRankings.value);

const totalPages = computed(() =>
  Math.ceil(pastContestsTotal.value / pageSize),
);

// Load data
onMounted(async () => {
  try {
    const page = Number(route.query.page) || 1;
    currentPage.value = page;

    await Promise.all([
      contestStore.loadContests(),
      contestStore.loadPastContests(page, pageSize),
      contestStore.loadGlobalRankings(),
    ]);
  } catch (error) {
    console.error("Failed to load contest data:", error);
  } finally {
    initialLoading.value = false;
  }
});

// Watch pagination changes
watch(currentPage, async (newPage) => {
  try {
    await contestStore.loadPastContests(newPage, pageSize);
    // Update URL Query
    router.replace({ query: { ...route.query, page: newPage } });
  } catch (error) {
    console.error("Failed to load past contests:", error);
  }
});
</script>

<template>
  <div
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <Trophy class="h-8 w-8 text-yellow-500" />
          <h1
            class="text-3xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-yellow-500 to-orange-600"
          >
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
      <!-- Home View -->
      <template v-if="!tab">
        <RunningContests :contests="runningContests" />
        <UpcomingContests :contests="upcomingContests" />
        <div class="grid gap-8 lg:grid-cols-12">
          <div class="lg:col-span-4">
            <GlobalRanking :rankings="globalRankings" />
          </div>
          <div class="lg:col-span-8">
            <PastContests
              :contests="pastContests"
              :loading="loadingContests"
              v-model:currentPage="currentPage"
              :totalPages="totalPages"
            />
          </div>
        </div>
      </template>

      <!-- Past Contests View -->
      <template v-else-if="tab === 'past'">
        <PastContests
          :contests="pastContests"
          :loading="loadingContests"
          v-model:currentPage="currentPage"
          :totalPages="totalPages"
        />
      </template>

      <!-- Rankings View -->
      <template v-else-if="tab === 'ranking'">
        <GlobalRanking :rankings="globalRankings" />
      </template>

      <!-- My Contests View -->
      <template v-else-if="tab === 'my'">
        <MyContests />
      </template>
    </div>
  </div>
</template>
