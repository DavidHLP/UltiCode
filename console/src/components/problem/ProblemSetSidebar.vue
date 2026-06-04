<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  type DateValue,
  getLocalTimeZone,
  today,
} from "@internationalized/date";
import { Badge } from "@/components/ui/badge";
import { type Ref, ref, onMounted } from "vue";
import { Trophy } from "lucide-vue-next";
import { fetchDailyActivity } from "@/api/submission";
import { useI18n } from "vue-i18n";

const date = ref(today(getLocalTimeZone())) as Ref<DateValue>;
const completedDates = ref<string[]>([]);

const { t } = useI18n();
onMounted(async () => {
  if (useAuthStore().isAuthenticated) {
    try {
      const year = new Date().getFullYear();
      completedDates.value = await fetchDailyActivity(year);
    } catch (e) {
      console.error("Failed to fetch daily activity", e);
    }
  }
});
</script>

<template>
  <div class="space-y-6">
    <!-- Calendar Widget -->
    <Card class="terminal-card shadow-sm">
      <CardHeader class="pb-3 border-b border-border/40">
        <CardTitle
          class="text-sm font-bold flex items-center justify-between"
        >
          <div class="flex items-center gap-2">
            <Trophy class="w-4 h-4 text-[var(--terminal-amber)] animate-pulse" />
            <span class="font-sans">{{ t("problem.sidebar.dailyChallenge") }}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <Badge
              variant="outline"
              class="text-[10px] font-mono font-bold h-5 px-2 flex gap-1 items-center bg-[var(--surface-sunken)] rounded-none border-border/40 text-[var(--terminal-amber)]"
            >
              <span
                class="w-1.5 h-1.5 rounded-full bg-[var(--terminal-amber)]"
              ></span>
              <span class="tabular-nums">{{ t("problem.sidebar.completed") }} {{ completedDates.length }} {{ t("problem.sidebar.daysUnit") }}</span>
            </Badge>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent class="p-4 flex flex-col items-center bg-card">
        <Calendar
          v-model="date"
          class="rounded-none border-0 p-0 w-full"
          :completed-dates="completedDates"
        />
        <!-- Legend description -->
        <div class="w-full mt-4 pt-3 border-t border-border/20 flex items-center justify-center gap-4 text-[10px] font-mono text-[var(--solarized-base01)] dark:text-[var(--silver-400)]">
          <div class="flex items-center gap-1.5">
            <span class="w-2.5 h-2.5 rounded-[3px] bg-[oklch(0.6545_0.1340_85.7_/_0.15)] ring-1 ring-[oklch(0.6545_0.1340_85.7_/_0.3)] ring-inset"></span>
            <span>{{ t("problem.sidebar.legendCompleted") }}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <span class="w-2.5 h-2.5 rounded-[3px] border border-dashed border-[var(--accent-electric)]/70 bg-[var(--accent-electric)]/6"></span>
            <span>{{ t("problem.sidebar.legendToday") }}</span>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Session/Progress Widget Placeholder -->
  </div>
</template>
