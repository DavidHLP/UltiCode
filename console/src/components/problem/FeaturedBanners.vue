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
import { useAuthStore } from "@/stores/auth";

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
const authStore = useAuthStore();

const CARD_BASE =
  "relative overflow-hidden border transition-all duration-300 hover:-translate-y-0.5 h-full rounded-none";
const CARD_CONTENT_BASE = "p-5 relative z-10 flex flex-col h-full";
const ICON_BASE =
  "w-10 h-10 rounded-none flex items-center justify-center transition-transform duration-300 group-hover:scale-110 group-hover:-rotate-3";
const BADGE_BASE =
  "px-2 py-0.5 rounded-none text-[10px] font-bold border backdrop-blur uppercase tracking-widest";
const GLOW_BASE =
  "absolute rounded-full blur-3xl transition-transform duration-300";

const SLATE_THEME: BannerTheme = {
  card: "bg-muted/30 border-border/40",
  icon: "text-muted-foreground bg-muted/50",
  badge:
    "bg-muted/70 text-muted-foreground border-border/60",
  glow: "bg-muted/20",
  sparkle: "text-muted-foreground",
};

const THEME_MAP: Record<string, BannerTheme> = {
  amber: {
    card: "bg-[var(--terminal-amber)]/10 border-[var(--terminal-amber)]/30",
    icon: "text-[var(--terminal-amber)] bg-[var(--terminal-amber)]/15",
    badge:
      "bg-[var(--terminal-amber)]/10 text-[var(--terminal-amber)] border-[var(--terminal-amber)]/30",
    glow: "bg-[var(--terminal-amber)]/20",
    sparkle: "text-[var(--terminal-amber)]",
  },
  sky: {
    card: "bg-[var(--accent-electric)]/10 border-[var(--accent-electric)]/30",
    icon: "text-[var(--accent-electric)] bg-[var(--accent-electric)]/15",
    badge:
      "bg-[var(--accent-electric)]/10 text-[var(--accent-electric)] border-[var(--accent-electric)]/30",
    glow: "bg-[var(--accent-electric)]/20",
    sparkle: "text-[var(--accent-electric)]",
  },
  emerald: {
    card: "bg-[var(--terminal-green)]/10 border-[var(--terminal-green)]/30",
    icon: "text-[var(--terminal-green)] bg-[var(--terminal-green)]/15",
    badge:
      "bg-[var(--terminal-green)]/10 text-[var(--terminal-green)] border-[var(--terminal-green)]/30",
    glow: "bg-[var(--terminal-green)]/20",
    sparkle: "text-[var(--terminal-green)]",
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

    // 只在已认证时获取精选列表，避免访客用户出现 401 错误
    if (authStore.isAuthenticated) {
      banners.value = await fetchFeaturedProblemLists();
    } else {
      // 访客用户显示空状态
      banners.value = [];
    }
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
        class="relative overflow-hidden border bg-muted/30 h-full rounded-none"
      >
        <CardContent class="p-5 animate-pulse space-y-4">
          <div class="flex items-start justify-between">
            <div class="h-10 w-10 rounded-none bg-muted/70"></div>
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
        class="col-span-full flex flex-col items-center justify-center py-24 rounded-none border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6"
      >
        <div
          class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
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
