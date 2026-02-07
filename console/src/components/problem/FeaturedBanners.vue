<script setup lang="ts">
import { Card, CardContent } from "@/components/ui/card";
import { fetchFeaturedProblemLists } from "@/api/problem-list";
import type { ProblemList } from "@/types/problem-list";
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  ArrowRight,
  Sparkles,
  Trophy,
  Code2,
  Database,
  ArrowUpDown,
} from "lucide-vue-next";
import type { LucideIcon } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

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

const CARD_BASE =
  "relative overflow-hidden border shadow-sm transition-all duration-300 hover:shadow-md hover:-translate-y-0.5 h-full rounded-2xl";
const CARD_CONTENT_BASE = "p-5 relative z-10 flex flex-col h-full";
const ICON_BASE =
  "w-10 h-10 rounded-2xl flex items-center justify-center shadow-sm transition-transform duration-300 group-hover:scale-110 group-hover:-rotate-3";
const BADGE_BASE =
  "px-2 py-0.5 rounded-md text-[10px] font-bold border shadow-sm backdrop-blur uppercase tracking-widest";
const GLOW_BASE =
  "absolute rounded-full blur-3xl transition-transform duration-300";

const SLATE_THEME: BannerTheme = {
  card: "bg-gradient-to-br from-slate-500/10 via-slate-500/5 to-transparent border-slate-200/40 dark:border-slate-800/40",
  icon: "text-slate-700 dark:text-slate-300 bg-slate-100/80 dark:bg-slate-900/30",
  badge:
    "bg-slate-50/70 text-slate-700 border-slate-200/60 dark:bg-slate-900/20 dark:text-slate-200 dark:border-slate-800/60",
  glow: "bg-slate-500/20",
  sparkle: "text-slate-500",
};

const THEME_MAP: Record<string, BannerTheme> = {
  amber: {
    card: "bg-gradient-to-br from-amber-500/10 via-orange-500/5 to-transparent border-amber-200/40 dark:border-amber-900/40",
    icon: "text-amber-700 dark:text-amber-300 bg-amber-100/80 dark:bg-amber-900/30",
    badge:
      "bg-amber-50/70 text-amber-700 border-amber-200/60 dark:bg-amber-900/20 dark:text-amber-200 dark:border-amber-800/60",
    glow: "bg-amber-500/20",
    sparkle: "text-amber-500",
  },
  sky: {
    card: "bg-gradient-to-br from-sky-500/10 via-indigo-500/5 to-transparent border-sky-200/40 dark:border-sky-900/40",
    icon: "text-sky-700 dark:text-sky-300 bg-sky-100/80 dark:bg-sky-900/30",
    badge:
      "bg-sky-50/70 text-sky-700 border-sky-200/60 dark:bg-sky-900/20 dark:text-sky-200 dark:border-sky-800/60",
    glow: "bg-sky-500/20",
    sparkle: "text-sky-500",
  },
  emerald: {
    card: "bg-gradient-to-br from-emerald-500/10 via-teal-500/5 to-transparent border-emerald-200/40 dark:border-emerald-900/40",
    icon: "text-emerald-700 dark:text-emerald-300 bg-emerald-100/80 dark:bg-emerald-900/30",
    badge:
      "bg-emerald-50/70 text-emerald-700 border-emerald-200/60 dark:bg-emerald-900/20 dark:text-emerald-200 dark:border-emerald-800/60",
    glow: "bg-emerald-500/20",
    sparkle: "text-emerald-500",
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
const hasError = ref(false);

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

onMounted(async () => {
  try {
    isLoading.value = true;
    hasError.value = false;
    banners.value = await fetchFeaturedProblemLists();
  } catch (error) {
    console.error("Failed to load featured lists", error);
    hasError.value = true;
    banners.value = [];
  } finally {
    isLoading.value = false;
  }
});
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
    <template v-if="isLoading">
      <Card
        v-for="i in 3"
        :key="i"
        class="relative overflow-hidden border bg-muted/30 h-full rounded-2xl"
      >
        <CardContent class="p-5 animate-pulse space-y-4">
          <div class="flex items-start justify-between">
            <div class="h-10 w-10 rounded-2xl bg-muted/70"></div>
            <div class="h-4 w-16 rounded-full bg-muted/60"></div>
          </div>
          <div class="space-y-2">
            <div class="h-5 w-2/3 rounded bg-muted/70"></div>
            <div class="h-3 w-full rounded bg-muted/60"></div>
            <div class="h-3 w-4/5 rounded bg-muted/60"></div>
          </div>
          <div class="flex items-center justify-between">
            <div class="h-3 w-20 rounded bg-muted/60"></div>
            <div class="h-3 w-16 rounded bg-muted/60"></div>
          </div>
        </CardContent>
      </Card>
    </template>

    <template v-else-if="displayBanners.length === 0">
      <div
        class="col-span-full flex flex-col items-center justify-center py-24 rounded-2xl border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6"
      >
        <div
          class="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/50 mb-4"
        >
          <Sparkles class="h-8 w-8 text-muted-foreground/50" />
        </div>
        <p class="text-xl font-bold text-foreground">
          {{
            hasError
              ? t("problem.banners.unableToLoad")
              : t("problem.banners.noBanners")
          }}
        </p>
        <p class="text-sm text-muted-foreground mt-2 max-w-[300px]">
          {{
            hasError
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
        class="group block focus-visible:outline-none h-full"
      >
        <Card :class="[CARD_BASE, banner.theme.card]">
          <div
            class="-right-10 -top-10 h-40 w-40 group-hover:scale-110"
            :class="[GLOW_BASE, banner.theme.glow]"
          ></div>
          <div
            class="-left-10 -bottom-10 h-32 w-32 group-hover:scale-105"
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
                class="text-xs font-medium text-muted-foreground flex items-center gap-1"
              >
                <Sparkles class="w-3 h-3" :class="banner.theme.sparkle" />
                {{ banner.problemCount }} {{ t("problem.banners.questions") }}
              </div>
              <div
                class="flex items-center gap-1 text-xs font-semibold text-primary opacity-0 -translate-x-2 transition-all duration-300 group-hover:opacity-100 group-hover:translate-x-0"
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
</template>
