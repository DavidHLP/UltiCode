<script setup lang="ts">
import { computed } from "vue";
import { Button } from "@/components/ui/button";
import {
  ChevronLeft,
  ChevronRight,
  Trophy,
  Calendar,
  Clock,
} from "lucide-vue-next";
import { useRouter } from "vue-router";
import type { ContestListItem } from "@/types/contest";
import { formatDateTime, getDurationMinutes } from "@/utils/date";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  contests: ContestListItem[];
  loading: boolean;
  currentPage: number;
  totalPages: number;
}>();

const emit = defineEmits<{
  "update:currentPage": [page: number];
}>();

const router = useRouter();
const { t } = useI18n();

// Simple pagination logic
const visiblePages = computed(() => {
  const pages: (number | string)[] = [];
  const total = props.totalPages;
  const current = props.currentPage;

  if (total <= 7) {
    for (let i = 1; i <= total; i++) pages.push(i);
  } else {
    pages.push(1);
    if (current > 3) pages.push("...");

    let start = Math.max(2, current - 1);
    let end = Math.min(total - 1, current + 1);

    if (current < 3) end = 4;
    if (current > total - 2) start = total - 3;

    for (let i = start; i <= end; i++) pages.push(i);

    if (current < total - 2) pages.push("...");
    pages.push(total);
  }
  return pages;
});
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-end justify-between">
      <div class="space-y-1">
        <h2 class="text-2xl font-bold tracking-tight">
          {{ t("contest.list.past") }}
        </h2>
        <p class="text-sm text-muted-foreground">
          {{ t("contest.list.pastSubtitle") }}
        </p>
      </div>
      <div
        class="flex items-center gap-1.5 rounded-full bg-yellow-50 px-3 py-1 text-xs font-semibold text-yellow-600 transition-colors hover:bg-yellow-100 cursor-pointer"
      >
        <Trophy class="h-3.5 w-3.5" />
        {{ t("contest.list.partner") }}
      </div>
    </div>

    <!-- Contest List -->
    <div
      class="flex flex-col rounded-xl border bg-card text-card-foreground shadow-sm overflow-hidden"
    >
      <div
        v-for="contest in contests"
        :key="contest.id"
        class="group flex items-center justify-between gap-4 p-4 transition-all hover:bg-muted/40 cursor-pointer border-b last:border-0"
        @click="
          router.push({
            name: 'contest-detail',
            params: { contestId: contest.id },
          })
        "
      >
        <div class="flex items-center gap-4 min-w-0">
          <!-- Icon Box -->
          <div
            class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary group-hover:scale-110 group-hover:shadow-md transition-all duration-300"
          >
            <Trophy class="h-6 w-6" />
          </div>

          <!-- Info -->
          <div class="flex flex-col gap-1 min-w-0">
            <span
              class="truncate text-base font-bold leading-none group-hover:text-primary transition-colors"
            >
              {{ contest.title }}
            </span>
            <div
              class="flex items-center gap-4 text-xs font-medium text-muted-foreground"
            >
              <span
                class="flex items-center gap-1.5 bg-muted/50 px-2 py-0.5 rounded-md"
              >
                <Calendar class="h-3.5 w-3.5" />
                {{ formatDateTime(contest.start_time).split(" ")[0] }}
              </span>
              <span
                class="flex items-center gap-1.5 bg-muted/50 px-2 py-0.5 rounded-md"
              >
                <Clock class="h-3.5 w-3.5" />
                {{ getDurationMinutes(contest.start_time, contest.end_time) }}
                {{ t("contest.time.min_short") }}
              </span>
            </div>
          </div>
        </div>

        <!-- Action -->
        <Button
          variant="secondary"
          size="sm"
          class="h-8 px-4 text-xs font-bold shadow-sm opacity-0 -translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-300"
          @click.stop
        >
          {{ t("contest.types.virtual") }}
        </Button>
      </div>
    </div>

    <!-- Loading State -->
    <div
      v-if="loading && contests.length === 0"
      class="py-10 text-center text-muted-foreground"
    >
      {{ t("common.status.loading") }}
    </div>

    <!-- Pagination -->
    <div class="flex items-center justify-center gap-2 pt-4">
      <Button
        variant="outline"
        size="icon"
        class="h-9 w-9"
        :disabled="currentPage === 1 || loading"
        @click="emit('update:currentPage', currentPage - 1)"
      >
        <ChevronLeft class="h-4 w-4" />
      </Button>

      <div class="flex gap-1.5">
        <Button
          v-for="page in visiblePages"
          :key="page"
          :variant="page === currentPage ? 'default' : 'outline'"
          size="icon"
          :disabled="page === '...' || loading"
          class="h-9 w-9 text-xs"
          :class="{ 'pointer-events-none': page === '...' }"
          @click="typeof page === 'number' && emit('update:currentPage', page)"
        >
          {{ page }}
        </Button>
      </div>

      <Button
        variant="outline"
        size="icon"
        class="h-9 w-9"
        :disabled="currentPage === totalPages || loading"
        @click="emit('update:currentPage', currentPage + 1)"
      >
        <ChevronRight class="h-4 w-4" />
      </Button>
    </div>
  </div>
</template>
