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
    <Card class="border-muted/60 bg-card/50 rounded-none">
      <CardHeader class="pb-2 border-b border-border/50 bg-muted/20">
        <CardTitle
          class="text-sm font-medium flex items-center justify-between"
        >
          <div class="flex items-center gap-2">
            <Trophy class="w-4 h-4 text-[var(--terminal-amber)]" />
            <span>{{ t("problem.sidebar.dailyChallenge") }}</span>
          </div>
          <div class="flex items-center gap-1">
            <span class="text-xs font-normal text-muted-foreground mr-1"
              >Dec 2025</span
            >
            <Badge
              variant="outline"
              class="text-[10px] font-data font-normal h-5 px-1.5 flex gap-1 items-center bg-background/50 rounded-none border-silver"
            >
              <span
                class="w-1.5 h-1.5 bg-[var(--terminal-amber)] shadow-[0_0_4px_var(--terminal-amber)]"
              ></span>
              <span class="tabular-nums">{{ completedDates.length }}</span>
            </Badge>
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent class="p-3 flex justify-center bg-card/30">
        <Calendar
          v-model="date"
          class="rounded-none border-0 p-0"
          :completed-dates="completedDates"
        />
      </CardContent>
    </Card>

    <!-- Session/Progress Widget Placeholder -->
  </div>
</template>
