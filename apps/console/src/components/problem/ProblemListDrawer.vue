<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { fetchProblems } from "@/api/problem";
import type { Problem } from "@/types/problem";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Check,
  Search,
  ArrowUpDown,
  Filter,
  ChevronRight,
} from "lucide-vue-next";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";

defineProps<{
  currentProblemId?: number;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const { t } = useI18n();
const router = useRouter();
const problems = ref<Problem[]>([]);
const searchQuery = ref("");
const loading = ref(true);

// The backend /problems endpoint validates `pageSize` with @Max(100) in
// ProblemQueryDTO. The service layer additionally caps any larger value to
// 100, but the DTO validation rejects the request with HTTP 400 before that
// cap is applied, which caused the drawer to render the "未找到题目。" empty
// state. Requesting 100 keeps each call within the validated bound; the
// onMounted handler paginates through every page so a corpus larger than
// 100 stays fully searchable/selectable instead of being silently truncated.
const PROBLEM_DRAWER_PAGE_SIZE = 100;

onMounted(async () => {
  try {
    loading.value = true;
    const first = await fetchProblems({}, 1, PROBLEM_DRAWER_PAGE_SIZE);
    const all = [...first.items];
    for (let page = 2; page <= (first.totalPages ?? 1); page++) {
      const result = await fetchProblems({}, page, PROBLEM_DRAWER_PAGE_SIZE);
      all.push(...result.items);
    }
    problems.value = all;
  } catch (error) {
    console.error("Failed to load problems", error);
  } finally {
    loading.value = false;
  }
});

const filteredProblems = computed(() => {
  if (!searchQuery.value) return problems.value;
  const q = searchQuery.value.toLowerCase();
  return problems.value.filter(
    (p) => p.title.toLowerCase().includes(q) || p.id.toString().includes(q),
  );
});

const navigateToProblem = (slug: string) => {
  router.push(`/problems/${slug}`);
  emit("close");
};
</script>

<template>
  <div class="flex flex-col h-full bg-background text-sm font-sans">
    <!-- Header -->
    <div class="flex-none px-4 py-3 border-b bg-background sticky top-0 z-10">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-1 cursor-pointer hover:opacity-80">
          <h2 class="text-base font-medium text-foreground">
            {{ t("problem.drawer.problemList") }}
          </h2>
          <ChevronRight class="h-4 w-4 text-muted-foreground" />
        </div>
        <div class="flex items-center text-xs text-muted-foreground">
          <div
            class="w-3 h-3 rounded-full border border-[var(--status-success-mark)] mr-1.5 relative"
          >
            <div
              class="absolute inset-0 m-auto w-1.5 h-1.5 rounded-full bg-[var(--status-success-mark)]"
              v-if="filteredProblems.length > 0"
            ></div>
          </div>
          <span
            >{{
              filteredProblems.filter((p) => p.status === "solved").length
            }}/{{ problems.length }} {{ t("problem.drawer.solved") }}</span
          >
        </div>
      </div>

      <!-- Filters/Search -->
      <div class="flex items-center gap-2">
        <div class="relative flex-1 group">
          <Search
            class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground/70"
          />
          <Input
            v-model="searchQuery"
            :placeholder="t('problem.drawer.searchQuestions')"
            class="pl-9 h-9 bg-muted/50 border-transparent rounded-full focus:bg-background focus:border-border transition-all placeholder:text-muted-foreground/70"
          />
        </div>
        <Button
          variant="ghost"
          size="icon"
          class="h-9 w-9 rounded-full bg-muted/50 hover:bg-muted text-muted-foreground"
        >
          <ArrowUpDown class="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          class="h-9 w-9 rounded-full bg-muted/50 hover:bg-muted text-muted-foreground"
        >
          <Filter class="h-4 w-4" />
        </Button>
      </div>
    </div>

    <!-- Problem List -->
    <ScrollArea class="flex-1">
      <div class="px-2 py-2">
        <template v-if="loading">
          <div
            class="flex flex-col items-center justify-center p-8 space-y-3 text-muted-foreground"
          >
            <div
              class="animate-spin rounded-full h-6 w-6 border-b-2 border-primary"
            ></div>
          </div>
        </template>
        <template v-else-if="filteredProblems.length === 0">
          <div
            class="flex flex-col items-center justify-center py-12 space-y-3 text-muted-foreground"
          >
            <div
              class="flex h-12 w-12 items-center justify-center rounded-none bg-muted/50 mb-2"
            >
              <Search class="h-6 w-6 text-muted-foreground/50" />
            </div>
            <span class="text-sm font-bold">{{
              t("problem.drawer.noProblemsFound")
            }}</span>
          </div>
        </template>
        <template v-else>
          <div class="space-y-0.5">
            <!--
              Active row mirrors the "selected" pattern from
              ProblemSaveButton (`bg-primary/10 border-l-2 border-primary`)
              and the category filter so the highlight adapts to both
              solarized themes. The inactive row keeps a transparent
              `border-l-2` reserved so toggling active/inactive does not
              shift the row content.
            -->
            <div
              v-for="problem in filteredProblems"
              :key="problem.id"
              class="group flex items-center justify-between border-l-2 px-4 py-3 cursor-pointer transition-colors"
              :class="
                currentProblemId === problem.id
                  ? 'bg-primary/10 border-primary text-primary'
                  : 'border-transparent hover:bg-muted/50 text-foreground'
              "
              @click="
                navigateToProblem(
                  problem.slug ||
                    problem.title.toLowerCase().replace(/\s+/g, '-'),
                )
              "
            >
              <!-- Left: Status & Title -->
              <div class="flex items-center gap-3 min-w-0">
                <div class="shrink-0">
                  <Check
                    v-if="problem.status === 'solved'"
                    class="h-4 w-4 text-[var(--status-success-mark)]"
                  />
                  <div v-else class="h-4 w-4"></div>
                  <!-- Spacer -->
                </div>
                <span
                  class="truncate text-sm leading-none"
                  :class="currentProblemId === problem.id ? 'font-medium' : ''"
                >
                  {{ problem.id }}. {{ problem.title }}
                </span>
              </div>

              <!-- Right: Difficulty -->
              <div class="shrink-0 ml-4 text-xs">
                <span class="text-foreground-strong">
                  {{
                    problem.difficulty === "EASY"
                      ? t("problem.difficulty.easy")
                      : problem.difficulty === "MEDIUM"
                        ? t("problem.difficulty.medium")
                        : t("problem.difficulty.hard")
                  }}
                </span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </ScrollArea>
  </div>
</template>
