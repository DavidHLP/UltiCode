<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from "vue";
import { useRoute } from "vue-router";
import { Trophy, Globe, MapPin } from "lucide-vue-next";
import { useContestRankingStore } from "@/stores/contestRanking";
import { useAuthStore } from "@/stores/auth";
import { storeToRefs } from "pinia";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
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
  else if (data && typeof data === "object" && Array.isArray((data as { items?: unknown[] }).items)) {
    liveRankings.value = (data as { items?: unknown[] }).items ?? null;
  }
});

const route = useRoute();
const contestId = computed(() => String(route.params.id ?? ""));

const { globalRankings, loadingRankings } = storeToRefs(contestStore);

const scope = ref<"global" | "local">("global");
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
