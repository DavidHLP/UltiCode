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
import { TrendingUp, TrendingDown } from "lucide-vue-next";
import { formatPenaltyTime } from "@/utils/date";
import { useI18n } from "vue-i18n";
import type { ContestRankingEntry } from "@/types/contest";

defineProps<{
  rankings: ContestRankingEntry[];
  getCountryFlag: (countryCode: string) => string;
}>();

const { t } = useI18n();
</script>

<template>
  <Card
    id="contest-ranking"
    class="border-none shadow-sm overflow-hidden rounded-none"
  >
    <CardHeader
      class="flex flex-row items-center justify-between pb-3 border-b bg-muted/20"
    >
      <CardTitle
        class="text-lg font-black uppercase tracking-widest text-muted-foreground"
        >{{ t("contest.detail.leaderboard") }}</CardTitle
      >
      <Button
        variant="outline"
        size="sm"
        class="rounded-full h-8 font-bold text-[10px]"
        >{{ t("contest.detail.viewAll") }}</Button
      >
    </CardHeader>
    <CardContent class="p-0">
      <Table>
        <TableHeader class="bg-muted/50">
          <TableRow>
            <TableHead class="w-20 pl-6 font-bold">{{
              t("contest.detail.rankingHeaders.rank")
            }}</TableHead>
            <TableHead class="font-bold">{{
              t("contest.detail.rankingHeaders.user")
            }}</TableHead>
            <TableHead class="w-24 text-center font-bold">{{
              t("contest.detail.rankingHeaders.score")
            }}</TableHead>
            <TableHead class="w-32 text-center font-bold">{{
              t("contest.detail.rankingHeaders.time")
            }}</TableHead>
            <TableHead class="w-48 font-bold">{{
              t("contest.detail.rankingHeaders.problems")
            }}</TableHead>
            <TableHead class="w-32 pr-6 text-right font-bold">{{
              t("contest.detail.rankingHeaders.rating")
            }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="entry in rankings.slice(0, 20)"
            :key="entry.username"
            class="group hover:bg-muted/30 transition-colors"
          >
            <TableCell class="pl-6">
              <div
                class="inline-flex h-10 w-10 items-center justify-center rounded-none font-black text-sm transition-all"
                :class="{
                  'bg-[var(--terminal-amber)] text-white shadow-[var(--shadow-float)] shadow-[var(--terminal-amber)]/20 scale-110':
                    entry.rank === 1,
                  'bg-[var(--silver-300)] text-white shadow-[var(--shadow-float)] shadow-[var(--silver-300)]/20 scale-105':
                    entry.rank === 2,
                  'bg-[var(--terminal-amber)] text-white shadow-[var(--shadow-float)] shadow-[var(--terminal-amber)]/20':
                    entry.rank === 3,
                  'bg-muted text-muted-foreground': entry.rank > 3,
                }"
              >
                {{ entry.rank }}
              </div>
            </TableCell>
            <TableCell>
              <div class="flex items-center gap-3">
                <div class="relative">
                  <img
                    :src="
                      entry.avatar ||
                      'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                    "
                    class="h-10 w-10 rounded-none border border-border bg-muted shadow-sm"
                    alt="Avatar"
                  />
                  <span
                    class="absolute -bottom-1 -right-1 text-base shadow-sm bg-background rounded-none"
                  >
                    {{ getCountryFlag(entry.country || "CN") }}
                  </span>
                </div>
                <div class="flex flex-col">
                  <span class="font-black text-sm">{{
                    entry.username
                  }}</span>
                  <span
                    class="text-[10px] font-bold text-muted-foreground uppercase tracking-widest"
                  >
                    {{ entry.ratingBefore || 1500 }} ->
                    {{ entry.ratingAfter || 1500 }}
                  </span>
                </div>
              </div>
            </TableCell>
            <TableCell class="text-center">
              <span class="text-xl font-black tracking-tight">{{
                entry.totalScore ?? entry.score ?? 0
              }}</span>
            </TableCell>
            <TableCell class="text-center">
              <span
                class="font-mono text-xs font-bold text-muted-foreground"
              >
                {{
                  formatPenaltyTime(
                    entry.finishTime ??
                      entry.finish_time ??
                      entry.totalPenalty ??
                      0,
                  )
                }}
              </span>
            </TableCell>
            <TableCell>
              <div class="flex flex-wrap gap-1">
                <Badge
                  v-for="result in entry.problemResults || []"
                  :key="result.problemIndex"
                  :variant="result.isSolved ? 'default' : 'secondary'"
                  class="min-w-[2rem] justify-center font-mono text-[10px] h-6 rounded px-1.5"
                >
                  {{ result.problemIndex }}
                </Badge>
              </div>
            </TableCell>
            <TableCell class="pr-6 text-right">
              <div
                class="inline-flex items-center gap-1 rounded-none px-2.5 py-1 text-xs font-black shadow-sm"
                :class="{
                  'bg-[var(--terminal-green)]/10 text-[var(--terminal-green)] border border-[var(--terminal-green)]/20':
                    (entry.ratingChange || 0) > 0,
                  'bg-[var(--terminal-red)]/10 text-[var(--terminal-red)] border border-[var(--terminal-red)]/20':
                    (entry.ratingChange || 0) < 0,
                  'bg-muted text-muted-foreground border border-border':
                    (entry.ratingChange || 0) === 0,
                }"
              >
                <TrendingUp
                  v-if="(entry.ratingChange || 0) > 0"
                  class="h-3 w-3"
                />
                <TrendingDown
                  v-else-if="(entry.ratingChange || 0) < 0"
                  class="h-3 w-3"
                />
                {{ (entry.ratingChange || 0) > 0 ? "+" : ""
                }}{{ entry.ratingChange || 0 }}
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
