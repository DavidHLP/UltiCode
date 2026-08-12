<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatPenaltyTime } from "@/utils/datetime";
import { useI18n } from "vue-i18n";
import type { ContestRankingEntry, LiveRankingEntry } from "@/types/contest";

defineProps<{
  rankings: (ContestRankingEntry | LiveRankingEntry)[];
  getCountryFlag: (countryCode: string) => string;
}>();

const { t } = useI18n();
</script>

<template>
  <Card
    id="contest-ranking"
    class="border border-border bg-surface dark:bg-surface-highlight shadow-[var(--shadow-float)] overflow-hidden rounded-none"
  >
    <CardHeader
      class="flex flex-row items-center justify-between pb-3 border-b border-border bg-[var(--surface-highlight)]/50 dark:bg-background/50"
    >
      <CardTitle
        class="text-xs font-bold font-mono uppercase tracking-widest text-foreground dark:text-foreground-strong"
        >{{ t("contest.detail.leaderboard") }}</CardTitle
      >
      <Button
        variant="outline"
        size="sm"
        class="rounded-none h-8 px-3 font-bold text-xs uppercase tracking-wider border border-border bg-transparent hover:bg-[var(--surface-highlight)] dark:hover:bg-background cursor-pointer"
        >{{ t("contest.detail.viewAll") }}</Button
      >
    </CardHeader>
    <CardContent class="p-0">
      <Table>
        <TableHeader
          class="bg-[var(--surface-highlight)]/45 dark:bg-background/45 border-b border-border/40"
        >
          <TableRow class="hover:bg-transparent">
            <TableHead
              class="w-20 pl-6 font-bold font-mono text-2xs tracking-wider uppercase text-muted-foreground h-10"
              >{{ t("contest.detail.rankingHeaders.rank") }}</TableHead
            >
            <TableHead
              class="font-bold font-mono text-2xs tracking-wider uppercase text-muted-foreground h-10"
              >{{ t("contest.detail.rankingHeaders.user") }}</TableHead
            >
            <TableHead
              class="w-24 text-center font-bold font-mono text-2xs tracking-wider uppercase text-muted-foreground h-10"
              >{{ t("contest.detail.rankingHeaders.score") }}</TableHead
            >
            <TableHead
              class="w-32 text-center font-bold font-mono text-2xs tracking-wider uppercase text-muted-foreground h-10"
              >{{ t("contest.detail.rankingHeaders.time") }}</TableHead
            >
            <TableHead
              class="w-48 font-bold font-mono text-2xs tracking-wider uppercase text-muted-foreground h-10"
              >{{ t("contest.detail.rankingHeaders.problems") }}</TableHead
            >
            <TableHead
              class="w-32 pr-6 text-right font-bold font-mono text-2xs tracking-wider uppercase text-muted-foreground h-10"
              >{{
                t("contest.detail.rankingHeaders.problemsSolved")
              }}</TableHead
            >
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="entry in rankings.slice(0, 20)"
            :key="entry.username"
            class="group border-b border-border/30 last:border-b-0 hover:bg-[var(--surface-highlight)]/30 dark:hover:bg-background/30 transition-all transition-colors"
          >
            <TableCell class="pl-6 py-3">
              <div
                class="inline-flex h-9 w-9 items-center justify-center rounded-none font-black text-xs transition-all shadow-sm"
                :class="{
                  'bg-surface-highlight text-foreground-strong border border-[var(--rank-first)] scale-110':
                    entry.rank === 1,
                  'bg-surface-highlight text-foreground-strong border border-[var(--rank-second)] scale-105':
                    entry.rank === 2,
                  'bg-surface-highlight text-foreground-strong border border-[var(--rank-third)]': entry.rank === 3,
                  'bg-surface-highlight text-foreground border border-control':
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
                    class="h-9 w-9 rounded-none border border-border bg-muted shadow-sm"
                    alt="Avatar"
                  />
                  <span
                    class="absolute -bottom-1 -right-1 text-base shadow-sm bg-background rounded-none"
                  >
                    {{ getCountryFlag(entry.country || "CN") }}
                  </span>
                </div>
                <div class="flex flex-col">
                  <span
                    class="font-bold text-sm text-foreground dark:text-foreground-strong"
                    >{{ entry.username }}</span
                  >
                  <span
                    class="text-2xs font-bold text-muted-foreground uppercase tracking-widest"
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
                  class="min-w-[2rem] justify-center font-mono text-2xs h-6 rounded-none px-2 border border-border/40 bg-[var(--surface-highlight)] dark:bg-background text-foreground font-bold"
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
    </CardContent>
  </Card>
</template>
