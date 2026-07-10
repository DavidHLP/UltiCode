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
import { formatDateTime, getDurationMinutes } from "@/shared/datetime-utils/src";
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
      <Button
        variant="outline"
        size="sm"
        class="h-8 rounded-none border border-border bg-transparent shadow-[2px_2px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 text-xs text-muted-foreground flex items-center gap-1.5 cursor-pointer font-mono"
      >
        <Trophy class="h-3.5 w-3.5 text-[var(--terminal-amber)]" />
        {{ t("contest.list.partner") }}
      </Button>
    </div>

    <!-- Contest List -->
    <div
      class="flex flex-col rounded-none border border-border bg-card text-card-foreground shadow-[3px_3px_0px_0px_var(--border)] overflow-hidden"
    >
      <!-- Table Column Headers (Hidden on Mobile) -->
      <div
        class="hidden sm:flex items-center gap-4 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-wider border-b border-border bg-muted/30 text-muted-foreground/80 rounded-none"
      >
        <div class="flex-1 text-left">Contest</div>
        <div class="w-32 text-center">Date</div>
        <div class="w-24 text-center">Duration</div>
        <div class="w-24 text-center">Status</div>
        <div class="w-28 text-right">Action</div>
      </div>

      <div
        v-for="contest in contests"
        :key="contest.id"
        class="group flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 transition-all hover:bg-muted/30 cursor-pointer border-b last:border-0"
        @click="
          router.push({
            name: 'contest-detail',
            params: { slug: contest.slug },
          })
        "
      >
        <div class="flex items-center gap-4 min-w-0 sm:flex-1">
          <!-- Icon Box -->
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-none border border-border bg-muted text-muted-foreground group-hover:bg-primary/10 group-hover:text-primary transition-all duration-300"
          >
            <Trophy class="h-5 w-5" />
          </div>

          <!-- Info -->
          <span
            class="truncate text-base font-bold leading-tight group-hover:text-primary transition-colors text-left"
          >
            {{ contest.title }}
          </span>
        </div>

        <!-- Meta row (responsive layout) -->
        <div
          class="flex flex-wrap sm:flex-nowrap items-center justify-between sm:justify-end gap-4 text-xs font-mono"
        >
          <!-- Date -->
          <span
            class="w-auto sm:w-32 text-left sm:text-center text-muted-foreground flex items-center gap-1.5"
          >
            <Calendar
              class="h-3.5 w-3.5 shrink-0 block sm:hidden text-muted-foreground/80"
            />
            {{ formatDateTime(contest.startTime).split(" ")[0] }}
          </span>

          <!-- Duration -->
          <span
            class="w-auto sm:w-24 text-left sm:text-center text-muted-foreground flex items-center gap-1.5"
          >
            <Clock
              class="h-3.5 w-3.5 shrink-0 block sm:hidden text-muted-foreground/80"
            />
            {{ getDurationMinutes(contest.startTime, contest.endTime) }}
            {{ t("contest.time.min_short") }}
          </span>

          <!-- Status -->
          <div class="w-auto sm:w-24 flex justify-start sm:justify-center">
            <span
              class="inline-flex items-center border border-border bg-muted/65 text-muted-foreground px-2 py-0.5 text-2xs font-bold uppercase tracking-wide rounded-none"
            >
              {{ t("contest.list.finished") }}
            </span>
          </div>

          <!-- Action -->
          <div class="w-full sm:w-28 flex justify-end">
            <Button
              variant="outline"
              size="sm"
              class="rounded-none border-border shadow-[2px_2px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 text-xs h-8 px-3"
              @click.stop="
                router.push({
                  name: 'contest-detail',
                  params: { slug: contest.slug },
                })
              "
            >
              {{ t("contest.types.virtual") }}
            </Button>
          </div>
        </div>
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
          variant="outline"
          size="icon"
          :disabled="page === '...' || loading"
          :aria-current="page === currentPage ? 'page' : undefined"
          :data-testid="
            page === currentPage ? 'past-contests-current-page' : undefined
          "
          class="h-9 w-9 rounded-none text-xs"
          :class="{
            'pointer-events-none': page === '...',
            'border-[var(--terminal-amber)]/35 bg-[oklch(0.6545_0.1340_85.7_/_0.12)] text-[var(--terminal-amber)] shadow-[2px_2px_0px_0px_var(--terminal-amber)] hover:bg-[oklch(0.6545_0.1340_85.7_/_0.2)] dark:border-[var(--terminal-amber)]/45 dark:bg-[oklch(0.6545_0.1340_85.7_/_0.18)] dark:text-[var(--terminal-amber)] dark:hover:bg-[oklch(0.6545_0.1340_85.7_/_0.25)]':
              page === currentPage,
          }"
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
