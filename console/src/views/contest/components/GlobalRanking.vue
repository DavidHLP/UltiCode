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
      class="border-none shadow-[var(--shadow-float)] bg-[var(--background)]"
    >
      <div class="p-6 text-center space-y-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-extrabold text-foreground/80 italic">
            {{ t("contest.ranking.national") }}
          </h2>
          <span
            class="text-xs text-muted-foreground border rounded px-1 cursor-pointer"
            >{{ t("contest.ranking.globalRanking") }}</span
          >
        </div>

        <!-- Podium Visual -->
        <div class="flex items-end justify-center gap-4 h-48 pt-4 pb-2">
          <!-- 2nd Place -->
          <div
            class="flex flex-col items-center gap-2 w-1/3 relative group cursor-pointer"
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
                class="w-14 h-14 rounded-full border-4 border-[var(--silver-200)] shadow-[var(--shadow-float)]"
              />
              <div class="absolute -bottom-2 w-full text-center">
                <span
                  class="bg-[var(--silver-200)] text-[var(--foreground)] text-xs px-2 py-0.5 rounded-full font-black shadow-[var(--shadow-float)]"
                  >2</span
                >
              </div>
            </div>
            <p
              class="text-xs font-bold truncate w-full text-[var(--foreground)] mt-1"
            >
              {{ rankings[1].username }}
            </p>
            <RatingBadge :rating="rankings[1].rating" size="sm" />
            <div
              class="h-24 w-full bg-[var(--silver-100)] rounded-none shadow-[var(--shadow-float)] border-t border-[var(--silver-200)] relative overflow-hidden"
            >
              <div
                class="absolute inset-0 bg-white/30 skew-y-12 opacity-50"
              ></div>
            </div>
          </div>

          <!-- 1st Place -->
          <div
            class="flex flex-col items-center gap-2 w-1/3 relative z-10 group cursor-pointer"
            v-if="rankings[0]"
          >
            <div
              class="relative transition-transform duration-300 group-hover:-translate-y-2"
            >
              <div
                class="absolute -top-6 left-1/2 -translate-x-1/2 text-3xl animate-bounce"
              >
                👑
              </div>
              <img
                :src="
                  rankings[0].avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
                "
                class="w-20 h-20 rounded-full border-4 border-[var(--terminal-amber)] shadow-[var(--shadow-float)] ring-4 ring-[var(--terminal-amber)]/20"
              />
              <div class="absolute -bottom-3 w-full text-center">
                <span
                  class="bg-[var(--terminal-amber)] text-white text-sm px-2.5 py-0.5 rounded-full font-black shadow-[var(--shadow-float)]"
                  >1</span
                >
              </div>
            </div>
            <p
              class="text-sm font-black truncate w-full text-[var(--terminal-amber)] mt-1"
            >
              {{ rankings[0].username }}
            </p>
            <RatingBadge :rating="rankings[0].rating" size="sm" />
            <div
              class="h-36 w-full bg-[var(--terminal-amber)]/10 rounded-none shadow-[var(--shadow-float)] border-t border-[var(--terminal-amber)]/50 relative overflow-hidden"
            >
              <div
                class="absolute inset-0 bg-white/40 skew-y-12 opacity-50"
              ></div>
              <div
                class="absolute bottom-0 w-full text-center pb-2 opacity-10 font-black text-4xl text-[var(--terminal-amber)]"
              >
                1
              </div>
            </div>
          </div>

          <!-- 3rd Place -->
          <div
            class="flex flex-col items-center gap-2 w-1/3 relative group cursor-pointer"
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
                class="w-14 h-14 rounded-full border-4 border-[var(--terminal-amber)] shadow-[var(--shadow-float)]"
              />
              <div class="absolute -bottom-2 w-full text-center">
                <span
                  class="bg-[var(--terminal-amber)] text-[var(--foreground)] text-xs px-2 py-0.5 rounded-full font-black shadow-[var(--shadow-float)]"
                  >3</span
                >
              </div>
            </div>
            <p
              class="text-xs font-bold truncate w-full text-[var(--terminal-amber)] mt-1"
            >
              {{ rankings[2].username }}
            </p>
            <RatingBadge :rating="rankings[2].rating" size="sm" />
            <div
              class="h-16 w-full bg-[var(--terminal-amber)]/10 rounded-none shadow-[var(--shadow-float)] border-t border-[var(--terminal-amber)]/50 relative overflow-hidden"
            >
              <div
                class="absolute inset-0 bg-white/30 skew-y-12 opacity-50"
              ></div>
            </div>
          </div>
        </div>

        <!-- Rest of List -->
        <div class="space-y-1 pt-4 border-t">
          <div
            v-for="(user, index) in rankings.slice(3, 10)"
            :key="user.username"
            class="flex items-center gap-3 p-2 rounded-none hover:bg-muted/50 transition-colors cursor-pointer group"
          >
            <span
              class="text-xs font-bold w-6 text-center text-muted-foreground group-hover:text-foreground transition-colors"
              >{{ index + 4 }}</span
            >
            <img
              :src="
                user.avatar ||
                'https://assets.leetcode.cn/aliyun-lc-upload/users/default_avatar.png'
              "
              class="h-8 w-8 rounded-full bg-muted border border-border"
            />
            <div class="flex-1 min-w-0 text-left">
              <p
                class="truncate text-sm font-medium group-hover:text-primary transition-colors"
              >
                {{ user.username }}
              </p>
              <p class="text-xs text-muted-foreground">
                {{
                  t("contest.ranking.attended", {
                    n: user.contestsAttended || 0,
                  })
                }}
              </p>
            </div>
            <RatingBadge :rating="user.rating" size="sm" />
          </div>
        </div>

        <Button variant="ghost" class="w-full text-primary text-sm">{{
          t("common.actions.loadMore")
        }}</Button>
      </div>
    </Card>
  </div>
</template>
