<script setup lang="ts">
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import { ChevronLeft, ChevronRight } from "@lucide/vue";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@ulticode/design-system/ui/table";
import { formatPenaltyTime } from "@/utils/datetime";
import { useI18n } from "vue-i18n";
import type { ContestRankingEntry, LiveRankingEntry } from "@/types/contest";

defineProps<{
  rankings: (ContestRankingEntry | LiveRankingEntry)[];
  getCountryFlag: (countryCode: string) => string;
  page: number;
  totalPages: number;
  loading: boolean;
  error: boolean;
  paginated: boolean;
}>();

const emit = defineEmits<{
  (event: "pageChange", page: number): void;
  (event: "retry"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <Card id="contest-ranking" class="overflow-hidden">
    <CardHeader class="flex flex-row items-center justify-between pb-3">
      <CardTitle>{{ t("contest.detail.leaderboard") }}</CardTitle>
    </CardHeader>
    <CardContent class="p-0">
      <div
        v-if="error"
        role="alert"
        class="flex flex-wrap items-center gap-3 p-4 text-sm"
      >
        <span>{{ t("contest.error.rankingsLoadFailed") }}</span>
        <Button
          variant="outline"
          size="sm"
          :disabled="loading"
          @click="emit('retry')"
          >{{ t("common.actions.retry") }}</Button
        >
      </div>
      <p
        v-if="!rankings.length && !error"
        role="status"
        class="p-6 text-center text-sm text-muted-foreground"
      >
        {{
          loading ? t("contest.loading.rankings") : t("contest.empty.rankings")
        }}
      </p>
      <Table v-if="rankings.length">
        <TableHeader>
          <TableRow>
            <TableHead class="w-20 pl-6 h-10">{{
              t("contest.detail.rankingHeaders.rank")
            }}</TableHead>
            <TableHead class="h-10">{{
              t("contest.detail.rankingHeaders.user")
            }}</TableHead>
            <TableHead class="w-24 text-center h-10">{{
              t("contest.detail.rankingHeaders.score")
            }}</TableHead>
            <TableHead class="w-32 text-center h-10">{{
              t("contest.detail.rankingHeaders.time")
            }}</TableHead>
            <TableHead class="w-48 h-10">{{
              t("contest.detail.rankingHeaders.problems")
            }}</TableHead>
            <TableHead class="w-32 pr-6 text-right h-10">{{
              t("contest.detail.rankingHeaders.problemsSolved")
            }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="entry in rankings"
            :key="entry.username"
            class="group last:border-b-0 dark:hover:bg-background/30"
          >
            <TableCell class="pl-6 py-3">
              <div
                class="inline-flex h-9 w-9 items-center justify-center rounded-lg font-black text-xs transition-all shadow-sm"
                :class="{
                  'bg-accent text-foreground border border-[var(--rank-first)] scale-110':
                    entry.rank === 1,
                  'bg-accent text-foreground border border-[var(--rank-second)] scale-105':
                    entry.rank === 2,
                  'bg-accent text-foreground border border-[var(--rank-third)]':
                    entry.rank === 3,
                  'bg-accent text-foreground border border-input':
                    (entry.rank ?? 0) > 3,
                }"
              >
                {{ entry.rank ?? 0 }}
              </div>
            </TableCell>
            <TableCell class="py-3">
              <div class="flex items-center gap-3">
                <div class="relative">
                  <img
                    :src="
                      entry.avatar ||
                      'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0iI2U1ZTdlYiI+PGNpcmNsZSBjeD0iMTIiIGN5PSIxMiIgcj0iMTIiLz48L3N2Zz4='
                    "
                    class="h-9 w-9 rounded-lg border border-border bg-muted shadow-sm"
                    alt="Avatar"
                  />
                  <span
                    class="absolute -bottom-1 -right-1 text-base shadow-sm bg-background rounded-lg"
                  >
                    {{ getCountryFlag(entry.country || "CN") }}
                  </span>
                </div>
                <div class="flex flex-col">
                  <span
                    class="font-bold text-sm text-foreground dark:text-foreground"
                    >{{ entry.username }}</span
                  >
                  <span
                    class="text-xs font-bold text-muted-foreground uppercase tracking-widest"
                  >
                    {{ entry.ratingTitle || "NEWBIE" }}
                  </span>
                </div>
              </div>
            </TableCell>
            <TableCell class="text-center py-3">
              <span
                class="text-base font-black tracking-tight font-mono text-foreground"
                >{{ entry.score ?? 0 }}</span
              >
            </TableCell>
            <TableCell class="text-center py-3">
              <span
                class="font-mono text-xs font-semibold text-muted-foreground"
              >
                {{ formatPenaltyTime(entry.penalty ?? 0) }}
              </span>
            </TableCell>
            <TableCell class="py-3">
              <div class="flex flex-wrap gap-1">
                <Badge
                  variant="secondary"
                  class="min-w-[2rem] justify-center h-6 px-2"
                >
                  {{ entry.problemsSolved }}
                </Badge>
              </div>
            </TableCell>
            <TableCell class="pr-6 text-right py-3">
              <span class="font-mono text-xs font-bold text-foreground">
                {{ entry.problemsSolved ?? 0 }}
              </span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <div
        v-if="paginated"
        class="flex flex-wrap items-center justify-between gap-3 border-t p-4"
      >
        <span
          role="status"
          aria-live="polite"
          class="text-sm text-muted-foreground"
          >{{
            t("common.pagination.page", { current: page, total: totalPages })
          }}</span
        >
        <div class="flex gap-2">
          <Button
            variant="outline"
            size="icon"
            :aria-label="t('common.pagination.previousPage')"
            :disabled="loading || page <= 1"
            @click="emit('pageChange', page - 1)"
            ><ChevronLeft class="h-4 w-4"
          /></Button>
          <Button
            variant="outline"
            size="icon"
            :aria-label="t('common.pagination.nextPage')"
            :disabled="loading || page >= totalPages"
            @click="emit('pageChange', page + 1)"
            ><ChevronRight class="h-4 w-4"
          /></Button>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
