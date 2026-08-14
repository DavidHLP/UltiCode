<script setup lang="ts">
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { GlobalRankingEntry } from "@/types/contest";
import RatingBadge from "./RatingBadge.vue";
import { useI18n } from "vue-i18n";

defineProps<{
  rankings: GlobalRankingEntry[];
}>();

const { t } = useI18n();
</script>

<template>
  <div class="space-y-6">
    <!-- Podium Section -->
    <Card
      class="rounded-none border border-border shadow-[3px_3px_0px_0px_var(--border)] bg-card text-foreground"
    >
      <div class="p-6 text-center space-y-6">
        <div
          class="flex items-center justify-between mb-4 border-b border-dashed pb-3"
        >
          <h2
            class="text-lg font-bold text-foreground font-mono tracking-tight flex items-center gap-2"
          >
            <span class="h-2.5 w-2.5 bg-[var(--status-warning-mark)]"></span>
            {{ t("contest.ranking.national") }}
          </h2>
          <span
            class="text-xs font-mono text-muted-foreground border border-border bg-muted/50 px-2 py-0.5 rounded-none cursor-pointer hover:bg-muted transition-colors"
            >{{ t("contest.ranking.globalRanking") }}</span
          >
        </div>

        <!-- Podium Visual -->
        <div class="flex items-end justify-center gap-4 h-56 pt-6 pb-2">
          <!-- 2nd Place -->
          <div
            class="flex flex-col items-center gap-1 w-1/3 relative group cursor-pointer"
            v-if="rankings[1]"
          >
            <div
              class="relative transition-transform duration-300 group-hover:-translate-y-1"
            >
              <img
                :src="
                  rankings[1].avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                "
                class="w-14 h-14 rounded-none border-2 border-[var(--border)] shadow-[2px_2px_0px_0px_var(--border)]"
              />
              <div class="absolute -bottom-2 w-full text-center">
                <span
                  class="bg-muted text-foreground border border-border text-2xs px-1.5 py-0.5 rounded-none font-bold shadow-[1px_1px_0px_0px_var(--border)] font-mono"
                  >2ND</span
                >
              </div>
            </div>
            <p
              class="text-xs font-bold truncate w-full text-foreground mt-2 text-center"
            >
              {{ rankings[1].name || rankings[1].username }}
            </p>
            <div class="flex flex-col items-center leading-tight">
              <RatingBadge :rating="rankings[1].rating" size="sm" />
              <span class="text-2xs font-mono text-muted-foreground"
                >{{ rankings[1].contestsAttended || 0 }}
                {{ t("contest.ranking.title", "Contests") }}</span
              >
            </div>
            <div
              class="h-20 w-full bg-muted/60 rounded-none shadow-[2px_2px_0px_0px_var(--border)] border border-border border-b-0 relative overflow-hidden mt-1"
            >
              <div
                class="absolute inset-0 bg-surface/5 skew-y-12 opacity-50"
              ></div>
              <div
                class="absolute bottom-0 w-full text-center pb-2 opacity-20 font-black text-4xl text-foreground-strong font-mono"
                data-testid="podium-rank-number"
              >
                2
              </div>
            </div>
          </div>

          <!-- 1st Place -->
          <div
            class="flex flex-col items-center gap-1 w-1/3 relative z-10 group cursor-pointer"
            v-if="rankings[0]"
          >
            <div
              class="relative transition-transform duration-300 group-hover:-translate-y-2"
            >
              <div
                class="absolute -top-6 left-1/2 -translate-x-1/2 text-2xl animate-bounce"
              >
                👑
              </div>
              <img
                :src="
                  rankings[0].avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                "
                class="w-16 h-16 rounded-none border-2 border-[var(--status-warning-mark)] shadow-[2px_2px_0px_0px_var(--status-warning-mark)]"
              />
              <div class="absolute -bottom-2.5 w-full text-center">
                <span
                  class="bg-surface-highlight text-foreground-strong border border-[var(--rank-first)] text-xxs px-2 py-0.5 rounded-none font-black shadow-[1px_1px_0px_0px_var(--border)] font-mono"
                  >1ST</span
                >
              </div>
            </div>
            <p
              class="text-sm font-black truncate w-full text-foreground-strong mt-3 text-center"
            >
              {{ rankings[0].name || rankings[0].username }}
            </p>
            <div class="flex flex-col items-center leading-tight">
              <RatingBadge :rating="rankings[0].rating" size="sm" />
              <span class="text-2xs font-mono text-muted-foreground"
                >{{ rankings[0].contestsAttended || 0 }}
                {{ t("contest.ranking.title", "Contests") }}</span
              >
            </div>
            <div
              class="h-28 w-full bg-[var(--status-warning-mark)]/5 rounded-none shadow-[2px_2px_0px_0px_var(--border)] border border-[var(--status-warning-mark)]/30 border-b-0 relative overflow-hidden mt-1"
            >
              <div
                class="absolute inset-0 bg-surface/10 skew-y-12 opacity-50"
              ></div>
              <div
                class="absolute bottom-0 w-full text-center pb-2 opacity-20 font-black text-4xl text-foreground-strong font-mono"
                data-testid="podium-rank-number"
              >
                1
              </div>
            </div>
          </div>

          <!-- 3rd Place -->
          <div
            class="flex flex-col items-center gap-1 w-1/3 relative group cursor-pointer"
            v-if="rankings[2]"
          >
            <div
              class="relative transition-transform duration-300 group-hover:-translate-y-1"
            >
              <img
                :src="
                  rankings[2].avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                "
                class="w-14 h-14 rounded-none border-2 border-[var(--rank-third)] shadow-[2px_2px_0px_0px_var(--rank-third)]"
              />
              <div class="absolute -bottom-2 w-full text-center">
                <span
                  class="bg-muted text-[var(--rank-third)] border border-[var(--rank-third)] text-2xs px-1.5 py-0.5 rounded-none font-bold shadow-[1px_1px_0px_0px_var(--border)] font-mono"
                  >3RD</span
                >
              </div>
            </div>
            <p
              class="text-xs font-bold truncate w-full text-foreground mt-2 text-center"
            >
              {{ rankings[2].name || rankings[2].username }}
            </p>
            <div class="flex flex-col items-center leading-tight">
              <RatingBadge :rating="rankings[2].rating" size="sm" />
              <span class="text-2xs font-mono text-muted-foreground"
                >{{ rankings[2].contestsAttended || 0 }}
                {{ t("contest.ranking.title", "Contests") }}</span
              >
            </div>
            <div
              class="h-12 w-full bg-[var(--rank-third)]/5 rounded-none shadow-[2px_2px_0px_0px_var(--border)] border border-[var(--rank-third)]/30 border-b-0 relative overflow-hidden mt-1"
            >
              <div
                class="absolute inset-0 bg-surface/5 skew-y-12 opacity-50"
              ></div>
              <div
                class="absolute bottom-0 w-full text-center pb-2 opacity-20 font-black text-4xl text-foreground-strong font-mono"
                data-testid="podium-rank-number"
              >
                3
              </div>
            </div>
          </div>
        </div>

        <!-- Table Column Headers -->
        <div
          class="flex items-center gap-3 px-2 py-2 text-xs font-mono font-bold uppercase tracking-wider border-b border-border bg-muted/30 text-muted-foreground/80 rounded-none mt-4"
        >
          <span class="w-6 text-center">#</span>
          <span class="flex-1 text-left">{{ t("contest.ranking.user") }}</span>
          <span class="w-20 text-center">{{
            t("contest.ranking.title", "Contests")
          }}</span>
          <span class="w-20 text-right">{{
            t("contest.ranking.score", "Rating")
          }}</span>
        </div>

        <!-- Rest of List -->
        <div class="space-y-1 divide-y divide-border/40 mt-1">
          <div
            v-for="(user, index) in rankings.slice(3, 10)"
            :key="user.username"
            class="flex items-center gap-3 p-2 rounded-none hover:bg-muted/40 transition-colors cursor-pointer group"
          >
            <span
              class="text-xs font-bold font-mono w-6 text-center text-muted-foreground group-hover:text-foreground transition-colors"
              >{{ index + 4 }}</span
            >
            <div class="flex-1 flex items-center gap-2 min-w-0 text-left">
              <img
                :src="
                  user.avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                "
                class="h-7 w-7 rounded-none bg-muted border border-border shrink-0"
              />
              <span
                class="truncate text-sm font-medium group-hover:text-primary transition-colors"
              >
                {{ user.name || user.username }}
              </span>
            </div>
            <span
              class="w-20 text-center text-xs font-mono text-muted-foreground"
            >
              {{ user.contestsAttended || 0 }}
            </span>
            <div class="w-20 text-right shrink-0">
              <RatingBadge :rating="user.rating" size="sm" />
            </div>
          </div>
        </div>

        <Button
          variant="outline"
          class="w-full text-xs font-bold font-mono rounded-none border border-border bg-transparent shadow-[2px_2px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 mt-4"
          >{{ t("common.actions.loadMore") }}</Button
        >
      </div>
    </Card>
  </div>
</template>
