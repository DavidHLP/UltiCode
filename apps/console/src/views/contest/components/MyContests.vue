<script setup lang="ts">
import { ref, onMounted, watch } from "vue";
import { useContestRankingStore } from "@/stores/contestRanking";
import { useRouter } from "vue-router";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import ContestStatusBadge from "./ContestStatusBadge.vue";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Calendar, Trophy } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

const contestStore = useContestRankingStore();
const router = useRouter();
const loading = ref(true);
const activeTab = ref("registered");
const { t, locale } = useI18n();

let requestId = 0;

async function loadDataForTab(tab: string) {
  const currentId = ++requestId;
  switch (tab) {
    case "registered": {
      if (contestStore.registeredContests.length === 0) {
        await contestStore.loadUserContests("registered");
      }
      break;
    }
    case "participated": {
      if (contestStore.contestHistory.length === 0) {
        await contestStore.loadContestHistory();
      }
      break;
    }
    case "virtual": {
      if (contestStore.virtualContests.length === 0) {
        await contestStore.loadUserContests("virtual");
      }
      break;
    }
  }
  // Only update loading if this is still the latest request
  if (currentId === requestId) {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    await loadDataForTab(activeTab.value);
  } catch {
    // Error handled by UI state
    loading.value = false;
  }
});

watch(activeTab, async (newTab) => {
  loading.value = true;
  try {
    await loadDataForTab(newTab);
  } catch {
    // Error handled by UI state
    loading.value = false;
  }
});

function formatDate(isoString: string): string {
  const date = new Date(isoString);
  return date.toLocaleDateString(locale.value, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function navigateToContest(slug: string) {
  router.push({ name: "contest-detail", params: { slug } });
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold">{{ t("contest.myContests.title") }}</h2>
        <p class="text-muted-foreground">
          {{ t("contest.myContests.subtitle") }}
        </p>
      </div>
    </div>

    <div v-if="loading" class="py-20 text-center">
      <p class="text-muted-foreground">
        {{ t("contest.myContests.loading") }}
      </p>
    </div>

    <Tabs v-else v-model="activeTab" class="w-full">
      <TabsList class="grid w-full max-w-md grid-cols-3">
        <TabsTrigger value="registered">{{
          t("contest.myContests.tabs.registered")
        }}</TabsTrigger>
        <TabsTrigger value="participated">{{
          t("contest.myContests.tabs.participated")
        }}</TabsTrigger>
        <TabsTrigger value="virtual">{{
          t("contest.myContests.tabs.virtual")
        }}</TabsTrigger>
      </TabsList>

      <!-- Registered Contests -->
      <TabsContent value="registered" class="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>{{ t("contest.myContests.registeredTitle") }}</CardTitle>
          </CardHeader>
          <CardContent>
            <div
              v-if="contestStore.registeredContests.length === 0"
              class="py-12 text-center text-muted-foreground"
            >
              {{ t("contest.myContests.noRegistered") }}
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="contest in contestStore.registeredContests"
                :key="contest.id"
                class="flex items-center justify-between rounded-none border p-4 hover:bg-muted/50 cursor-pointer"
                @click="navigateToContest(contest.id)"
              >
                <div class="space-y-1">
                  <h3 class="font-semibold">{{ contest.title }}</h3>
                  <div
                    class="flex items-center gap-3 text-sm text-muted-foreground"
                  >
                    <span class="flex items-center gap-1">
                      <Calendar class="h-3 w-3" />
                      {{ formatDate(contest.startTime) }}
                    </span>
                    <span
                      >{{ contest.duration }}
                      {{ t("contest.time.min_short") }}</span
                    >
                  </div>
                </div>
                <ContestStatusBadge :status="contest.status" size="sm" />
              </div>
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <!-- Participated Contests -->
      <TabsContent value="participated" class="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>{{ t("contest.myContests.historyTitle") }}</CardTitle>
          </CardHeader>
          <CardContent>
            <div
              v-if="contestStore.contestHistory.length === 0"
              class="py-12 text-center text-muted-foreground"
            >
              {{ t("contest.myContests.noParticipated") }}
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="history in contestStore.contestHistory"
                :key="history.contestId"
                class="flex items-center justify-between rounded-none border p-4 hover:bg-muted/50"
              >
                <div class="flex-1 space-y-1">
                  <h3 class="font-semibold">{{ history.title }}</h3>
                  <div
                    class="flex items-center gap-3 text-sm text-muted-foreground"
                  >
                    <span class="flex items-center gap-1">
                      <Calendar class="h-3 w-3" />
                      {{ formatDate(history.startTime || "") }}
                    </span>
                    <span class="flex items-center gap-1">
                      <Trophy class="h-3 w-3" />
                      {{
                        t("contest.myContests.rank", {
                          rank: history.rank,
                          total: history.totalParticipants,
                        })
                      }}
                    </span>
                    <span>{{
                      t("contest.myContests.score", { score: history.score })
                    }}</span>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <Badge
                    v-if="history.isRated"
                    variant="outline"
                    class="font-data text-2xs"
                  >
                    {{ t("contest.list.rated") }}
                  </Badge>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <!-- Virtual Contests -->
      <TabsContent value="virtual" class="space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>{{ t("contest.myContests.virtualTitle") }}</CardTitle>
          </CardHeader>
          <CardContent>
            <div
              v-if="contestStore.virtualContests.length === 0"
              class="py-12 text-center text-muted-foreground"
            >
              {{ t("contest.myContests.noVirtual") }}
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="contest in contestStore.virtualContests"
                :key="contest.id"
                class="flex items-center justify-between rounded-none border p-4 hover:bg-muted/50 cursor-pointer"
                @click="navigateToContest(contest.id)"
              >
                <div class="space-y-1">
                  <h3 class="font-semibold">{{ contest.title }}</h3>
                  <div
                    class="flex items-center gap-3 text-sm text-muted-foreground"
                  >
                    <span class="flex items-center gap-1">
                      <Calendar class="h-3 w-3" />
                      {{ formatDate(contest.startTime) }}
                    </span>
                    <span
                      >{{ contest.duration }}
                      {{ t("contest.time.min_short") }}</span
                    >
                  </div>
                </div>
                <Badge variant="outline">{{
                  t("contest.types.virtual")
                }}</Badge>
              </div>
            </div>
          </CardContent>
        </Card>
      </TabsContent>
    </Tabs>
  </div>
</template>
