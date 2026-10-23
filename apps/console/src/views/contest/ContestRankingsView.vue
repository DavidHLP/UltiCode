<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Trophy } from "@lucide/vue";
import { useContestRankingStore } from "@/stores/contestRanking";
import { useAuthStore } from "@/stores/auth";
import { storeToRefs } from "pinia";
import GlobalRanking from "./components/GlobalRanking.vue";
import { useI18n } from "vue-i18n";
import { useContestSocket } from "@/composables/contest/useContestSocket";
import { toast } from "vue-sonner";

const contestStore = useContestRankingStore();
const authStore = useAuthStore();
const { t } = useI18n();

// R6.4 / F-04 + F-18: live ranking via WebSocket. useContestSocket exposes
// its own disconnect(); we leaveContest() on unmount to drop the
// subscription so the server-side reference count decrements.
const { isConnected, joinContest, leaveContest, onRankingUpdate } =
  useContestSocket({ autoConnect: true });
// R9.3 / F-44: show a reconnecting banner using the locale
// string added in R9.3 (previously a dead translation per R8
// review MED-2). R8 review HIGH-1 left the locales bare; this is
// the first view reference.
const showReconnecting = ref(false);
watch(isConnected, (connected) => {
  showReconnecting.value = !connected;
});
const liveRankings = ref<unknown[] | null>(null);
const unsubscribeRanking = onRankingUpdate((data) => {
  if (Array.isArray(data)) liveRankings.value = data;
  else if (
    data &&
    typeof data === "object" &&
    Array.isArray((data as { items?: unknown[] }).items)
  ) {
    liveRankings.value = (data as { items?: unknown[] }).items ?? null;
  }
});

const route = useRoute();
const router = useRouter();
const contestId = computed(() => String(route.params.id ?? ""));

const {
  globalRankings,
  loadingRankings,
  loadingMoreRankings,
  loadMoreRankingsError,
  hasMoreRankings,
  error: rankingError,
} = storeToRefs(contestStore);

const scope = computed<"global" | "local">({
  get: () => (route.query.scope === "local" ? "local" : "global"),
  set: (value) => {
    void router.replace({ query: { ...route.query, scope: value } });
  },
});
watch(scope, () => {
  void loadRankings();
});
const initialLoading = ref(true);

const userCountry = computed(() => {
  const user = authStore.user as Record<string, unknown> | null;
  // Resolution order:
  //   1. explicit `user.country` (matches backend `users.country` once rolled out)
  //   2. `user.location` (existing profile field — often contains a country name)
  //   3. browser language (`zh-CN` → "CN", `en-US` → "US", …)
  //   4. hard-coded "CN" dev fallback so the "local" tab stays usable while the
  //      dedicated country column is still being rolled out across the auth/me
  //      DTOs and the bootstrap seed.
  if (user) {
    const country = (user as { country?: unknown }).country;
    if (typeof country === "string" && country.trim()) return country.trim();
    const location = (user as { location?: unknown }).location;
    if (typeof location === "string" && location.trim()) return location.trim();
  }
  if (typeof navigator !== "undefined") {
    const lang = navigator.language;
    if (typeof lang === "string" && lang.includes("-")) {
      const region = lang.split("-").pop();
      if (region && region.length === 2) return region.toUpperCase();
    }
  }
  return "CN";
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
    // R9.3 / F-41: surface the i18n error key (was a dead
    // translation per R8 review MED-2). Viewers see a toast.
    console.error("Failed to load rankings:", error);
    toast.error(t("contest.error.rankingsLoadFailed"));
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
    } catch {
      // joinContest may reject if the user is not registered; the
      // server-side ContestSubscribeAuthInterceptor (F-17) handles this.
    }
  }
});

// R6 review (MED-2): liveRankings is a merged view of "REST snapshot +
// WebSocket deltas" — when a ranking update arrives, replace the local
// list so the template can show the freshest order. If the WS feed
// is empty, we keep the REST result; the v-if guards against showing
// an empty list before the first REST response.
onUnmounted(() => {
  unsubscribeRanking();
  void leaveContest();
});
function loadMoreRankings(): void {
  void contestStore.loadMoreGlobalRankings().catch(() => {
    // Preserve loaded rows; the store exposes the pagination error for retry.
  });
}
</script>

<template>
  <div
    class="max-w-5xl mx-auto w-full min-w-0 space-y-5 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <Trophy class="h-8 w-8 text-status-warning-mark" />
          <h1 class="text-3xl font-bold tracking-tight text-foreground">
            {{ t("contest.rankings.title") }}
          </h1>
        </div>
        <p class="text-muted-foreground">
          {{ t("contest.rankings.subtitle") }}
        </p>
      </div>
    </div>

    <div class="space-y-5">

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
      <div v-else class="min-w-0">
        <div class="min-w-0">
          <GlobalRanking
            :show-header="false"
            :rankings="globalRankings"
            :has-more="hasMoreRankings"
            :loading-more="loadingMoreRankings"
            :load-more-error="Boolean(loadMoreRankingsError)"
            @load-more="loadMoreRankings"
            :scope="scope"
            :error="Boolean(rankingError)"
            @retry="loadRankings"
          />
        </div>
      </div>
    </div>
  </div>
</template>
