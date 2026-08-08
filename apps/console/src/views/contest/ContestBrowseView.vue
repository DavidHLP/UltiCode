<script setup lang="ts">
/**
 * ContestBrowseView - Contest browse page with tabbed navigation
 *
 * Features:
 * - Tabbed navigation for ongoing, upcoming, and finished contests
 * - Supports initialTab prop for direct tab selection
 * - Uses contestStore for state management
 * - Integrates ContestCard components
 * - Shows loading skeletons during data fetch
 * - Shows empty state when no contests available
 */
import { ref, computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useI18n } from "vue-i18n";
import { Trophy, Calendar, CheckCircle, PlayCircle } from "lucide-vue-next";
import { useContestBrowseStore } from "@/stores/contestBrowse";
import { Card, CardContent } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import ContestCard from "./components/ContestCard.vue";
import type { ContestListItem } from "@/types/contest";

const props = defineProps<{
  initialTab?: string;
}>();

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const contestStore = useContestBrowseStore();

// Store state
const {
  upcomingContests,
  runningContests,
  pastContests,
  pastContestsTotal,
  loadingContests,
} = storeToRefs(contestStore);

// Local state
const activeTab = ref<string>("ongoing");
const currentPage = ref(1);
const pageSize = 10;
const initialLoading = ref(true);

// Computed properties
const isLoading = computed(() => initialLoading.value || loadingContests.value);

const totalPages = computed(() =>
  Math.ceil(pastContestsTotal.value / pageSize),
);

// Get contests based on active tab
const currentContests = computed<ContestListItem[]>(() => {
  switch (activeTab.value) {
    case "ongoing":
      return runningContests.value;
    case "upcoming":
      return upcomingContests.value;
    case "finished":
      return pastContests.value;
    default:
      return runningContests.value;
  }
});

const hasContests = computed(() => currentContests.value.length > 0);

// Watch for tab changes to update URL
watch(activeTab, (newTab) => {
  router.replace({ query: { ...route.query, tab: newTab } });
});

// Load data
async function loadData() {
  try {
    // Resolve initial tab: prop > query > default
    const allowedTabs = ["ongoing", "upcoming", "finished"];
    const resolvedTab =
      props.initialTab && allowedTabs.includes(props.initialTab)
        ? props.initialTab
        : allowedTabs.includes(route.query.tab as string)
          ? (route.query.tab as string)
          : "ongoing";
    activeTab.value = resolvedTab;

    const page = Number(route.query.page) || 1;
    currentPage.value = page;

    await Promise.all([
      contestStore.loadContests(),
      contestStore.loadPastContests(page, pageSize),
    ]);
  } catch {
    // Error handled by UI state
  } finally {
    initialLoading.value = false;
  }
}

// Watch for pagination changes
watch(currentPage, async (newPage) => {
  try {
    await contestStore.loadPastContests(newPage, pageSize);
    router.replace({ query: { ...route.query, page: newPage } });
  } catch {
    // Error handled by UI state
  }
});

// Initialize on mount
onMounted(loadData);
</script>

<template>
  <div
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <!-- Page Header -->
    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <Trophy class="h-8 w-8 text-[var(--terminal-amber)]" />
          <h1
            class="text-3xl font-bold tracking-tight text-[var(--terminal-amber)]"
          >
            {{ t("contest.list.title") }}
          </h1>
        </div>
        <p class="text-muted-foreground">
          {{ t("contest.list.subtitle") }}
        </p>
      </div>
    </div>

    <Separator />

    <!-- Loading State -->
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

    <!-- Main Content -->
    <div v-else class="space-y-6">
      <!-- Tabbed Navigation -->
      <Tabs v-model="activeTab" class="w-full">
        <TabsList
          class="bg-muted/50 p-1 h-12 rounded-none w-full sm:w-auto grid grid-cols-3"
        >
          <TabsTrigger
            value="ongoing"
            class="rounded-none px-6 font-bold data-[state=active]:bg-background data-[state=active]:shadow-[var(--shadow-float)] gap-2"
          >
            <PlayCircle class="h-4 w-4" />
            <span class="hidden sm:inline">{{
              t("contest.list.running")
            }}</span>
            <span class="sm:hidden">{{ t("contest.list.running") }}</span>
            <span
              v-if="runningContests.length > 0"
              class="ml-1 flex h-5 w-5 items-center justify-center rounded-full bg-[var(--terminal-red)] text-2xs font-bold text-white"
            >
              {{ runningContests.length }}
            </span>
          </TabsTrigger>
          <TabsTrigger
            value="upcoming"
            class="rounded-none px-6 font-bold data-[state=active]:bg-background data-[state=active]:shadow-[var(--shadow-float)] gap-2"
          >
            <Calendar class="h-4 w-4" />
            <span class="hidden sm:inline">{{
              t("contest.list.upcoming")
            }}</span>
            <span class="sm:hidden">{{ t("contest.list.upcoming") }}</span>
          </TabsTrigger>
          <TabsTrigger
            value="finished"
            class="rounded-none px-6 font-bold data-[state=active]:bg-background data-[state=active]:shadow-[var(--shadow-float)] gap-2"
          >
            <CheckCircle class="h-4 w-4" />
            <span class="hidden sm:inline">{{
              t("contest.list.finished")
            }}</span>
            <span class="sm:hidden">{{ t("contest.list.finished") }}</span>
          </TabsTrigger>
        </TabsList>

        <!-- Ongoing Contests Tab -->
        <TabsContent value="ongoing" class="mt-6 space-y-6">
          <!-- Loading Skeletons -->
          <div
            v-if="loadingContests"
            class="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          >
            <Card v-for="i in 3" :key="i">
              <CardContent class="p-5 space-y-4">
                <Skeleton class="h-4 w-20" />
                <Skeleton class="h-6 w-3/4" />
                <div class="space-y-2">
                  <Skeleton class="h-4 w-full" />
                  <Skeleton class="h-4 w-2/3" />
                </div>
                <Skeleton class="h-10 w-full" />
              </CardContent>
            </Card>
          </div>

          <!-- Contest Cards Grid -->
          <div
            v-else-if="hasContests"
            class="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          >
            <ContestCard
              v-for="contest in currentContests"
              :key="contest.id"
              :contest="contest"
              variant="default"
            />
          </div>

          <!-- Empty State -->
          <div
            v-else
            class="flex flex-col items-center justify-center py-16 border-2 border-dashed rounded-none bg-muted/5 text-center px-6"
          >
            <PlayCircle class="h-12 w-12 text-muted-foreground/30 mb-4" />
            <h3 class="text-lg font-bold">
              {{ t("contest.list.noContests") }}
            </h3>
            <p class="text-sm text-muted-foreground mt-1 max-w-[300px]">
              {{ t("contest.list.noContestsHint") }}
            </p>
          </div>
        </TabsContent>

        <!-- Upcoming Contests Tab -->
        <TabsContent value="upcoming" class="mt-6 space-y-6">
          <!-- Loading Skeletons -->
          <div
            v-if="loadingContests"
            class="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          >
            <Card v-for="i in 3" :key="i">
              <CardContent class="p-5 space-y-4">
                <Skeleton class="h-4 w-20" />
                <Skeleton class="h-6 w-3/4" />
                <div class="space-y-2">
                  <Skeleton class="h-4 w-full" />
                  <Skeleton class="h-4 w-2/3" />
                </div>
                <Skeleton class="h-10 w-full" />
              </CardContent>
            </Card>
          </div>

          <!-- Contest Cards Grid -->
          <div
            v-else-if="hasContests"
            class="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          >
            <ContestCard
              v-for="contest in currentContests"
              :key="contest.id"
              :contest="contest"
              variant="default"
            />
          </div>

          <!-- Empty State -->
          <div
            v-else
            class="flex flex-col items-center justify-center py-16 border-2 border-dashed rounded-none bg-muted/5 text-center px-6"
          >
            <Calendar class="h-12 w-12 text-muted-foreground/30 mb-4" />
            <h3 class="text-lg font-bold">
              {{ t("contest.list.noContests") }}
            </h3>
            <p class="text-sm text-muted-foreground mt-1 max-w-[300px]">
              {{ t("contest.list.noUpcomingHint") }}
            </p>
          </div>
        </TabsContent>

        <!-- Finished Contests Tab -->
        <TabsContent value="finished" class="mt-6 space-y-6">
          <!-- Loading Skeletons -->
          <div
            v-if="loadingContests"
            class="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          >
            <Card v-for="i in 3" :key="i">
              <CardContent class="p-5 space-y-4">
                <Skeleton class="h-4 w-20" />
                <Skeleton class="h-6 w-3/4" />
                <div class="space-y-2">
                  <Skeleton class="h-4 w-full" />
                  <Skeleton class="h-4 w-2/3" />
                </div>
                <Skeleton class="h-10 w-full" />
              </CardContent>
            </Card>
          </div>

          <!-- Contest Cards Grid -->
          <div
            v-else-if="hasContests"
            class="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          >
            <ContestCard
              v-for="contest in currentContests"
              :key="contest.id"
              :contest="contest"
              variant="default"
            />
          </div>

          <!-- Empty State -->
          <div
            v-else
            class="flex flex-col items-center justify-center py-16 border-2 border-dashed rounded-none bg-muted/5 text-center px-6"
          >
            <CheckCircle class="h-12 w-12 text-muted-foreground/30 mb-4" />
            <h3 class="text-lg font-bold">
              {{ t("contest.list.noContests") }}
            </h3>
            <p class="text-sm text-muted-foreground mt-1 max-w-[300px]">
              {{ t("contest.list.noFinishedHint") }}
            </p>
          </div>

          <!-- Pagination for finished contests -->
          <div
            v-if="hasContests && activeTab === 'finished' && totalPages > 1"
            class="flex items-center justify-center gap-2 pt-4"
          >
            <Button
              variant="outline"
              size="icon"
              class="h-9 w-9"
              :disabled="currentPage === 1 || loadingContests"
              @click="currentPage--"
            >
              <span class="sr-only">Previous page</span>
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="m15 18-6-6 6-6" />
              </svg>
            </Button>
            <span class="text-sm text-muted-foreground">
              {{ currentPage }} / {{ totalPages }}
            </span>
            <Button
              variant="outline"
              size="icon"
              class="h-9 w-9"
              :disabled="currentPage === totalPages || loadingContests"
              @click="currentPage++"
            >
              <span class="sr-only">Next page</span>
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="m9 18 6-6-6-6" />
              </svg>
            </Button>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  </div>
</template>
