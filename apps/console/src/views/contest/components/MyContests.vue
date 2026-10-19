<script setup lang="ts">
import { ref, onMounted, watch } from "vue";
import { createValueRequest } from "@ulticode/request-state";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import { useContestRankingStore } from "@/stores/contestRanking";
import { useRouter } from "vue-router";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { Badge } from "@ulticode/design-system/ui/badge";
import ContestStatusBadge from "./ContestStatusBadge.vue";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@ulticode/design-system/ui/tabs";
import { Calendar, Trophy } from "@lucide/vue";
import { useI18n } from "vue-i18n";

const contestStore = useContestRankingStore();
const router = useRouter();
const tabRequest = createValueRequest({
  errorMessage: "Failed to load contests",
  rethrow: true,
});
const loading = tabRequest.loading;
const error = tabRequest.error;
const activeTab = ref("registered");
const { t, locale } = useI18n();

async function loadDataForTab(tab: string) {
  return tabRequest.run(async () => {
    switch (tab) {
      case "registered":
        if (contestStore.registeredContests.length === 0) {
          await contestStore.loadUserContests("registered");
        }
        break;
      case "participated":
        if (contestStore.contestHistory.length === 0) {
          await contestStore.loadContestHistory();
        }
        break;
      case "virtual":
        if (contestStore.virtualContests.length === 0) {
          await contestStore.loadUserContests("virtual");
        }
        break;
    }
    return true;
  });
}

async function loadActiveTab() {
  try {
    await loadDataForTab(activeTab.value);
  } catch {
    // Error handled by UI state.
  }
}

onMounted(loadActiveTab);
watch(activeTab, loadActiveTab);

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
    <Tabs v-model="activeTab" class="w-full" :aria-busy="loading">
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

      <p
        v-if="loading"
        role="status"
        class="py-20 text-center text-muted-foreground"
      >
        {{ t("contest.myContests.loading") }}
      </p>

      <div
        v-else-if="error"
        role="alert"
        class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border p-4"
      >
        <p
          class="min-w-0 flex-1 text-sm text-destructive [overflow-wrap:anywhere]"
        >
          {{ t("contest.myContests.loadError") }}
        </p>
        <RetryButton @retry="!loading && loadActiveTab()" />
      </div>

      <!-- Registered Contests -->
      <TabsContent
        v-if="!loading && !error"
        value="registered"
        class="space-y-4"
      >
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
                class="flex items-center justify-between rounded-lg border p-4 hover:bg-muted/50 cursor-pointer"
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
      <TabsContent
        v-if="!loading && !error"
        value="participated"
        class="space-y-4"
      >
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
                class="flex items-center justify-between rounded-lg border p-4 hover:bg-muted/50"
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
                  <Badge v-if="history.isRated" variant="outline">
                    {{ t("contest.list.rated") }}
                  </Badge>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <!-- Virtual Contests -->
      <TabsContent v-if="!loading && !error" value="virtual" class="space-y-4">
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
                class="flex items-center justify-between rounded-lg border p-4 hover:bg-muted/50 cursor-pointer"
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
