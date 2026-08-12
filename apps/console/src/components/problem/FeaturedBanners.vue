<script setup lang="ts">
import { Card, CardContent } from "@/components/ui/card";
import type { ProblemList } from "@/types/problem-list";
import { computed, onMounted, onBeforeUnmount, ref, watch } from "vue";
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
} from "lucide-vue-next";
import type { LucideIcon } from "lucide-vue-next";
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
  "terminal-card relative overflow-hidden transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[var(--shadow-float-hover)] h-full rounded-lg group";
const CARD_CONTENT_BASE = "p-5 relative z-10 flex flex-col h-full";
const ICON_BASE =
  "w-12 h-12 rounded-md flex items-center justify-center transition-all duration-200 group-hover:scale-105 border";
const BADGE_BASE =
  "terminal-badge rounded-md text-2xs font-bold border uppercase tracking-widest";
const GLOW_BASE =
  "absolute rounded-full blur-3xl transition-all duration-500 group-hover:scale-110";

const SLATE_THEME: BannerTheme = {
  card: "hover:border-border-control",
  icon: "text-foreground-strong bg-surface-highlight border-border-control",
  badge: "bg-surface-highlight text-foreground-strong border-border-control",
  glow: "hidden",
  sparkle: "text-muted-foreground",
};

const THEME_MAP: Record<string, BannerTheme> = {
  amber: {
    card: "hover:border-border-control",
    icon: "text-status-warning-mark bg-surface-highlight border-border-control",
    badge: "bg-surface-highlight text-foreground-strong border-border-control",
    glow: "hidden",
    sparkle: "text-status-warning-mark",
  },
  sky: {
    card: "hover:border-border-control",
    icon: "text-status-info-mark bg-surface-highlight border-border-control",
    badge: "bg-surface-highlight text-foreground-strong border-border-control",
    glow: "hidden",
    sparkle: "text-status-info-mark",
  },
  emerald: {
    card: "hover:border-border-control",
    icon: "text-status-success-mark bg-surface-highlight border-border-control",
    badge: "bg-surface-highlight text-foreground-strong border-border-control",
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
  if (width >= 1024) {
    visibleCount.value = 3;
  } else if (width >= 640) {
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

// Check if we need scroll indicators (more than 3 items)
const needsScroll = computed(() => displayBanners.value.length > 3);

onMounted(async () => {
  await problemListsStore.loadOverview();

  // Set up scroll listeners after data loads
  if (scrollContainer.value) {
    scrollContainer.value.addEventListener("scroll", updateScrollState, {
      passive: true,
    });
    window.addEventListener("resize", updateVisibleCount, { passive: true });
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
  window.removeEventListener("resize", updateVisibleCount);
});
</script>

<template>
  <div class="relative group/carousel">
    <!-- Navigation Arrow Left -->
    <button
      v-show="needsScroll && showLeftArrow"
      @click="scrollByCard('left')"
      class="absolute left-0 top-1/2 -translate-y-1/2 z-20 w-10 h-10 hidden group-hover/carousel:flex group-focus-within/carousel:flex items-center justify-center rounded-md bg-surface-elevated border border-border-control shadow-float hover:bg-surface-highlight hover:border-primary transition-all duration-200 opacity-0 group-hover/carousel:opacity-100 group-focus-within/carousel:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <ChevronLeft class="w-5 h-5 text-foreground" />
    </button>

    <div
      ref="scrollContainer"
      class="flex gap-4 overflow-x-auto pb-4 scrollbar-thin scrollbar-thumb-muted-foreground/30 scrollbar-track-transparent snap-x snap-mandatory"
    >
      <template v-if="isLoading">
        <Card
          v-for="i in 3"
          :key="i"
          class="terminal-card flex-shrink-0 w-[calc(33.333%-1rem)] relative overflow-hidden h-[200px] rounded-lg"
        >
          <CardContent
            class="p-5 animate-pulse space-y-4 relative z-10 h-full flex flex-col"
          >
            <div class="flex items-start justify-between">
              <div
                class="h-12 w-12 rounded-md bg-surface-highlight border border-border-control/60"
              ></div>
              <div class="h-5 w-14 rounded-md bg-surface-highlight"></div>
            </div>
            <div class="space-y-3 flex-1">
              <div class="h-5 w-2/3 rounded-md bg-surface-highlight"></div>
              <div class="h-3 w-full rounded-md bg-surface-highlight"></div>
              <div class="h-3 w-4/5 rounded-md bg-surface-highlight"></div>
            </div>
            <div class="flex items-center justify-between pt-2">
              <div class="h-3 w-20 rounded-md bg-surface-highlight"></div>
              <div class="h-3 w-16 rounded-md bg-surface-highlight"></div>
            </div>
          </CardContent>
        </Card>
      </template>

      <template v-else-if="displayBanners.length === 0">
        <div
          class="terminal-card col-span-full flex-shrink-0 w-full flex flex-col items-center justify-center py-16 rounded-lg border-dashed text-center px-6"
        >
          <div
            class="flex h-14 w-14 items-center justify-center rounded-md bg-surface-highlight border border-border-control mb-4 shadow-float"
          >
            <Sparkles class="h-7 w-7 text-muted-foreground/60" />
          </div>
          <p class="text-base font-bold text-foreground tracking-tight">
            {{
              problemListsStore.loadError
                ? t("problem.banners.unableToLoad")
                : t("problem.banners.noBanners")
            }}
          </p>
          <p
            class="text-xs text-muted-foreground mt-2 max-w-[280px] leading-relaxed"
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
          class="group block flex-shrink-0 w-[calc(33.333%-1rem)] snap-start focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
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
              <div class="flex items-start justify-between mb-4">
                <div :class="[ICON_BASE, banner.theme.icon]">
                  <component :is="banner.icon" class="w-5 h-5" />
                </div>
                <div :class="[BADGE_BASE, banner.theme.badge]">
                  {{ banner.tag }}
                </div>
              </div>

              <div class="flex-1">
                <h3
                  class="font-bold text-lg mb-1 transition-colors group-hover:text-primary"
                >
                  {{ banner.name }}
                </h3>
                <p
                  class="text-xs text-muted-foreground line-clamp-2 leading-relaxed mb-4"
                >
                  {{ banner.summary }}
                </p>
              </div>

              <div class="flex items-center justify-between">
                <div
                  class="text-xs font-data font-medium text-muted-foreground flex items-center gap-1"
                >
                  <Sparkles class="w-3 h-3" :class="banner.theme.sparkle" />
                  <span class="tabular-nums">{{ banner.problemCount }}</span>
                  {{ t("problem.banners.questions") }}
                </div>
                <div
                  class="flex items-center gap-1 text-xs font-data font-semibold text-primary opacity-0 -translate-x-2 transition-all duration-300 group-hover:opacity-100 group-hover:translate-x-0"
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

    <!-- Navigation Arrow Right -->
    <button
      v-show="needsScroll && showRightArrow"
      @click="scrollByCard('right')"
      class="absolute right-0 top-1/2 -translate-y-1/2 z-20 w-10 h-10 hidden group-hover/carousel:flex group-focus-within/carousel:flex items-center justify-center rounded-md bg-surface-elevated border border-border-control shadow-float hover:bg-surface-highlight hover:border-primary transition-all duration-200 opacity-0 group-hover/carousel:opacity-100 group-focus-within/carousel:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <ChevronRight class="w-5 h-5 text-foreground" />
    </button>
  </div>
</template>
