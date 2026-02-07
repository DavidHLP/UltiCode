<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useContestStore } from "@/stores/contest";
import { useRouter } from "vue-router";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Calendar, Trophy, TrendingUp, TrendingDown } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

const contestStore = useContestStore();
const router = useRouter();
const loading = ref(true);
const { t, locale } = useI18n();

onMounted(async () => {
  try {
    await Promise.all([
      contestStore.loadUserContests(),
      contestStore.loadContestHistory(),
    ]);
  } catch (error) {
    console.error("Failed to load user contests:", error);
  } finally {
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

function getStatusBadge(status: string) {
  const variants: Record<string, string> = {
    upcoming: "default",
    running: "destructive",
    finished: "secondary",
  };
  return variants[status] || "secondary";
}

function navigateToContest(contestId: string) {
  router.push({ name: "contest-detail", params: { contestId } });
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

    <Tabs v-else default-value="registered" class="w-full">
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
                      {{ formatDate(contest.start_time) }}
                    </span>
                    <span
                      >{{ contest.duration_minutes }}
                      {{ t("contest.time.min_short") }}</span
                    >
                  </div>
                </div>
                <Badge :variant="getStatusBadge(contest.status) as any">
                  {{ contest.status }}
                </Badge>
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
                class="flex items-center justify-between rounded-lg border p-4 hover:bg-muted/50"
              >
                <div class="flex-1 space-y-1">
                  <h3 class="font-semibold">{{ history.contestTitle }}</h3>
                  <div
                    class="flex items-center gap-3 text-sm text-muted-foreground"
                  >
                    <span class="flex items-center gap-1">
                      <Calendar class="h-3 w-3" />
                      {{ formatDate(history.contestDate) }}
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
                  <Badge v-if="history.isVirtual" variant="outline">{{
                    t("contest.types.virtual")
                  }}</Badge>
                  <div
                    class="flex items-center gap-1 rounded-md px-2 py-1 font-semibold"
                    :class="{
                      'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400':
                        history.ratingChange > 0,
                      'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400':
                        history.ratingChange < 0,
                      'text-muted-foreground': history.ratingChange === 0,
                    }"
                  >
                    <TrendingUp
                      v-if="history.ratingChange > 0"
                      class="h-4 w-4"
                    />
                    <TrendingDown
                      v-else-if="history.ratingChange < 0"
                      class="h-4 w-4"
                    />
                    {{ history.ratingChange > 0 ? "+" : ""
                    }}{{ history.ratingChange }}
                  </div>
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
                      {{ formatDate(contest.start_time) }}
                    </span>
                    <span
                      >{{ contest.duration_minutes }}
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
