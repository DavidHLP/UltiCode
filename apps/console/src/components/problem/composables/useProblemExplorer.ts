import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { Problem } from "@/types/problem";
import type { Component } from "vue";
import type { ProblemExplorerProps } from "../type";
import { CheckCircle2, FileEdit } from "lucide-vue-next";
import { fetchProblems } from "@/api/problem";
import { toast } from "vue-sonner";
import { useRouter } from "vue-router";
import { PROBLEM_CATEGORIES } from "@/constants/problem-categories";
import type { ColumnDef } from "@/components/common/data-table";

export interface EnrichedProblem extends Omit<Problem, "acceptance_rate"> {
  status?: "solved" | "attempted" | "todo";
  statusIcon?: Component;
  acceptanceRate?: number;
  acceptance_rate?: number | undefined;
}

const PROBLEMS_PER_PAGE = 50;

export function useProblemExplorer(props: ProblemExplorerProps) {
  const router = useRouter();
  const { t } = useI18n();

  const searchQuery = ref("");
  const selectedTags = ref<string[]>([]);
  const selectedStatus = ref<string[]>([]);
  const selectedDifficulty = ref<string[]>([]);
  const showPremium = ref<boolean | null>(null);
  const fallbackProblems = ref<Problem[]>([]);

  const page = ref(1);
  const total = ref(0);
  const totalPages = ref(1);
  const isLoading = ref(false);
  const searchDebounceTimer = ref<ReturnType<typeof setTimeout> | null>(null);
  // Monotonic request token: a fresh search/filter bumps it so an in-flight
  // response that was superseded can detect it is stale and discard itself.
  const latestRequestToken = ref(0);

  const selectedCategory = ref(props.initialCategory || "all");

  watch(
    () => props.initialCategory,
    (newVal) => {
      selectedCategory.value = newVal || "all";
    },
  );

  const categoryOptions = computed(() =>
    PROBLEM_CATEGORIES.map((cat) => ({
      label: t("problem.categories." + cat.value),
      value: cat.value,
      icon: cat.icon,
    })),
  );

  const buildFilters = () => {
    return {
      search: searchQuery.value || undefined,
      category: selectedCategory.value,
      difficulty: selectedDifficulty.value[0] || undefined,
      status: selectedStatus.value[0] || undefined,
      tag: selectedTags.value[0] || undefined,
      isPremium: showPremium.value ?? undefined,
    };
  };

  const loadProblems = async (append = false) => {
    // Supplied mode: the caller passed its own problem list, so there is no
    // fetch to perform and the watchers/clearFilters/onMounted stay no-op.
    if (props.problems !== undefined) return;
    // Serialize pagination (scroll-driven load-more can fan); fresh
    // searches/filters go through and supersede via latestRequestToken.
    if (append && isLoading.value) return;
    const myToken = ++latestRequestToken.value;
    isLoading.value = true;
    try {
      const currentPage = append ? page.value : 1;
      const result = await fetchProblems(
        buildFilters(),
        currentPage,
        PROBLEMS_PER_PAGE,
      );
      // A newer search/filter superseded this request — discard the stale
      // response so it cannot overwrite newer state.
      if (myToken !== latestRequestToken.value) return;
      if (append) {
        fallbackProblems.value = [...fallbackProblems.value, ...result.items];
      } else {
        fallbackProblems.value = result.items;
        page.value = 1;
      }
      total.value = result.total;
      totalPages.value = result.totalPages;
    } catch (error) {
      if (myToken !== latestRequestToken.value) return;
      console.error("Failed to load problems", error);
      toast.error(t("problem.explorer.failedToLoad"));
      if (!append) {
        fallbackProblems.value = [];
        total.value = 0;
        totalPages.value = 1;
      }
    } finally {
      if (myToken === latestRequestToken.value) {
        isLoading.value = false;
      }
    }
  };

  onMounted(() => {
    void loadProblems();
  });

  const sourceProblems = computed(
    () => props.problems ?? fallbackProblems.value,
  );

  const enrichedProblems = computed<EnrichedProblem[]>(() => {
    return sourceProblems.value.map((p) => ({
      ...p,
      status: p.status ?? "todo",
    })) as EnrichedProblem[];
  });

  // Debounced search
  watch(searchQuery, () => {
    if (searchDebounceTimer.value) {
      clearTimeout(searchDebounceTimer.value);
    }
    searchDebounceTimer.value = setTimeout(() => {
      void loadProblems();
    }, 300);
  });

  // Immediate reload for non-search filters
  watch(
    [
      selectedTags,
      selectedStatus,
      selectedDifficulty,
      showPremium,
      selectedCategory,
    ],
    () => {
      void loadProblems();
    },
  );

  const hasMore = computed(() => page.value < totalPages.value);

  const displayedProblems = computed<EnrichedProblem[]>(() => {
    return enrichedProblems.value.map((p) => ({
      ...p,
      statusIcon:
        p.status === "solved"
          ? CheckCircle2
          : p.status === "attempted"
            ? FileEdit
            : undefined,
    })) as EnrichedProblem[];
  });

  const columns = computed<ColumnDef[]>(() => {
    const cols: ColumnDef[] = [
      {
        key: "status",
        header: t("problem.table.status"),
        class: "w-[50px] text-center p-0",
        headerClass: "w-[50px] text-center",
      },
      { key: "title", header: t("problem.table.title") },
      {
        key: "acceptance",
        header: t("problem.table.acceptance"),
        class: "w-[120px] text-center",
        headerClass: "w-[120px] text-center",
      },
      {
        key: "difficulty",
        header: t("problem.table.difficulty"),
        class: "w-[100px] text-center",
        headerClass: "w-[100px] text-center",
      },
    ];

    if (props.editable) {
      cols.push({
        key: "actions",
        header: t("problem.table.actions"),
        class: "w-[80px] text-center",
        headerClass: "w-[80px] text-center",
      });
    }

    return cols;
  });

  // Tag labels come from the backend (`problem_tags.label`) and are already
  // localized — typically Chinese for this codebase. Derive popular tags from
  // the actual tags present in the current dataset, ranked by frequency, so the
  // two rows of the filter (popular / more) stay in sync with what clicking
  // will actually match on the server.
  const MAX_POPULAR_TAGS = 6;

  const allTags = computed(() => {
    const tags = new Set<string>();
    enrichedProblems.value.forEach((p) =>
      p.tags.forEach((tag) => tags.add(tag)),
    );
    return Array.from(tags);
  });

  const popularTags = computed(() => {
    const counts = new Map<string, number>();
    enrichedProblems.value.forEach((p) =>
      p.tags.forEach((tag) => counts.set(tag, (counts.get(tag) ?? 0) + 1)),
    );
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .slice(0, MAX_POPULAR_TAGS)
      .map(([tag]) => tag);
  });

  const otherTags = computed(() => {
    const popular = new Set(popularTags.value);
    return allTags.value
      .filter((tag) => !popular.has(tag))
      .sort((a, b) => a.localeCompare(b, "zh-Hans-CN"));
  });

  const activeFilterCount = computed(() => {
    return (
      selectedStatus.value.length +
      selectedDifficulty.value.length +
      (showPremium.value !== null ? 1 : 0)
    );
  });

  const hasActiveFilters = computed(() => {
    return (
      !!searchQuery.value ||
      selectedTags.value.length > 0 ||
      selectedStatus.value.length > 0 ||
      selectedDifficulty.value.length > 0 ||
      showPremium.value !== null
    );
  });

  function toggleStatus(value: string, checked: boolean) {
    if (checked) {
      if (!selectedStatus.value.includes(value)) {
        selectedStatus.value = [...selectedStatus.value, value];
      }
    } else {
      selectedStatus.value = selectedStatus.value.filter((s) => s !== value);
    }
  }

  function toggleDifficulty(value: string, checked: boolean) {
    if (checked) {
      if (!selectedDifficulty.value.includes(value)) {
        selectedDifficulty.value = [...selectedDifficulty.value, value];
      }
    } else {
      selectedDifficulty.value = selectedDifficulty.value.filter(
        (d) => d !== value,
      );
    }
  }

  function togglePremium(value: boolean, checked: boolean) {
    if (checked) {
      showPremium.value = value;
    } else {
      if (showPremium.value === value) {
        showPremium.value = null;
      }
    }
  }

  function setSearchQuery(value: string) {
    searchQuery.value = value;
  }

  function setSelectedCategory(value: string) {
    selectedCategory.value = value;
  }

  function setSelectedTags(value: string[]) {
    selectedTags.value = value;
  }

  function clearFilters() {
    searchQuery.value = "";
    selectedTags.value = [];
    selectedStatus.value = [];
    selectedDifficulty.value = [];
    showPremium.value = null;
    void loadProblems();
  }

  async function pickOne() {
    const currentProblems = displayedProblems.value;
    if (currentProblems.length > 0) {
      const randomIndex = Math.floor(Math.random() * currentProblems.length);
      const problem = currentProblems[randomIndex];
      if (problem && problem.slug) {
        await router.push(`/problems/${problem.slug}`);
      }
      return;
    }

    try {
      const { fetchRandomProblem } = await import("@/api/problem");
      const problem = await fetchRandomProblem();
      if (problem) {
        await router.push(`/problems/${problem.slug}`);
      } else {
        toast.error(t("problem.explorer.noProblemsAvailable"));
      }
    } catch (error) {
      console.error("Failed to fetch random problem", error);
      toast.error(t("problem.explorer.failedToPickRandom"));
    }
  }

  function loadMore() {
    if (page.value < totalPages.value) {
      page.value += 1;
      void loadProblems(true);
    }
  }

  return {
    // State
    searchQuery,
    selectedTags,
    selectedStatus,
    selectedDifficulty,
    showPremium,
    selectedCategory,
    categoryOptions,
    popularTags,
    otherTags,
    isLoading,

    // Computed
    enrichedProblems,
    displayedProblems,
    columns,
    allTags,
    activeFilterCount,
    hasActiveFilters,
    hasMore,

    // Functions
    toggleStatus,
    toggleDifficulty,
    togglePremium,
    setSearchQuery,
    setSelectedCategory,
    setSelectedTags,
    clearFilters,
    pickOne,
    loadMore,
    loadProblems,
  };
}
