<script setup lang="ts">
/**
 * ContestCard - Displays contest information in a card format
 *
 * Shows contest title, type, time, status, registration/participant count.
 * Links to contest detail page.
 */
import { computed } from "vue";
import { useRouter } from "vue-router";
import { Card, CardContent } from "@ulticode/design-system/ui/card";
import { Button } from "@ulticode/design-system/ui/button";
import { Calendar, Clock, Users, Trophy } from "@lucide/vue";
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
  const base = "h-full cursor-pointer transition-all";

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
    <CardContent class="flex flex-1 flex-col p-5">
      <div class="flex flex-1 flex-col gap-4">
        <!-- Header: Title and Status -->
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-1 min-w-0 flex-1">
            <p class="text-xs text-muted-foreground uppercase tracking-wide">
              {{ contestType }}
            </p>
            <h3 class="break-words text-lg font-semibold leading-tight">
              {{ contest.title }}
            </h3>
          </div>
          <ContestStatusBadge
            :status="contestStatus"
            size="sm"
            class="shrink-0"
          />
        </div>

        <!-- Info Row -->
        <div class="mt-auto grid grid-cols-2 gap-3 text-sm text-muted-foreground">
          <div class="col-span-2 flex min-w-0 items-center gap-2">
            <Calendar class="h-4 w-4 shrink-0" />
            <span class="break-words">{{ formatDateTime(startTime) }}</span>
          </div>
          <div class="flex items-center gap-2">
            <Clock class="h-4 w-4 shrink-0" />
            <span>{{ duration }} {{ t("contest.time.min_short") }}</span>
          </div>
          <div class="flex items-center gap-2">
            <Trophy class="h-4 w-4 shrink-0" />
            <span>
              {{
                isRated ? t("contest.types.rated") : t("contest.types.unrated")
              }}
            </span>
          </div>
          <div class="col-span-2 flex min-w-0 items-start gap-2">
            <Users class="h-4 w-4 shrink-0" />
            <span class="flex min-w-0 flex-wrap gap-x-3">
              <span>
                {{ t("contest.detail.registered") }} {{ registeredCount }}
              </span>
              <span>
                {{ t("contest.detail.participants") }} {{ participantCount }}
              </span>
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
