<script setup lang="ts">
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Calendar, Clock, Users, Trophy, PlayCircle } from "lucide-vue-next";
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
  isRegistered: boolean;
  registering: boolean;
  startingVirtual: boolean;
  virtualSessionActive: boolean;
}>();

const emit = defineEmits<{
  (e: "register"): void;
  (e: "unregister"): void;
  (e: "startVirtual"): void;
  (e: "scrollToProblems"): void;
  (e: "scrollToRanking"): void;
}>();

const { t } = useI18n();

const statusColor = computed(() => {
  if (props.contest.status === "RUNNING") return "var(--status-error-mark)";
  if (props.contest.status === "UPCOMING") return "var(--status-success-mark)";
  return "var(--foreground)";
});
</script>

<template>
  <Card
    class="border border-border bg-surface dark:bg-surface-highlight shadow-[var(--shadow-float)] text-foreground overflow-hidden rounded-none relative transition-all duration-300"
    :class="statusCardClass"
  >
    <CardContent class="p-6 md:p-8 relative z-10">
      <div class="grid grid-cols-1 md:grid-cols-3 gap-8">
        <!-- Left Column: Status, Countdown & Progress -->
        <div
          class="md:col-span-2 space-y-5 flex flex-col justify-between pr-0 md:pr-8 md:border-r border-border/40"
        >
          <div class="space-y-3">
            <p
              class="text-2xs font-bold font-mono uppercase tracking-widest text-muted-foreground"
            >
              {{ t("contest.detail.contestStatus") }}
            </p>
            <div class="flex items-center gap-3">
              <span
                class="inline-flex items-center gap-1.5 rounded-none px-2.5 py-0.5 text-2xs font-bold font-mono uppercase tracking-widest border transition-all duration-300"
                :style="{
                  color: statusColor,
                  backgroundColor: `color-mix(in oklch, ${statusColor} 10%, transparent)`,
                  borderColor: `color-mix(in oklch, ${statusColor} 30%, transparent)`,
                }"
              >
                <span
                  v-if="contest.status === 'RUNNING'"
                  class="h-1.5 w-1.5 rounded-full bg-[var(--status-error-mark)] animate-pulse shadow-[0_0_8px_2px_var(--status-error-mark)]"
                ></span>
                <span
                  v-else-if="contest.status === 'UPCOMING'"
                  class="h-1.5 w-1.5 rounded-full bg-[var(--status-success-mark)] animate-pulse"
                ></span>
                {{
                  contest.status === "RUNNING"
                    ? t("contest.list.liveBadge")
                    : contest.status === "UPCOMING"
                      ? t("contest.status.upcoming")
                      : t("contest.status.finished")
                }}
              </span>
              <span
                v-if="statusHint"
                class="text-xs font-bold text-muted-foreground"
              >
                // {{ statusHint }}
              </span>
            </div>

            <div class="space-y-1 pt-1.5">
              <h2
                class="text-xl font-black text-foreground-strong dark:text-foreground-strong tracking-tight"
              >
                {{ statusLabel }}
              </h2>
              <!-- Monospace Timer Block -->
              <p
                v-if="contest.status !== 'FINISHED'"
                class="font-mono text-3xl font-black tracking-tight md:text-5xl text-[var(--primary)] select-all tabular-nums"
              >
                {{ statusCountdown }}
              </p>
              <p
                v-else
                class="text-lg font-black text-muted-foreground tracking-tight"
              >
                {{ statusCountdown }}
              </p>
            </div>
          </div>

          <!-- Progress Bar -->
          <div class="space-y-1.5 pt-2">
            <div
              class="h-2 rounded-none bg-[var(--surface-sunken)] overflow-hidden border border-border/40"
            >
              <div
                class="h-full transition-all duration-1000 ease-out"
                :class="{
                  'bg-[var(--status-success-mark)]': contest.status === 'UPCOMING',
                  'bg-[var(--status-error-mark)] shadow-[0_0_8px_var(--status-error-mark)]':
                    contest.status === 'RUNNING',
                  'bg-muted-foreground/40': contest.status === 'FINISHED',
                }"
                :style="{ width: `${statusProgress}%` }"
              ></div>
            </div>
          </div>
        </div>

        <!-- Right Column: Meta Information Table & Action CTA Button -->
        <div class="flex flex-col justify-between space-y-6">
          <!-- Structured Details Table -->
          <div class="space-y-3.5">
            <p
              class="text-2xs font-bold font-mono uppercase tracking-widest text-muted-foreground border-b border-border/30 pb-1"
            >
              {{ t("contest.detail.details") || "CONTEST DETAILS" }}
            </p>
            <div class="space-y-2.5">
              <div
                class="flex items-center justify-between text-xs border-b border-border/20 pb-1.5"
              >
                <span
                  class="text-muted-foreground font-medium flex items-center gap-1.5"
                >
                  <Calendar class="h-3.5 w-3.5" />
                  {{ t("contest.detail.startTime") }}
                </span>
                <span class="font-bold font-mono text-foreground">{{
                  formatDateTime(contest.startTime)
                }}</span>
              </div>
              <div
                class="flex items-center justify-between text-xs border-b border-border/20 pb-1.5"
              >
                <span
                  class="text-muted-foreground font-medium flex items-center gap-1.5"
                >
                  <Clock class="h-3.5 w-3.5" />
                  {{ t("contest.detail.endsAt") }}
                </span>
                <span class="font-bold font-mono text-foreground">{{
                  formatDateTime(contestEndTime)
                }}</span>
              </div>
              <div
                class="flex items-center justify-between text-xs border-b border-border/20 pb-1.5"
              >
                <span
                  class="text-muted-foreground font-medium flex items-center gap-1.5"
                >
                  <Users class="h-3.5 w-3.5" />
                  {{ t("contest.detail.participants") }}
                </span>
                <span class="font-bold font-mono text-foreground">{{
                  contest.participantCount || 0
                }}</span>
              </div>
            </div>
          </div>

          <!-- CTA Actions Panel -->
          <div class="pt-2">
            <!-- UPCOMING Actions -->
            <template v-if="contest.status === 'UPCOMING'">
              <Button
                v-if="!isRegistered"
                size="lg"
                class="w-full gap-2 rounded-none h-11 font-bold bg-primary hover:bg-primary/90 text-primary-foreground cursor-pointer uppercase tracking-wider text-xxs shadow-sm"
                :disabled="registering"
                @click="emit('register')"
              >
                <Users class="h-4 w-4" />
                {{
                  registering
                    ? t("contest.detail.registering")
                    : t("contest.detail.register")
                }}
              </Button>
              <Button
                v-else
                size="lg"
                variant="outline"
                class="w-full gap-2 rounded-none h-11 font-bold border border-destructive bg-status-error-surface hover:bg-status-error-surface/80 text-foreground-strong transition-colors cursor-pointer uppercase tracking-wider text-xxs"
                :disabled="registering"
                @click="emit('unregister')"
              >
                <Users class="h-4 w-4" />
                {{
                  registering
                    ? t("contest.detail.unregistering")
                    : t("contest.detail.unregister")
                }}
              </Button>
            </template>

            <!-- RUNNING Actions -->
            <template v-else-if="contest.status === 'RUNNING'">
              <div class="flex flex-col gap-2">
                <Button
                  size="lg"
                  class="w-full gap-2 rounded-none h-11 font-bold border border-status-error-mark bg-status-error-surface hover:bg-status-error-surface text-foreground-strong cursor-pointer uppercase tracking-wider text-xxs shadow-sm animate-pulse"
                  @click="emit('scrollToProblems')"
                >
                  <PlayCircle class="h-4 w-4" />
                  {{ t("contest.detail.enterContest") }}
                </Button>
                <Button
                  size="lg"
                  variant="outline"
                  class="w-full gap-2 rounded-none h-11 font-bold border border-border hover:bg-surface-highlight dark:hover:bg-surface cursor-pointer uppercase tracking-wider text-xxs"
                  @click="emit('scrollToRanking')"
                >
                  <Trophy class="h-4 w-4" />
                  {{ t("contest.detail.liveRanking") }}
                </Button>
              </div>
            </template>

            <!-- FINISHED Actions -->
            <template v-else-if="contest.status === 'FINISHED'">
              <Button
                v-if="!virtualSessionActive"
                size="lg"
                class="w-full gap-2 rounded-none h-11 font-bold bg-primary hover:bg-primary/90 text-primary-foreground cursor-pointer uppercase tracking-wider text-xxs shadow-sm"
                :disabled="startingVirtual"
                @click="emit('startVirtual')"
              >
                <PlayCircle class="h-4 w-4" />
                {{
                  startingVirtual
                    ? t("contest.detail.starting")
                    : t("contest.virtual.start")
                }}
              </Button>
              <Button
                v-else
                size="lg"
                variant="outline"
                class="w-full gap-2 rounded-none h-11 font-bold text-foreground-strong border border-status-success-mark/40 bg-status-success-surface uppercase tracking-wider text-xxs"
                disabled
              >
                <PlayCircle class="h-4 w-4" />
                {{ t("contest.virtual.active") }}
              </Button>
            </template>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
