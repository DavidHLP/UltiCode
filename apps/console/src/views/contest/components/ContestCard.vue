<script setup lang="ts">
/**
 * ContestCard - Displays contest information in a card format
 *
 * Shows contest title, type, time, status, registration/participant count.
 * Links to contest detail page.
 */
import { computed } from "vue";
import { useRouter } from "vue-router";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Calendar, Clock, Users, Trophy } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestListItem } from "@/types/contest";
import { formatDateTime, getDurationMinutes } from "@/utils/datetime";
import ContestStatusBadge from "./ContestStatusBadge.vue";
import ContestTimer from "./ContestTimer.vue";

const props = defineProps<{
  contest: ContestListItem;
  /** Card variant */
  variant?: "default" | "featured" | "compact";
}>();

const router = useRouter();
const { t } = useI18n();

// Computed properties
const contestType = computed(() => {
  const type = props.contest.contestType || "weekly";
  return t(`contest.types.${type}`, type);
});

const startTime = computed(() => props.contest.startTime || "");
const endTime = computed(() => props.contest.endTime || "");
const duration = computed(() => {
  if (startTime.value && endTime.value) {
    return getDurationMinutes(startTime.value, endTime.value);
  }
  return props.contest.duration || 0;
});

const participantCount = computed(() => props.contest.participantCount || 0);
const registeredCount = computed(() => props.contest.registeredCount || 0);
const isRated = computed(() => props.contest.isRated ?? false);

const contestStatus = computed(() => props.contest.status || "UPCOMING");

// Determine if contest is live
const isLive = computed(() => contestStatus.value === "RUNNING");

// Navigate to contest detail
function goToContest() {
  router.push({
    name: "contest-detail",
    params: { slug: props.contest.slug },
  });
}

// Card classes based on variant
const cardClasses = computed(() => {
  const base = "cursor-pointer transition-all";

  if (props.variant === "featured") {
    return `${base} hover:scale-[1.02] hover:shadow-xl border-0`;
  }

  if (props.variant === "compact") {
    return `${base} hover:shadow-md`;
  }

  return `${base} hover:shadow-lg`;
});
</script>

<template>
  <Card :class="cardClasses" @click="goToContest">
    <CardContent class="p-5">
      <div class="space-y-4">
        <!-- Header: Title and Status -->
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-1 min-w-0 flex-1">
            <p class="text-xs text-muted-foreground uppercase tracking-wide">
              {{ contestType }}
            </p>
            <h3 class="text-lg font-semibold leading-tight truncate">
              {{ contest.title }}
            </h3>
          </div>
          <ContestStatusBadge :status="contestStatus" size="sm" />
        </div>

        <!-- Info Row -->
        <div class="grid grid-cols-2 gap-3 text-sm text-muted-foreground">
          <div class="flex items-center gap-2">
            <Calendar class="h-4 w-4 shrink-0" />
            <span class="truncate">{{ formatDateTime(startTime) }}</span>
          </div>
          <div class="flex items-center gap-2">
            <Clock class="h-4 w-4 shrink-0" />
            <span>{{ duration }} {{ t("contest.time.min_short") }}</span>
          </div>
          <div class="flex items-center gap-2">
            <Users class="h-4 w-4 shrink-0" />
            <span>
              {{ registeredCount }} / {{ participantCount }}
              {{ t("contest.detail.participants") }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <Trophy class="h-4 w-4 shrink-0" />
            <span>
              {{ isRated ? t("common.labels.yes") : t("common.labels.no") }}
            </span>
          </div>
        </div>

        <!-- Timer for live/upcoming contests -->
        <div
          v-if="isLive || contestStatus === 'UPCOMING'"
          class="pt-2 border-t"
        >
          <ContestTimer
            :target-time="isLive ? endTime : startTime"
            :is-countdown-to-start="!isLive"
            show-icon
            size="sm"
          />
        </div>

        <!-- Action Button -->
        <div class="pt-2">
          <Button
            size="sm"
            variant="secondary"
            class="w-full"
            @click.stop="goToContest"
          >
            {{ t("contest.detail.enterContest") }}
          </Button>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
