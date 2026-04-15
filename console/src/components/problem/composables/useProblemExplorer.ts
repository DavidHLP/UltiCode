import { computed, onMounted, ref, watch, markRaw } from "vue";
import { useAuthStore } from "@/stores/auth";
import { useI18n } from "vue-i18n";
import type { Problem } from "@/types/problem";
import type { Component } from "vue";
import type { ProblemExplorerProps } from "../type";
import {
  CheckCircle2,
  FileEdit,
  CircleDot,
} from "lucide-vue-next";
import { fetchProblems, fetchRandomProblem } from "@/api/problem";
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
  const numProblemsToShow = ref(PROBLEMS_PER_PAGE);
  const fallbackProblems = ref<Problem[]>([]);

  const selectedCategory = ref(props.initialCategory || "all");

  watch(
    () => props.initialCategory,
    (newVal) => {
      selectedCategory.value = newVal || "all";
      void loadProblems();
    },
  );

  const categoryOptions = computed(() =>
    PROBLEM_CATEGORIES.map((cat) => ({
      label: t("problem.categories." + cat.value),
      value: cat.value,
      icon: cat.icon,
    })),
  );

  const loadProblems = async () => {
    try {
      const userId = useAuthStore().fetchCurrentUserId();
      fallbackProblems.value = await fetchProblems(userId ?? undefined, {
        category: selectedCategory.value,
      });
    } catch (error) {
      console.error("Failed to load problems", error);
      fallbackProblems.value = [];
    }
  };

  onMounted(() => {
    void loadProblems();
  });

  const sourceProblems = computed(() => props.problems ?? fallbackProblems.value);

  const enrichedProblems = computed<EnrichedProblem[]>(() => {
    return sourceProblems.value.map((p) => {
      const status = p.status ?? "todo";
      const icon =
        status === "solved"
          ? markRaw(CheckCircle2)
          : status === "attempted"
            ? markRaw(CircleDot)
            : undefined;
      return { ...p, status, statusIcon: icon } as EnrichedProblem;
    });
  });

  // Reset pagination when filters change
  watch(
    [searchQuery, selectedTags, selectedStatus, selectedDifficulty, showPremium, selectedCategory],
    () => {
      numProblemsToShow.value = PROBLEMS_PER_PAGE;
    },
  );

  const filteredProblems = computed(() => {
    return enrichedProblems.value.filter((p) => {
      const searchMatch =
        !searchQuery.value ||
        p.title.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
        p.id.toString().includes(searchQuery.value);
      const tagMatch =
        selectedTags.value.length === 0 ||
        selectedTags.value.some((tag) => p.tags.includes(tag));
      const statusMatch =
        selectedStatus.value.length === 0 ||
        (p.status && selectedStatus.value.includes(p.status));
      const difficultyMatch =
        selectedDifficulty.value.length === 0 ||
        selectedDifficulty.value.includes(p.difficulty);
      const premiumMatch =
        showPremium.value === null || p.isPremium === showPremium.value;

      const categoryConfig = PROBLEM_CATEGORIES.find(
        (c) => c.value === selectedCategory.value,
      );
      const categoryMatch =
        selectedCategory.value === "all" ||
        !categoryConfig ||
        (p.tags && p.tags.includes(categoryConfig.name));

      return searchMatch && tagMatch && statusMatch && difficultyMatch && premiumMatch && categoryMatch;
    });
  });

  const totalFilteredProblems = computed(() => filteredProblems.value.length);

  const displayedProblems = computed<EnrichedProblem[]>(() => {
    return filteredProblems.value.slice(0, numProblemsToShow.value).map((p) => ({
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

  const allTags = computed(() => {
    const tags = new Set<string>();
    enrichedProblems.value.forEach((p) => p.tags.forEach((tag) => tags.add(tag)));
    return Array.from(tags).sort();
  });

  const popularTags = ref([
    "Array",
    "Hash Table",
    "String",
    "Math",
    "Dynamic Programming",
    "Sorting",
  ]);

  const otherTags = computed(() =>
    allTags.value.filter((tag) => !popularTags.value.includes(tag)),
  );

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
      selectedDifficulty.value = selectedDifficulty.value.filter((d) => d !== value);
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

  function clearFilters() {
    searchQuery.value = "";
    selectedTags.value = [];
    selectedStatus.value = [];
    selectedDifficulty.value = [];
    showPremium.value = null;
    numProblemsToShow.value = PROBLEMS_PER_PAGE;
  }

  async function pickOne() {
    const currentProblems = filteredProblems.value;
    if (currentProblems.length > 0) {
      const randomIndex = Math.floor(Math.random() * currentProblems.length);
      const problem = currentProblems[randomIndex];
      if (problem && problem.slug) {
        await router.push(`/problems/${problem.slug}`);
      }
      return;
    }

    try {
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
    if (numProblemsToShow.value < totalFilteredProblems.value) {
      numProblemsToShow.value += PROBLEMS_PER_PAGE;
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
    numProblemsToShow,
    categoryOptions,
    popularTags,
    otherTags,

    // Computed
    enrichedProblems,
    filteredProblems,
    totalFilteredProblems,
    displayedProblems,
    columns,
    allTags,
    activeFilterCount,
    hasActiveFilters,

    // Functions
    toggleStatus,
    toggleDifficulty,
    togglePremium,
    clearFilters,
    pickOne,
    loadMore,
    loadProblems,
  };
}
