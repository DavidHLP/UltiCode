<script setup lang="ts">
import { Card, CardContent } from "@/components/ui/card";
import { Calendar, Clock, Users } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestDetail } from "@/types/contest";

defineProps<{
  contest: ContestDetail;
  statusCardClass: string;
  statusLabel: string;
  statusCountdown: string;
  statusHint: string;
  statusProgress: number;
  contestEndTime: string;
  formatDateTime: (iso: string) => string;
}>();

const { t } = useI18n();
</script>

<template>
  <Card
    class="border-none shadow-[var(--shadow-float)] text-white overflow-hidden rounded-none backdrop-blur-md relative"
    :class="statusCardClass"
  >
    <!-- Background Pattern -->
    <div
      class="absolute inset-0 bg-[url('data:image/svg+xml,%3Csvg viewBox=%220 0 200 200%22 xmlns=%22http://www.w3.org/2000/svg%22%3E%3Cfilter id=%22noise%22%3E%3CfeTurbulence type=%22fractalNoise%22 baseFrequency=%220.8%22 numOctaves=%223%22 stitchTiles=%22stitch%22/%3E%3C/filter%3E%3Crect width=%22100%25%22 height=%22100%25%22 filter=%22url(%23noise)%22/%3E%3C/svg%3E')] opacity-20 mix-blend-soft-light"
    ></div>
    <div
      class="absolute -top-24 -right-24 w-64 h-64 bg-white/10 rounded-full blur-3xl"
    ></div>
    <div
      class="absolute -bottom-24 -left-24 w-64 h-64 bg-black/10 rounded-full blur-3xl"
    ></div>

    <CardContent class="p-8 relative z-10">
      <div
        class="flex flex-col gap-6 md:flex-row md:items-center md:justify-between"
      >
        <div class="space-y-3">
          <p class="text-xs font-bold uppercase tracking-[0.2em] text-white/80">
            {{ t("contest.detail.contestStatus") }}
          </p>
          <div class="flex items-center gap-3">
            <span
              class="inline-flex items-center gap-2 rounded-none bg-white/20 px-3 py-1 text-[10px] font-black uppercase tracking-widest backdrop-blur-sm border border-white/10"
            >
              <span
                v-if="contest.status === 'RUNNING'"
                class="h-2 w-2 rounded-full bg-[var(--terminal-red)] animate-pulse shadow-[0_0_8px_2px_oklch(0.6_0.25_25)]"
              ></span>
              <span
                v-else-if="contest.status === 'UPCOMING'"
                class="h-2 w-2 rounded-full bg-[var(--terminal-green)]"
              ></span>
              {{
                contest.status === "RUNNING"
                  ? t("contest.list.liveBadge")
                  : contest.status === "UPCOMING"
                    ? t("contest.status.upcoming")
                    : t("contest.status.finished")
              }}
            </span>
            <span v-if="statusHint" class="text-xs font-medium text-white/90">{{
              statusHint
            }}</span>
          </div>
          <p
            class="text-3xl font-black md:text-5xl tracking-tight drop-shadow-sm"
          >
            {{ statusLabel }}
          </p>
        </div>
        <div class="text-left md:text-right space-y-2">
          <p class="text-xs font-bold uppercase tracking-widest text-white/70">
            {{
              contest.status === "RUNNING"
                ? t("contest.virtual.timeRemaining")
                : contest.status === "UPCOMING"
                  ? t("contest.time.startsIn")
                  : t("contest.detail.status")
            }}
          </p>
          <p
            class="font-mono text-4xl font-black tracking-tighter md:text-6xl tabular-nums drop-shadow-sm"
          >
            {{ statusCountdown }}
          </p>
          <p v-if="contestEndTime" class="text-xs font-medium text-white/60">
            {{
              contest.status === "FINISHED"
                ? t("contest.detail.endedAt")
                : t("contest.detail.endsAt")
            }}
            {{ formatDateTime(contestEndTime) }}
          </p>
        </div>
      </div>

      <div
        class="mt-8 h-3 rounded-none bg-black/20 overflow-hidden backdrop-blur-sm border border-white/5"
      >
        <div
          class="h-full bg-white/80 shadow-[0_0_10px_oklch(0.95_0_0_0)] transition-all duration-1000 ease-out"
          :style="{ width: `${statusProgress}%` }"
        ></div>
      </div>

      <div
        class="mt-6 flex flex-wrap items-center gap-6 text-xs font-medium text-white/80"
      >
        <span
          class="flex items-center gap-2 bg-black/10 px-3 py-1.5 rounded-none backdrop-blur-sm border border-white/5"
        >
          <Calendar class="h-4 w-4" />
          {{ formatDateTime(contest.startTime) }}
        </span>
        <span
          v-if="contestEndTime"
          class="flex items-center gap-2 bg-black/10 px-3 py-1.5 rounded-none backdrop-blur-sm border border-white/5"
        >
          <Clock class="h-4 w-4" />
          {{ formatDateTime(contestEndTime) }}
        </span>
        <span
          class="flex items-center gap-2 bg-black/10 px-3 py-1.5 rounded-none backdrop-blur-sm border border-white/5"
        >
          <Users class="h-4 w-4" />
          {{ contest.participantCount || 0 }}
          {{ t("contest.detail.participants") }}
        </span>
      </div>
    </CardContent>
  </Card>
</template>
