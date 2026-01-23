<script setup lang="ts">
import type { Problem } from "@/types/problem";

import { computed, onMounted, ref, watch, markRaw } from "vue";
import {
  CheckCircle2,
  FileEdit,
  CircleDot,
  Video,
  Lock,
  Trash2,
} from "lucide-vue-next";
import CheckIcon from "~icons/radix-icons/check";
import { fetchProblems, fetchRandomProblem } from "@/api/problem";
import { toast } from "vue-sonner";
import { fetchCurrentUserId } from "@/utils/auth";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";

import type { ProblemExplorerProps } from "./type";
import { PROBLEM_CATEGORIES } from "@/constants/problem-categories";

import {
  CategoryFilter,
  DataTableToolbar,
  TagFilter,
  DataTable,
  type ColumnDef,
} from "@/components/common/data-table";
import {
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuCheckboxItem,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";

const router = useRouter();
const { t } = useI18n();

const props = defineProps<ProblemExplorerProps>();

const emit = defineEmits<{
  remove: [problem: Problem];
}>();

const searchQuery = ref("");
const selectedTags = ref<string[]>([]);
const selectedStatus = ref<string[]>([]);
const selectedDifficulty = ref<string[]>([]);
const showPremium = ref<boolean | null>(null);
const problemsPerPage = 50;
const numProblemsToShow = ref(problemsPerPage);
const fallbackProblems = ref<Problem[]>([]);

const selectedCategory = ref(props.initialCategory || "all");

watch(
  () => props.initialCategory,
  (newVal) => {
    selectedCategory.value = newVal || "all";
    void loadProblems();
  },
);

// Map categories for generic component
const categoryOptions = computed(() =>
  PROBLEM_CATEGORIES.map((cat) => ({
    label: t("problem.categories." + cat.value),
    value: cat.value,
    icon: cat.icon,
  })),
);

const loadProblems = async () => {
  try {
    const userId = fetchCurrentUserId();
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
const enrichedProblems = computed(() => {
  const enriched = sourceProblems.value.map((p) => {
    const status = p.status ?? "todo";
    const icon =
      status === "solved"
        ? markRaw(CheckCircle2)
        : status === "attempted"
          ? markRaw(CircleDot)
          : undefined;

    return {
      ...p,
      status,
      statusIcon: icon,
    };
  });
  return enriched;
});

// Watch filters to reset pagination
watch(
  [
    searchQuery,
    selectedTags,
    selectedStatus,
    selectedDifficulty,
    showPremium,
    selectedCategory,
  ],
  () => {
    numProblemsToShow.value = problemsPerPage;
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

    return (
      searchMatch &&
      tagMatch &&
      statusMatch &&
      difficultyMatch &&
      premiumMatch &&
      categoryMatch
    );
  });
});

const totalFilteredProblems = computed(() => filteredProblems.value.length);

const displayedProblems = computed(() => {
  return filteredProblems.value.slice(0, numProblemsToShow.value).map((p) => ({
    ...p,
    statusIcon:
      p.status === "solved"
        ? CheckCircle2
        : p.status === "attempted"
          ? FileEdit
          : undefined,
  }));
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
  enrichedProblems.value.forEach((p) => p.tags.forEach((t) => tags.add(t)));
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
  allTags.value.filter((t) => !popularTags.value.includes(t)),
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

function clearFilters() {
  searchQuery.value = "";
  selectedTags.value = [];
  selectedStatus.value = [];
  selectedDifficulty.value = [];
  showPremium.value = null;
  numProblemsToShow.value = problemsPerPage;
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
    numProblemsToShow.value += problemsPerPage;
  }
}

const difficultyClass = (difficulty: Problem["difficulty"]) => {
  switch (difficulty) {
    case "Easy":
      return "text-emerald-600";
    case "Medium":
      return "text-amber-600";
    case "Hard":
      return "text-red-600";
    default:
      return "";
  }
};

const formatAcceptance = (value: number | string | undefined | null) => {
  if (value === undefined || value === null) return "-";
  const num = Number(value);
  return isNaN(num) ? "-" : `${num.toFixed(1)}%`;
};

const goToDetail = (slug: string) => {
  router.push({ name: "problem-detail", params: { slug } });
};

const handleRowClick = (problem: Problem) => {
  if (problem && problem.slug) {
    goToDetail(problem.slug);
  }
};

const handleRemove = (e: Event, problem: Problem) => {
  e.stopPropagation();
  emit("remove", problem);
};
</script>

<template>
  <section class="flex flex-col gap-6">
    <slot name="header" />

    <div class="space-y-4">
      <CategoryFilter
        :categories="categoryOptions"
        v-model="selectedCategory"
      />

      <DataTableToolbar
        v-model="searchQuery"
        :placeholder="t('problem.list.searchPlaceholder')"
        :filter-label="t('problem.explorer.filters')"
        :active-filter-count="activeFilterCount"
        :show-clear="hasActiveFilters"
        :clear-label="
          t('common.actions.clear') + ' ' + t('problem.explorer.filters')
        "
        @clear="clearFilters"
      >
        <template #filters>
          <DropdownMenuLabel>{{
            t("problem.explorer.status")
          }}</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuCheckboxItem
            :checked="selectedStatus.includes('solved')"
            @click="
              () => toggleStatus('solved', !selectedStatus.includes('solved'))
            "
          >
            <span class="flex items-center w-full">
              {{ t("problem.status.solved") }}
              <CheckIcon
                v-if="selectedStatus.includes('solved')"
                class="ml-auto h-4 w-4"
              />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem
            :checked="selectedStatus.includes('attempted')"
            @click="
              () =>
                toggleStatus('attempted', !selectedStatus.includes('attempted'))
            "
          >
            <span class="flex items-center w-full">
              {{ t("problem.status.attempted") }}
              <CheckIcon
                v-if="selectedStatus.includes('attempted')"
                class="ml-auto h-4 w-4"
              />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem
            :checked="selectedStatus.includes('todo')"
            @click="
              () => toggleStatus('todo', !selectedStatus.includes('todo'))
            "
          >
            <span class="flex items-center w-full">
              {{ t("problem.status.todo") }}
              <CheckIcon
                v-if="selectedStatus.includes('todo')"
                class="ml-auto h-4 w-4"
              />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuSeparator />
          <DropdownMenuLabel>{{
            t("problem.explorer.difficulty")
          }}</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuCheckboxItem
            :checked="selectedDifficulty.includes('Easy')"
            @click="
              () =>
                toggleDifficulty('Easy', !selectedDifficulty.includes('Easy'))
            "
          >
            <span class="flex items-center w-full">
              {{ t("problem.difficulty.easy") }}
              <CheckIcon
                v-if="selectedDifficulty.includes('Easy')"
                class="ml-auto h-4 w-4"
              />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem
            :checked="selectedDifficulty.includes('Medium')"
            @click="
              () =>
                toggleDifficulty(
                  'Medium',
                  !selectedDifficulty.includes('Medium'),
                )
            "
          >
            <span class="flex items-center w-full">
              {{ t("problem.difficulty.medium") }}
              <CheckIcon
                v-if="selectedDifficulty.includes('Medium')"
                class="ml-auto h-4 w-4"
              />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem
            :checked="selectedDifficulty.includes('Hard')"
            @click="
              () =>
                toggleDifficulty('Hard', !selectedDifficulty.includes('Hard'))
            "
          >
            <span class="flex items-center w-full">
              {{ t("problem.difficulty.hard") }}
              <CheckIcon
                v-if="selectedDifficulty.includes('Hard')"
                class="ml-auto h-4 w-4"
              />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuSeparator />
          <DropdownMenuLabel>{{
            t("problem.explorer.premium")
          }}</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuCheckboxItem
            :checked="showPremium === false"
            @click="() => togglePremium(false, !(showPremium === false))"
          >
            <span class="flex items-center w-full">
              {{ t("problem.explorer.free") }}
              <CheckIcon v-if="showPremium === false" class="ml-auto h-4 w-4" />
            </span>
          </DropdownMenuCheckboxItem>
          <DropdownMenuCheckboxItem
            :checked="showPremium === true"
            @click="() => togglePremium(true, !(showPremium === true))"
          >
            <span class="flex items-center w-full">
              {{ t("problem.explorer.premium") }}
              <CheckIcon v-if="showPremium === true" class="ml-auto h-4 w-4" />
            </span>
          </DropdownMenuCheckboxItem>
        </template>
        <template #actions>
          <Button variant="outline" class="h-10 rounded-full" @click="pickOne">
            {{ t("problem.explorer.pickOne") }}
          </Button>
        </template>
      </DataTableToolbar>

      <TagFilter
        v-model="selectedTags"
        :popular-tags="popularTags"
        :other-tags="otherTags"
        :show-more-label="t('problem.explorer.showMoreTags')"
      />
    </div>

    <DataTable
      :data="displayedProblems"
      :columns="columns"
      :has-more="numProblemsToShow < totalFilteredProblems"
      :empty-label="t('problem.table.noResults')"
      :empty-description="t('problem.table.tryAdjusting')"
      :loading-label="t('problem.table.loadingMore')"
      @load-more="loadMore"
      @row-click="handleRowClick"
    >
      <template #cell-status="{ item: problem }">
        <div class="flex justify-center items-center">
          <component
            :is="(problem as any).statusIcon"
            v-if="(problem as any).statusIcon"
            class="h-5 w-5"
            :class="{
              'text-emerald-600': (problem as any).status === 'solved',
            }"
          />
        </div>
      </template>

      <template #cell-title="{ item: problem }">
        <div class="flex items-center gap-2">
          <span class="truncate"
            >{{ (problem as any).id }}. {{ (problem as any).title }}</span
          >
          <a
            v-if="(problem as any).hasSolution"
            href="#"
            class="no-underline hover:no-underline text-muted-foreground hover:text-foreground"
            @click.stop
          >
            <Video class="h-4 w-4" />
          </a>
          <Lock
            v-if="(problem as any).isPremium"
            class="h-4 w-4 text-amber-500"
          />
        </div>
      </template>

      <template #cell-acceptance="{ item: problem }">
        {{
          formatAcceptance(
            (problem as any).acceptanceRate ?? (problem as any).acceptance_rate,
          )
        }}
      </template>

      <template #cell-difficulty="{ item: problem }">
        <span :class="difficultyClass((problem as any).difficulty)">
          {{
            t("problem.difficulty." + (problem as any).difficulty.toLowerCase())
          }}
        </span>
      </template>

      <template #cell-actions="{ item: problem }">
        <Button
          variant="ghost"
          size="icon"
          class="h-8 w-8 text-muted-foreground hover:text-destructive rounded-full"
          @click="(e: MouseEvent) => handleRemove(e, problem as Problem)"
        >
          <Trash2 class="h-4 w-4" />
        </Button>
      </template>
    </DataTable>
  </section>
</template>
