<script setup lang="ts">
import { computed } from "vue";
import { Button } from "@ulticode/design-system/ui/button";
import {
  ChevronLeft,
  ChevronRight,
  Trophy,
  Calendar,
  Clock,
} from "@lucide/vue";
import { useRouter } from "vue-router";
import type { ContestListItem } from "@/types/contest";
import { formatDate, getDurationMinutes } from "@/utils/datetime";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  contests: ContestListItem[];
  loading: boolean;
  currentPage: number;
  totalPages: number;
  error?: string | null;
}>();

const emit = defineEmits<{
  "update:currentPage": [page: number];
  retry: [];
}>();

const router = useRouter();
const { t } = useI18n();

// Simple pagination logic
const visiblePages = computed(() => {
  const pages: (number | string)[] = [];
  const total = Math.max(1, props.totalPages);
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
  <div class="@container/past min-w-0 space-y-6">
    <!-- Header -->
    <div class="flex flex-wrap items-end justify-between gap-4">
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
        class="active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 flex items-center gap-1.5 cursor-pointer"
      >
        <Trophy class="h-3.5 w-3.5 text-status-warning-mark" />
        {{ t("contest.list.partner") }}
      </Button>
    </div>
    <div
      v-if="error"
      role="alert"
      class="flex items-center justify-between gap-4 border border-status-error-mark bg-status-error-surface px-4 py-3 text-sm text-foreground"
    >
      <p>{{ error }}</p>
      <Button type="button" variant="outline" size="sm" @click="emit('retry')">
        {{ t("common.actions.retry") }}
      </Button>
    </div>

    <!-- Contest List -->
    <div
      class="flex flex-col rounded-lg border border-border bg-card text-card-foreground shadow-[3px_3px_0px_0px_var(--border)] overflow-hidden"
    >
      <!-- Table Column Headers (Hidden on Mobile) -->
      <div
        class="hidden @3xl/past:flex items-center gap-4 px-4 py-2.5 text-xs font-mono font-bold uppercase tracking-wider border-b border-border bg-muted/30 text-muted-foreground/80 rounded-lg"
      >
        <div class="flex-1 text-left">
          {{ t("contest.list.tableHeaders.contest") }}
        </div>
        <div class="w-32 text-center">
          {{ t("contest.list.tableHeaders.date") }}
        </div>
        <div class="w-24 text-center">
          {{ t("contest.list.tableHeaders.duration") }}
        </div>
        <div class="w-24 text-center">
          {{ t("contest.list.tableHeaders.status") }}
        </div>
        <div class="w-40 text-right">
          {{ t("contest.list.tableHeaders.action") }}
        </div>
      </div>

      <div
        v-for="contest in contests"
        :key="contest.id"
        class="group flex flex-col @3xl/past:flex-row @3xl/past:items-center justify-between gap-4 p-4 transition-all hover:bg-muted/30 cursor-pointer border-b last:border-0"
        @click="
          router.push({
            name: 'contest-detail',
            params: { slug: contest.slug },
          })
        "
      >
        <div class="flex items-center gap-4 min-w-0 @3xl/past:flex-1">
          <!-- Icon Box -->
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-border bg-muted text-muted-foreground group-hover:bg-primary/10 group-hover:text-primary transition-all duration-300"
          >
            <Trophy class="h-5 w-5" />
          </div>

          <!-- Info -->
          <span
            class="min-w-0 break-words text-base font-bold leading-tight group-hover:text-primary transition-colors text-left"
          >
            {{ contest.title }}
          </span>
        </div>

        <!-- Meta row (responsive layout) -->
        <div
          class="flex flex-wrap @3xl/past:flex-nowrap @3xl/past:shrink-0 items-center justify-between @3xl/past:justify-end gap-4 text-xs font-mono"
        >
          <!-- Date -->
          <span
            class="w-auto @3xl/past:w-32 text-left @3xl/past:text-center text-muted-foreground flex items-center gap-1.5"
          >
            <Calendar
              class="h-3.5 w-3.5 shrink-0 block @3xl/past:hidden text-muted-foreground/80"
            />
            {{ formatDate(contest.startTime) }}
          </span>

          <!-- Duration -->
          <span
            class="w-auto @3xl/past:w-24 text-left @3xl/past:text-center text-muted-foreground flex items-center gap-1.5"
          >
            <Clock
              class="h-3.5 w-3.5 shrink-0 block @3xl/past:hidden text-muted-foreground/80"
            />
            {{ getDurationMinutes(contest.startTime, contest.endTime) }}
            {{ t("contest.time.min_short") }}
          </span>

          <!-- Status -->
          <div
            class="w-auto @3xl/past:w-24 flex justify-start @3xl/past:justify-center"
          >
            <span
              class="inline-flex items-center border border-border bg-muted/65 text-muted-foreground px-2 py-0.5 text-xs font-bold uppercase tracking-wide rounded-lg"
            >
              {{ t("contest.list.finished") }}
            </span>
          </div>

          <!-- Action -->
          <div class="w-full @3xl/past:w-40 flex justify-end">
            <Button
              variant="outline"
              size="sm"
              class="active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 px-3"
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
    <div
      class="mx-auto grid w-fit max-w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2 pt-4"
    >
      <Button
        variant="outline"
        size="icon"
        class="w-9"
        :aria-label="t('common.pagination.previousPage')"
        :disabled="currentPage <= 1 || loading"
        @click="emit('update:currentPage', currentPage - 1)"
      >
        <ChevronLeft class="h-4 w-4" />
      </Button>

      <div class="flex flex-wrap justify-center gap-1.5">
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
          class="w-9"
          :class="{
            'pointer-events-none': page === '...',
            'border-status-warning-mark bg-status-warning-surface text-foreground shadow-[2px_2px_0px_0px_var(--status-warning-mark)] hover:bg-status-warning-surface/80':
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
        class="w-9"
        :aria-label="t('common.pagination.nextPage')"
        :disabled="currentPage >= Math.max(1, totalPages) || loading"
        @click="emit('update:currentPage', currentPage + 1)"
      >
        <ChevronRight class="h-4 w-4" />
      </Button>
    </div>
  </div>
</template>
