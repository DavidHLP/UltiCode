<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";

import { Card, CardContent } from "@ulticode/design-system/ui/card";
import type { ProblemList } from "@/types/problem-list";
import {
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from "vue";
import { useResizeObserver } from "@vueuse/core";
import { RouterLink } from "vue-router";
import {
  ArrowRight,
  Sparkles,
  Trophy,
  Code2,
  Database,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
} from "@lucide/vue";
import type { LucideIcon } from "@lucide/vue";
import { useI18n } from "vue-i18n";
import { useProblemListsStore } from "@/stores/problemLists";

type BannerTheme = {
  card: string;
  icon: string;
  badge: string;
  glow: string;
  sparkle: string;
};

type DisplayBanner = ProblemList & {
  tag: string;
  summary: string;
  icon: LucideIcon;
  theme: BannerTheme;
};

const { t } = useI18n();
const problemListsStore = useProblemListsStore();

const CARD_BASE =
  "relative h-full overflow-hidden rounded-lg border bg-card py-0 text-card-foreground transition-shadow duration-200 hover:shadow-[var(--shadow-float-hover)] group-focus-visible:ring-2 group-focus-visible:ring-inset group-focus-visible:ring-ring";
const CARD_CONTENT_BASE = "relative z-10 flex h-full flex-col p-3";
const ICON_BASE =
  "flex h-7 w-7 shrink-0 items-center justify-center rounded-md border";
const BADGE_BASE =
  "shrink-0 rounded-md border px-1.5 py-0.5 text-xs font-medium";
const GLOW_BASE =
  "absolute rounded-full blur-3xl transition-all duration-500 group-hover:scale-110";

const SLATE_THEME: BannerTheme = {
  card: "hover:border-input",
  icon: "text-foreground bg-accent border-input",
  badge: "bg-accent text-foreground border-input",
  glow: "hidden",
  sparkle: "text-muted-foreground",
};

const THEME_MAP: Record<string, BannerTheme> = {
  amber: {
    card: "hover:border-input",
    icon: "text-status-warning-mark bg-accent border-input",
    badge: "bg-accent text-foreground border-input",
    glow: "hidden",
    sparkle: "text-status-warning-mark",
  },
  sky: {
    card: "hover:border-input",
    icon: "text-status-info-mark bg-accent border-input",
    badge: "bg-accent text-foreground border-input",
    glow: "hidden",
    sparkle: "text-status-info-mark",
  },
  emerald: {
    card: "hover:border-input",
    icon: "text-status-success-mark bg-accent border-input",
    badge: "bg-accent text-foreground border-input",
    glow: "hidden",
    sparkle: "text-status-success-mark",
  },
  slate: SLATE_THEME,
};

const DEFAULT_THEME = SLATE_THEME;

const ICON_MAP: Record<string, LucideIcon> = {
  Trophy,
  Code2,
  Database,
  ArrowUpDown,
  Sparkles,
};

const resolveTheme = (key?: string): BannerTheme =>
  (key ? THEME_MAP[key] : undefined) ?? DEFAULT_THEME;

const resolveIcon = (key?: string): LucideIcon =>
  (key ? ICON_MAP[key] : undefined) ?? Sparkles;

const banners = ref<ProblemList[]>([]);
const isLoading = ref(true);
const scrollContainer = ref<HTMLElement | null>(null);

// Current scroll position tracking
const currentIndex = ref(0);
const visibleCount = ref(3);
const bannerStyle = computed(() => ({
  width: `calc((100% - ${visibleCount.value - 1}rem) / ${visibleCount.value})`,
}));

const updateScrollState = () => {
  if (!scrollContainer.value) return;

  const containerWidth = scrollContainer.value.clientWidth;
  const scrollLeft = scrollContainer.value.scrollLeft;
  const cardWidth = containerWidth / visibleCount.value;

  currentIndex.value = Math.round(scrollLeft / cardWidth);

  // Use a small buffer for right arrow visibility
  const maxScroll = scrollContainer.value.scrollWidth - containerWidth;
  const atEnd = scrollLeft >= maxScroll - 5;
  const canScroll = maxScroll > 0;

  // Update button visibility
  showLeftArrow.value = currentIndex.value > 0;
  showRightArrow.value = canScroll && !atEnd;
};

const showLeftArrow = ref(false);
const showRightArrow = ref(false);

const scrollByCard = (direction: "left" | "right") => {
  if (!scrollContainer.value) return;

  const containerWidth = scrollContainer.value.clientWidth;
  const cardWidth = containerWidth / visibleCount.value;
  const scrollAmount = direction === "left" ? -cardWidth : cardWidth;

  scrollContainer.value.scrollBy({
    left: scrollAmount,
    behavior: "smooth",
  });
};

// Handle responsive visible count
const updateVisibleCount = () => {
  if (!scrollContainer.value) return;
  const width = scrollContainer.value.clientWidth;
  if (width >= 800) {
    visibleCount.value = 3;
  } else if (width >= 520) {
    visibleCount.value = 2;
  } else {
    visibleCount.value = 1;
  }
};

const sortedBanners = computed(() => {
  return [...banners.value].sort((a, b) => {
    const orderA = a.bannerOrder ?? 0;
    const orderB = b.bannerOrder ?? 0;
    if (orderA !== orderB) return orderA - orderB;
    const dateA = a.updatedAt ? Date.parse(a.updatedAt) : 0;
    const dateB = b.updatedAt ? Date.parse(b.updatedAt) : 0;
    return dateB - dateA;
  });
});

const displayBanners = computed<DisplayBanner[]>(() =>
  sortedBanners.value.map((banner) => ({
    ...banner,
    tag: banner.bannerTag ?? t("problem.banners.featuredDefault"),
    summary: banner.description ?? t("problem.banners.curatedDefault"),
    icon: resolveIcon(banner.bannerIcon),
    theme: resolveTheme(banner.bannerTheme),
  })),
);

const needsScroll = computed(
  () => displayBanners.value.length > visibleCount.value,
);

useResizeObserver(scrollContainer, async () => {
  updateVisibleCount();
  await nextTick();
  updateScrollState();
});

onMounted(async () => {
  await problemListsStore.loadOverview();

  // Set up scroll listeners after data loads
  if (scrollContainer.value) {
    scrollContainer.value.addEventListener("scroll", updateScrollState, {
      passive: true,
    });
    updateVisibleCount();
    updateScrollState();
  }
});

watch(
  () => problemListsStore.isLoading,
  (loading) => {
    isLoading.value = loading;
  },
  { immediate: true },
);

watch(
  () => problemListsStore.featuredLists,
  (lists) => {
    banners.value = [...lists];
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  if (scrollContainer.value) {
    scrollContainer.value.removeEventListener("scroll", updateScrollState);
  }
});
</script>

<template>
  <section
    class="min-w-0 space-y-3"
    :aria-label="t('problem.banners.collections')"
  >
    <div class="flex h-8 items-center justify-between gap-3">
      <h2 class="flex items-center gap-2 text-sm font-semibold">
        {{ t("problem.banners.collections") }}
        <span
          v-if="displayBanners.length"
          class="text-xs font-normal tabular-nums text-muted-foreground"
        >
          {{ displayBanners.length }}
        </span>
      </h2>
      <div v-if="needsScroll" class="flex items-center gap-1">
        <Button
          variant="outline"
          size="icon"
          class="h-8 w-8"
          :disabled="!showLeftArrow"
          :aria-label="t('problem.banners.previousCollections')"
          @click="scrollByCard('left')"
        >
          <ChevronLeft class="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          class="h-8 w-8"
          :disabled="!showRightArrow"
          :aria-label="t('problem.banners.nextCollections')"
          @click="scrollByCard('right')"
        >
          <ChevronRight class="h-4 w-4" />
        </Button>
      </div>
    </div>

    <div
      ref="scrollContainer"
      tabindex="0"
      role="region"
      :aria-label="t('problem.banners.collections')"
      class="collections-scroll flex snap-x snap-mandatory gap-4 overflow-x-auto rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
    >
      <template v-if="isLoading">
        <Card
          v-for="i in 3"
          :key="i"
          class="relative h-32 shrink-0 overflow-hidden py-0"
          :style="bannerStyle"
        >
          <CardContent
            class="relative z-10 flex h-full animate-pulse flex-col gap-2 p-3"
          >
            <div class="flex items-start justify-between">
              <div
                class="h-7 w-7 rounded-md border border-input/60 bg-accent"
              ></div>
              <div class="h-5 w-14 rounded-md bg-accent"></div>
            </div>
            <div class="flex-1 space-y-1">
              <div class="h-4 w-2/3 rounded-md bg-accent"></div>
              <div class="h-3 w-full rounded-md bg-accent"></div>
            </div>
            <div class="flex items-center justify-between">
              <div class="h-3 w-20 rounded-md bg-accent"></div>
              <div class="h-3 w-16 rounded-md bg-accent"></div>
            </div>
          </CardContent>
        </Card>
      </template>

      <template v-else-if="displayBanners.length === 0">
        <div
          class="flex h-32 w-full shrink-0 flex-col items-center justify-center rounded-lg border border-dashed bg-card px-6 text-center text-card-foreground"
        >
          <div
            class="mb-2 flex h-7 w-7 items-center justify-center rounded-md border border-input bg-accent"
          >
            <Sparkles class="h-4 w-4 text-muted-foreground/60" />
          </div>
          <p class="text-sm font-semibold text-foreground">
            {{
              problemListsStore.loadError
                ? t("problem.banners.unableToLoad")
                : t("problem.banners.noBanners")
            }}
          </p>
          <p
            class="mt-1 max-w-[280px] text-xs leading-relaxed text-muted-foreground"
          >
            {{
              problemListsStore.loadError
                ? t("problem.banners.tryAgain")
                : t("problem.banners.featuredListsConfigured")
            }}
          </p>
        </div>
      </template>

      <template v-else>
        <RouterLink
          v-for="banner in displayBanners"
          :key="banner.id"
          :to="`/problemset/list/${banner.id}`"
          class="group block h-32 shrink-0 snap-start rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
          :style="bannerStyle"
        >
          <Card :class="[CARD_BASE, banner.theme.card]">
            <div
              class="-right-8 -top-8 h-48 w-48 group-hover:scale-125 opacity-60 group-hover:opacity-100"
              :class="[GLOW_BASE, banner.theme.glow]"
            ></div>
            <div
              class="-left-12 -bottom-12 h-40 w-40 group-hover:scale-110 opacity-40 group-hover:opacity-70"
              :class="[GLOW_BASE, banner.theme.glow]"
            ></div>

            <CardContent :class="CARD_CONTENT_BASE">
              <div class="flex items-center gap-2">
                <div :class="[ICON_BASE, banner.theme.icon]">
                  <component :is="banner.icon" class="h-4 w-4" />
                </div>
                <h3
                  class="min-w-0 flex-1 truncate text-sm font-semibold transition-colors group-hover:text-primary"
                  :title="banner.name"
                >
                  {{ banner.name }}
                </h3>
                <div :class="[BADGE_BASE, banner.theme.badge]">
                  {{ banner.tag }}
                </div>
              </div>

              <p
                class="mt-2 line-clamp-2 flex-1 text-xs leading-4 text-muted-foreground"
              >
                {{ banner.summary }}
              </p>

              <div class="mt-2 flex items-center justify-between gap-2">
                <div
                  class="flex items-center gap-1 text-xs text-muted-foreground"
                >
                  <Sparkles class="w-3 h-3" :class="banner.theme.sparkle" />
                  <span class="tabular-nums">{{ banner.problemCount }}</span>
                  {{ t("problem.banners.questions") }}
                </div>
                <div
                  class="flex items-center gap-1 text-xs font-medium text-primary"
                >
                  {{ t("problem.banners.viewList") }}
                  <ArrowRight class="w-3.5 h-3.5" />
                </div>
              </div>
            </CardContent>
          </Card>
        </RouterLink>
      </template>
    </div>
  </section>
</template>

<style scoped>
.collections-scroll {
  scrollbar-width: none;
}

.collections-scroll::-webkit-scrollbar {
  display: none;
}
</style>
