<script setup lang="ts">
import { Card, CardContent } from "@/components/ui/card";
import { Calendar, Clock, Users } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestDetail } from "@/types/contest";
import { computed } from "vue";

const props = defineProps<{
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

const statusColor = computed(() => {
  if (props.contest.status === "RUNNING") return "var(--terminal-red)";
  if (props.contest.status === "UPCOMING") return "var(--terminal-green)";
  return "var(--silver-400)";
});
</script>

<template>
  <Card
    class="border border-silver bg-card/60 shadow-[var(--shadow-float)] text-foreground overflow-hidden rounded-none relative backdrop-blur-md transition-all duration-300"
    :class="statusCardClass"
  >
    <!-- Background Pattern -->
    <div
      class="absolute inset-0 bg-[url('data:image/svg+xml,%3Csvg viewBox=%220 0 200 200%22 xmlns=%22http://www.w3.org/2000/svg%22%3E%3Cfilter id=%22noise%22%3E%3CfeTurbulence type=%22fractalNoise%22 baseFrequency=%220.8%22 numOctaves=%223%22 stitchTiles=%22stitch%22/%3E%3C/filter%3E%3Crect width=%22100%25%22 height=%22100%25%22 filter=%22url(%23noise)%22/%3E%3C/svg%3E')] opacity-[0.03] dark:opacity-[0.05] pointer-events-none"
    ></div>

    <!-- Decorative Glows -->
    <div
      class="absolute -top-24 -right-24 w-64 h-64 rounded-full blur-3xl opacity-5 dark:opacity-10 pointer-events-none transition-all duration-1000"
      :style="{ backgroundColor: statusColor }"
    ></div>
    <div
      class="absolute -bottom-24 -left-24 w-64 h-64 rounded-full blur-3xl opacity-[0.02] dark:opacity-5 pointer-events-none transition-all duration-1000"
      :style="{ backgroundColor: statusColor }"
    ></div>

    <CardContent class="p-8 relative z-10">
      <div
        class="flex flex-col gap-6 md:flex-row md:items-center md:justify-between"
      >
        <div class="space-y-3">
          <p class="text-xs font-bold uppercase tracking-[0.2em] text-muted-foreground">
            {{ t("contest.detail.contestStatus") }}
          </p>
          <div class="flex items-center gap-3">
            <span
              class="inline-flex items-center gap-1.5 rounded-none px-2.5 py-0.5 text-[10px] font-black uppercase tracking-widest border transition-all duration-300"
              :style="{
                color: statusColor,
                backgroundColor: `color-mix(in oklch, ${statusColor} 10%, transparent)`,
                borderColor: `color-mix(in oklch, ${statusColor} 30%, transparent)`,
              }"
            >
              <span
                v-if="contest.status === 'RUNNING'"
                class="h-1.5 w-1.5 rounded-full bg-[var(--terminal-red)] animate-pulse shadow-[0_0_8px_2px_var(--terminal-red)]"
              ></span>
              <span
                v-else-if="contest.status === 'UPCOMING'"
                class="h-1.5 w-1.5 rounded-full bg-[var(--terminal-green)] animate-pulse"
              ></span>
              {{
                contest.status === "RUNNING"
                  ? t("contest.list.liveBadge")
                  : contest.status === "UPCOMING"
                    ? t("contest.status.upcoming")
                    : t("contest.status.finished")
              }}
            </span>
            <span v-if="statusHint" class="text-xs font-bold text-muted-foreground">
              // {{ statusHint }}
            </span>
          </div>
          <p
            class="text-2xl font-black md:text-3xl tracking-tight text-foreground"
          >
            {{ statusLabel }}
          </p>
        </div>

        <div class="text-left md:text-right space-y-2">
          <p class="text-xs font-bold uppercase tracking-widest text-muted-foreground">
            {{
              contest.status === "RUNNING"
                ? t("contest.virtual.timeRemaining")
                : contest.status === "UPCOMING"
                  ? t("contest.time.startsIn")
                  : t("contest.detail.status")
            }}
          </p>
          <p
            v-if="contest.status !== 'FINISHED'"
            class="font-mono text-3xl font-black tracking-tight md:text-5xl tabular-nums text-[var(--accent-electric)] filter drop-shadow-sm select-all"
          >
            {{ statusCountdown }}
          </p>
          <p
            v-else
            class="text-xl font-black md:text-3xl text-muted-foreground tracking-tight"
          >
            {{ statusCountdown }}
          </p>
          <p v-if="contestEndTime" class="text-xs font-bold text-muted-foreground">
            {{
              contest.status === "FINISHED"
                ? t("contest.detail.endedAt")
                : t("contest.detail.endsAt")
            }}:
            <span class="font-mono text-foreground font-semibold">
              {{ formatDateTime(contestEndTime) }}
            </span>
          </p>
        </div>
      </div>

      <!-- Precision Progress Bar -->
      <div
        class="mt-8 h-2 rounded-none bg-[var(--surface-sunken)] overflow-hidden border border-silver-100 dark:border-silver-800"
      >
        <div
          class="h-full transition-all duration-1000 ease-out"
          :class="{
            'bg-[var(--terminal-green)]': contest.status === 'UPCOMING',
            'bg-[var(--terminal-red)] shadow-[0_0_8px_var(--terminal-red)] animate-pulse': contest.status === 'RUNNING',
            'bg-muted-foreground/45': contest.status === 'FINISHED',
          }"
          :style="{ width: `${statusProgress}%` }"
        ></div>
      </div>

      <!-- Metadata Info pills -->
      <div
        class="mt-6 flex flex-wrap items-center gap-4 text-xs font-bold text-muted-foreground"
      >
        <span
          class="flex items-center gap-2 bg-[var(--surface-sunken)] px-3 py-1.5 rounded-none border border-silver"
        >
          <Calendar class="h-4 w-4 text-[var(--accent-electric)]" />
          {{ formatDateTime(contest.startTime) }}
        </span>
        <span
          v-if="contestEndTime"
          class="flex items-center gap-2 bg-[var(--surface-sunken)] px-3 py-1.5 rounded-none border border-silver"
        >
          <Clock class="h-4 w-4 text-[var(--accent-electric)]" />
          {{ formatDateTime(contestEndTime) }}
        </span>
        <span
          class="flex items-center gap-2 bg-[var(--surface-sunken)] px-3 py-1.5 rounded-none border border-silver"
        >
          <Users class="h-4 w-4 text-[var(--accent-electric)]" />
          {{ contest.participantCount || 0 }}
          {{ t("contest.detail.participants") }}
        </span>
      </div>
    </CardContent>
  </Card>
</template>
