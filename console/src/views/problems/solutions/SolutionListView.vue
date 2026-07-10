<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { computed, ref, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import type { SolutionFeedItem } from "@/types/solution";
import type { SubmissionRecord } from "@/types/submission";
import { fetchBestSubmission } from "@/api/submission";
import { fetchUserSolutions } from "@/api/solution";
import { toast } from "vue-sonner";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import SolutionCard from "./components/SolutionCard.vue";
import Separator from "@/components/ui/separator";
import Badge from "@/components/ui/badge/Badge.vue";
import {
  Search,
  PenLine,
  ArrowDownAZ,
  Check,
  Lightbulb,
} from "lucide-vue-next";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
} from "@/components/ui/empty";
import { useI18n } from "vue-i18n";
import { useErrorHandler } from "@/composables/useErrorHandler";

const props = defineProps<{
  problemId?: number;
  items: SolutionFeedItem[];
  followUp: string;
  sortOptions: Array<{ label: string; value: string }>;
}>();

const emit = defineEmits<{
  select: [item: SolutionFeedItem];
}>();

const router = useRouter();
const route = useRoute();
const { t } = useI18n();
const { handleError } = useErrorHandler();

const search = ref("");
const languageFilter = ref("all");
const sortBy = ref("likes");
const bestSubmission = ref<SubmissionRecord | null>(null);
const userSolution = ref<SolutionFeedItem | null>(null);

const sortOptions = computed(() =>
  props.sortOptions.length
    ? props.sortOptions
    : [
        { label: t("problem.solutions.mostLiked"), value: "likes" },
        { label: t("problem.solutions.mostRecent"), value: "newest" },
      ],
);

const feedItems = computed<SolutionFeedItem[]>(() => props.items ?? []);

// 从题解数据中实时提取语言选项
const languageOptions = computed(() => {
  const languages = new Set<string>();
  feedItems.value.forEach((item) => {
    if (item.language) {
      languages.add(item.language);
    }
  });

  return Array.from(languages)
    .sort()
    .map((lang) => ({
      label: lang,
      value: lang.toLowerCase(),
    }));
});

const filteredItems = computed(() => {
  const query = search.value.trim().toLowerCase();
  return feedItems.value.filter((item) => {
    const matchesLanguage =
      languageFilter.value === "all" ||
      item.languageFilter === languageFilter.value ||
      item.language.toLowerCase() === languageFilter.value;
    const matchesQuery =
      !query ||
      [item.highlight, item.author.name, item.content]
        .join(" ")
        .toLowerCase()
        .includes(query);
    return matchesLanguage && matchesQuery;
  });
});

const sortedItems = computed(() => {
  const items = [...filteredItems.value];
  switch (sortBy.value) {
    case "likes":
      return items.sort(
        (a, b) => (b.stats?.likes ?? 0) - (a.stats?.likes ?? 0),
      );
    case "heat":
      return items.sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
    case "newest":
      return items.sort(
        (a, b) =>
          new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime(),
      );
    case "oldest":
      return items.sort(
        (a, b) =>
          new Date(a.publishedAt).getTime() - new Date(b.publishedAt).getTime(),
      );
    default:
      return items;
  }
});

const handleSelect = (item: SolutionFeedItem) => {
  emit("select", item);
};

const handleCreateSolution = () => {
  const problemId = props.problemId?.toString() || route.params.id || "1";
  if (userSolution.value) {
    router.push({
      name: "solution-edit",
      params: { id: userSolution.value.id },
    });
    return;
  }
  if (!bestSubmission.value) {
    toast.error(t("solution.messages.acceptedRequired"));
    return;
  }
  router.push({
    name: "solution-create",
    params: { id: problemId },
    query: { submissionId: bestSubmission.value.id },
  });
};

onMounted(async () => {
  const problemId = props.problemId?.toString() || (route.params.id as string);
  if (problemId) {
    try {
      bestSubmission.value = await fetchBestSubmission(problemId);
    } catch (e) {
      handleError(e, {
        fallbackMessage: "problem.solutions.error.bestSubmissionLoadFailed",
        logToConsole: true,
        resetState: () => {
          bestSubmission.value = null;
        },
      });
    }
    try {
      const userId = useAuthStore().fetchCurrentUserId();
      if (userId) {
        const response = await fetchUserSolutions(userId, problemId);
        userSolution.value = response.items[0] ?? null;
      }
    } catch (e) {
      handleError(e, {
        fallbackMessage: "problem.solutions.error.userSolutionsLoadFailed",
        logToConsole: true,
        resetState: () => {
          userSolution.value = null;
        },
      });
    }
  }
});
</script>

<template>
  <section
    class="terminal-card relative flex h-full w-full flex-col overflow-hidden shadow-sm"
  >
    <!-- Header 区域 -->
    <header
      class="terminal-card-header flex flex-col gap-0 px-3 pb-2 pt-2 font-data"
    >
      <!-- 顶部搜索和排序栏 -->
      <div class="flex items-center gap-2">
        <!-- 搜索框 -->
        <div class="relative flex-1">
          <Search
            class="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground"
          />
          <Input
            v-model="search"
            :placeholder="t('problem.solutions.searchPlaceholder')"
            class="h-8 pl-8.5 text-xs rounded-none bg-card border-border text-foreground placeholder:text-muted-foreground focus-visible:border-[var(--accent-electric)] focus-visible:ring-1 focus-visible:ring-[var(--accent-electric)] focus-visible:ring-offset-0 focus-visible:ring-offset-transparent shadow-none"
          />
        </div>

        <!-- 排序按钮 -->
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="outline"
              size="sm"
              class="gap-1.5 text-xs rounded-none bg-card border-border text-foreground hover:bg-muted font-bold cursor-pointer h-8"
            >
              <ArrowDownAZ class="h-3.5 w-3.5" />
              <span class="text-xxs uppercase tracking-wider">{{
                t("common.actions.sort")
              }}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            align="end"
            class="rounded-none bg-popover text-popover-foreground border-border"
          >
            <DropdownMenuItem
              v-for="option in sortOptions"
              :key="option.value"
              @click="sortBy = option.value"
              class="text-xs cursor-pointer rounded-none focus:bg-muted"
            >
              {{ option.label }}
              <Check
                v-if="sortBy === option.value"
                class="ml-auto h-3.5 w-3.5 text-[var(--accent-electric)]"
              />
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <!-- 语言标签过滤栏 -->
      <div
        class="relative w-full overflow-hidden border-t border-border/20 pt-1.5"
      >
        <div
          class="flex w-full items-center gap-1.5 overflow-x-auto scrollbar-hide"
        >
          <Badge
            variant="secondary"
            class="lc-md:px-2 inline-flex cursor-pointer items-center flex-shrink-0 gap-1 whitespace-nowrap rounded-none border border-transparent px-2.5 py-0.5 text-2xs font-bold uppercase tracking-wider transition-colors"
            :class="
              languageFilter === 'all'
                ? 'bg-[var(--accent-electric)] text-white border-[var(--accent-electric)]'
                : 'bg-muted text-muted-foreground hover:bg-muted/80 hover:text-foreground border-border/40'
            "
            @click="languageFilter = 'all'"
          >
            {{ t("common.labels.all") }}
          </Badge>
          <Badge
            v-for="option in languageOptions.filter(
              (opt) => opt.value !== 'all',
            )"
            :key="option.value"
            translate="no"
            variant="secondary"
            class="lc-md:px-2 inline-flex cursor-pointer items-center flex-shrink-0 gap-1 whitespace-nowrap rounded-none border border-transparent px-2.5 py-0.5 text-2xs font-bold uppercase tracking-wider transition-colors"
            :class="
              languageFilter === option.value
                ? 'bg-[var(--accent-electric)] text-white border-[var(--accent-electric)]'
                : 'bg-muted text-muted-foreground hover:bg-muted/80 hover:text-foreground border-border/40'
            "
            @click="languageFilter = option.value"
          >
            {{ option.label }}
          </Badge>
        </div>
      </div>

      <!-- 提交统计和操作栏 -->
      <div class="mt-1.5 lc-md:mt-1.5">
        <div
          class="bg-muted border border-border/40 flex items-center justify-between gap-3 rounded-none p-2 lc-md:p-1.5"
        >
          <div class="flex items-center gap-2 flex-1 min-w-0 pl-1">
            <template v-if="userSolution">
              <div
                class="rounded-none p-0.5 bg-[var(--solarized-green)] flex-shrink-0 flex items-center justify-center h-4 w-4"
              >
                <Check class="h-3 w-3 text-white" />
              </div>
              <span
                class="text-xxs font-bold uppercase tracking-wider text-[var(--solarized-green)] leading-tight truncate"
              >
                {{ t("problem.solutions.alreadyShared") }}
              </span>
            </template>
            <template v-else-if="bestSubmission">
              <div
                class="rounded-none p-0.5 bg-[var(--accent-electric)] flex-shrink-0 flex items-center justify-center h-4 w-4"
              >
                <Check class="h-3 w-3 text-white" />
              </div>
              <span
                class="text-xxs font-semibold text-foreground leading-tight truncate"
              >
                {{
                  t("problem.solutions.runtimeBeats", {
                    percent: bestSubmission.runtimePercentile?.toFixed(1) ?? 0,
                  })
                }}
              </span>
            </template>
            <template v-else>
              <span
                class="text-xxs font-semibold text-muted-foreground leading-tight truncate"
              >
                {{ t("problem.solutions.solveToWrite") }}
              </span>
            </template>
          </div>

          <button
            class="flex h-7.5 flex-shrink-0 items-center gap-1 rounded-none bg-[var(--accent-electric)] px-3 py-1 text-xxs font-bold uppercase tracking-wider text-white dark:text-[var(--solarized-base03)] shadow-xs transition-all hover:bg-[var(--accent-electric)]/90 active:bg-[var(--accent-electric)] cursor-pointer focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50"
            @click="handleCreateSolution"
          >
            <PenLine
              class="h-3.5 w-3.5 text-white dark:text-[var(--solarized-base03)]"
            />
            <span>
              {{
                userSolution
                  ? t("problem.solutions.editSolution")
                  : t("problem.solutions.writeSolution")
              }}
            </span>
          </button>
        </div>
      </div>
    </header>

    <!-- 题解列表 -->
    <div class="flex-1 overflow-y-auto bg-card">
      <div class="py-3">
        <div v-if="sortedItems.length" class="flex flex-col">
          <div
            v-for="(item, index) in sortedItems"
            :key="item.id"
            class="flex flex-col"
          >
            <div class="px-3">
              <SolutionCard :item="item" @select="handleSelect" />
            </div>
            <Separator v-if="index < sortedItems.length - 1" class="my-2" />
          </div>
        </div>
        <Empty v-else class="mx-3 bg-muted/30 px-6 py-8">
          <EmptyContent>
            <EmptyMedia variant="icon">
              <Lightbulb class="h-6 w-6 text-muted-foreground" />
            </EmptyMedia>
            <EmptyHeader>
              <p class="text-base font-semibold text-foreground">
                {{ t("problem.solutions.noSolutionsTitle") }}
              </p>
              <EmptyDescription>
                {{ t("problem.solutions.noSolutionsDesc") }}
              </EmptyDescription>
            </EmptyHeader>
          </EmptyContent>
        </Empty>
      </div>
    </div>
  </section>
</template>
