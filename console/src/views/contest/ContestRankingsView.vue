<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from "vue";
import { useRoute } from "vue-router";
import { Trophy, Globe, MapPin } from "lucide-vue-next";
import { useContestStore } from "@/stores/contest";
import { useAuthStore } from "@/stores/auth";
import { storeToRefs } from "pinia";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import GlobalRanking from "./components/GlobalRanking.vue";
import { useI18n } from "vue-i18n";
import { useContestSocket } from "@/composables/contest/useContestSocket";

const contestStore = useContestStore();
const authStore = useAuthStore();
const { t } = useI18n();

// R6.4 / F-04 + F-18: live ranking via WebSocket. useContestSocket exposes
// its own disconnect(); we leaveContest() on unmount to drop the
// subscription so the server-side reference count decrements.
const { isConnected, joinContest, leaveContest, onRankingUpdate } =
  useContestSocket({ autoConnect: true });
const liveRankings = ref<unknown[] | null>(null);
const unsubscribeRanking = onRankingUpdate((data) => {
  if (Array.isArray(data)) liveRankings.value = data;
  else if (data && typeof data === "object" && Array.isArray((data as any).items)) {
    liveRankings.value = (data as any).items;
  }
});

const route = useRoute();
const contestId = computed(() => String(route.params.id ?? ""));

const { globalRankings, loadingRankings } = storeToRefs(contestStore);

const scope = ref<"global" | "local">("global");
const initialLoading = ref(true);

const userCountry = computed(() => {
  const user = authStore.user as Record<string, unknown> | null;
  if (user && typeof user.country === "string") {
    return user.country;
  }
  return undefined;
});

const hasUserCountry = computed(() => !!userCountry.value);

async function loadRankings() {
  try {
    if (scope.value === "local" && hasUserCountry.value) {
      await contestStore.loadGlobalRankings({
        page: 1,
        limit: 10,
        country: userCountry.value,
      });
    } else {
      await contestStore.loadGlobalRankings({ page: 1, limit: 10 });
    }
  } catch (error) {
    console.error("Failed to load rankings:", error);
  } finally {
    initialLoading.value = false;
  }
}

// R6.4 / F-04: join the contest room on mount so the server pushes
// ranking updates. R6.4 / F-18: leave on unmount so the server-side
// subscription count decrements and the WS connection isn't held longer
// than the user is on the page.
onMounted(async () => {
  await loadRankings();
  if (contestId.value) {
    try {
      await joinContest(contestId.value);
    } catch (e) {
      // joinContest may reject if the user is not registered; the
      // server-side ContestSubscribeAuthInterceptor (F-17) handles this.
    }
  }
});

onUnmounted(() => {
  unsubscribeRanking();
  void leaveContest();
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
          <Trophy class="h-8 w-8 text-[var(--terminal-amber)]" />
          <h1
            class="text-3xl font-bold tracking-tight text-[var(--terminal-amber)]"
          >
            {{ t("contest.rankings.title") }}
          </h1>
        </div>
        <p class="text-muted-foreground">
          {{ t("contest.rankings.subtitle") }}
        </p>
      </div>
    </div>

    <Separator />

    <div class="space-y-6">
      <!-- Scope Toggle -->
      <div
        class="inline-flex bg-muted/50 p-1 h-12 rounded-none w-full sm:w-auto"
      >
        <Button
          variant="ghost"
          class="rounded-none px-6 font-bold gap-2"
          :class="{
            'bg-background shadow-[var(--shadow-float)]': scope === 'global',
          }"
          @click="
            scope = 'global';
            loadRankings();
          "
        >
          <Globe class="h-4 w-4" />
          <span>{{ t("contest.rankings.global") }}</span>
        </Button>
        <Button
          variant="ghost"
          class="rounded-none px-6 font-bold gap-2"
          :class="{
            'bg-background shadow-[var(--shadow-float)]': scope === 'local',
          }"
          :disabled="!hasUserCountry"
          @click="
            scope = 'local';
            loadRankings();
          "
        >
          <MapPin class="h-4 w-4" />
          <span>{{ t("contest.rankings.local") }}</span>
        </Button>
      </div>

      <!-- Loading State -->
      <div
        v-if="initialLoading || loadingRankings"
        class="flex flex-col items-center justify-center py-20"
      >
        <div
          class="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"
        ></div>
        <p class="mt-4 text-sm text-muted-foreground">
          {{ t("contest.list.loading") }}
        </p>
      </div>

      <!-- Rankings Content -->
      <div v-else class="grid gap-8 lg:grid-cols-12">
        <div class="lg:col-span-8 lg:col-start-3">
          <GlobalRanking :rankings="globalRankings" />
        </div>
      </div>
    </div>
  </div>
</template>
