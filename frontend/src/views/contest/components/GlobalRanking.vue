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
      class="border-none shadow-sm bg-gradient-to-b from-gray-50/50 to-white"
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
                class="w-14 h-14 rounded-full border-4 border-slate-300 shadow-lg"
              />
              <div class="absolute -bottom-2 w-full text-center">
                <span
                  class="bg-slate-300 text-slate-800 text-xs px-2 py-0.5 rounded-full font-black shadow-sm"
                  >2</span
                >
              </div>
            </div>
            <p
              class="text-xs font-bold truncate w-full text-slate-700 dark:text-slate-300 mt-1"
            >
              {{ rankings[1].username }}
            </p>
            <RatingBadge :rating="rankings[1].rating" size="sm" />
            <div
              class="h-24 w-full bg-gradient-to-b from-slate-200 via-slate-100 to-white dark:from-slate-800 dark:to-slate-900 rounded-t-xl shadow-inner border-t border-slate-300/50 relative overflow-hidden"
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
                class="w-20 h-20 rounded-full border-4 border-yellow-400 shadow-xl ring-4 ring-yellow-400/20"
              />
              <div class="absolute -bottom-3 w-full text-center">
                <span
                  class="bg-gradient-to-r from-yellow-400 to-orange-400 text-white text-sm px-2.5 py-0.5 rounded-full font-black shadow-md"
                  >1</span
                >
              </div>
            </div>
            <p
              class="text-sm font-black truncate w-full text-yellow-600 dark:text-yellow-400 mt-1"
            >
              {{ rankings[0].username }}
            </p>
            <RatingBadge :rating="rankings[0].rating" size="sm" />
            <div
              class="h-36 w-full bg-gradient-to-b from-yellow-100 via-yellow-50 to-white dark:from-yellow-900/40 dark:to-slate-900 rounded-t-xl shadow-lg border-t border-yellow-300/50 relative overflow-hidden"
            >
              <div
                class="absolute inset-0 bg-white/40 skew-y-12 opacity-50"
              ></div>
              <div
                class="absolute bottom-0 w-full text-center pb-2 opacity-10 font-black text-4xl text-yellow-600"
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
                class="w-14 h-14 rounded-full border-4 border-orange-300 shadow-lg"
              />
              <div class="absolute -bottom-2 w-full text-center">
                <span
                  class="bg-orange-300 text-orange-800 text-xs px-2 py-0.5 rounded-full font-black shadow-sm"
                  >3</span
                >
              </div>
            </div>
            <p
              class="text-xs font-bold truncate w-full text-orange-700 dark:text-orange-300 mt-1"
            >
              {{ rankings[2].username }}
            </p>
            <RatingBadge :rating="rankings[2].rating" size="sm" />
            <div
              class="h-16 w-full bg-gradient-to-b from-orange-100 via-orange-50 to-white dark:from-orange-900/30 dark:to-slate-900 rounded-t-xl shadow-inner border-t border-orange-300/50 relative overflow-hidden"
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
            class="flex items-center gap-3 p-2 rounded-lg hover:bg-muted/50 transition-colors cursor-pointer group"
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
