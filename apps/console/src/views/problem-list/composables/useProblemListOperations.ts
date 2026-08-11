import { ref, computed, watch, type Ref } from "vue";
import { useAuthStore } from "@/stores/auth";
import { useRouter } from "vue-router";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";
import { getDifficultyBadgeClass } from "@ulticode/design-system";
import type { Problem } from "@/types/problem";
import type {
  ProblemList,
  ProblemListCategoryOption,
} from "@/types/problem-list";
import {
  fetchProblemListOverview,
  forkProblemList,
  deleteProblemList,
  updateProblemList,
  addProblemToList,
  removeProblemFromList,
  saveList,
  unsaveList,
  moveListToCategory,
} from "@/api/problem-list";
import { useProblemListMutations } from "@/composables/useProblemListMutations";

/**
 * Problem List detail-page composable (architecture-review candidate #2).
 *
 * <p>Screen-specific shape (current list, problems, owner/save state,
 * edit form, fork/delete flow) stays here. The mutation policy
 * (HTTP call + toast + reload) concentrates in
 * {@link useProblemListMutations} so the sidebar, the personal page, and
 * this detail page all surface identical user feedback.
 *
 * <p>Two handlers stay outside the helper because they don't fit the
 * shape:
 * <ul>
 *   <li>{@code handleShare} — pure clipboard write, no HTTP call.</li>
 *   <li>{@code handleFork}'s auth-required branch — pre-call validation,
 *       not a mutation.</li>
 * </ul>
 */
export function useProblemListOperations(listId: Ref<string>) {
  const router = useRouter();
  const { t, locale } = useI18n();
  const { run } = useProblemListMutations();

  const currentList = ref<ProblemList | null>(null);
  const problems = ref<Problem[]>([]);
  const isSaved = ref(false);
  const isSaving = ref(false);
  const isForking = ref(false);
  const isDeleting = ref(false);
  const userCategories = ref<ProblemListCategoryOption[]>([]);
  const currentCategoryId = ref<string | null>(null);

  const currentUser = useAuthStore().fetchCurrentUserId();

  const editForm = ref({
    name: "",
    description: "",
    isPublic: false,
  });

  const isOwner = computed(() => {
    return !!(currentUser && currentList.value?.authorId === currentUser);
  });

  const canSave = computed(() => {
    return !!(
      currentUser &&
      currentList.value &&
      currentList.value.authorId !== currentUser &&
      currentList.value.isPublic
    );
  });

  const problemIdsInList = computed(() => {
    return new Set(problems.value.map((p) => p.id));
  });

  async function loadProblemList(id?: string) {
    if (!id) {
      currentList.value = null;
      problems.value = [];
      isSaved.value = false;
      return;
    }
    try {
      const overview = await fetchProblemListOverview(id);
      currentList.value = overview.list;
      problems.value = overview.problems;
      isSaved.value = overview.viewer?.isSaved ?? false;
      currentCategoryId.value = overview.viewer?.categoryId ?? null;
      userCategories.value = overview.categories ?? [];

      if (currentList.value) {
        editForm.value = {
          name: currentList.value.name,
          description: currentList.value.description || "",
          isPublic: currentList.value.isPublic || false,
        };
      }
    } catch (error) {
      console.error("Failed to load problem list overview", error);
      problems.value = [];
      currentList.value = null;
      isSaved.value = false;
      currentCategoryId.value = null;
      userCategories.value = [];
    }
  }

  watch(
    listId,
    (id) => {
      void loadProblemList(id);
    },
    { immediate: true },
  );

  function formatDate(date?: Date | string): string {
    if (!date) return "";
    const d = typeof date === "string" ? new Date(date) : date;
    return new Intl.DateTimeFormat(locale.value, {
      year: "numeric",
      month: "long",
      day: "numeric",
    }).format(d);
  }

  async function handleShare() {
    const url = window.location.href;
    await navigator.clipboard.writeText(url);
    toast.success(t("problem.problemList.messages.linkCopied"), {
      description: t("problem.problemList.messages.linkCopiedDesc"),
    });
  }

  async function handleFork() {
    if (!currentUser) {
      toast.error(t("problem.problemList.messages.authRequired"), {
        description: t("problem.problemList.messages.signInToFork"),
      });
      return;
    }

    isForking.value = true;
    await run({
      call: () => forkProblemList(listId.value),
      onSuccess: (newList) => {
        router.push(`/problemset/list/${newList.id}`);
      },
      successMessage: t("problem.problemList.messages.forkSuccess"),
      successDescription: t("problem.problemList.messages.forkSuccessDesc"),
      errorMessage: t("problem.problemList.messages.forkFailed"),
      failureLabel: "fork list",
    });
    isForking.value = false;
  }

  async function handleDelete() {
    if (!currentUser || !currentList.value) return;

    isDeleting.value = true;
    await run({
      call: () => deleteProblemList(listId.value),
      onSuccess: () => {
        router.push("/problemset");
      },
      successMessage: t("problem.problemList.messages.deleteSuccess"),
      successDescription: t("problem.problemList.messages.deleteSuccessDesc"),
      errorMessage: t("problem.problemList.messages.deleteFailed"),
      failureLabel: "delete list",
    });
    isDeleting.value = false;
  }

  async function handleSaveEdit(): Promise<boolean> {
    if (!currentUser || !currentList.value) return false;

    const result = await run({
      call: () =>
        updateProblemList(listId.value, {
          name: editForm.value.name,
          description: editForm.value.description,
          isPublic: editForm.value.isPublic,
        }),
      onSuccess: async () => {
        await loadProblemList(listId.value);
      },
      successMessage: t("problem.problemList.messages.updateSuccess"),
      successDescription: t("problem.problemList.messages.updateSuccessDesc"),
      errorMessage: t("problem.problemList.messages.updateFailed"),
      failureLabel: "update list",
    });
    return result !== null;
  }

  async function handleToggleSave() {
    if (!currentUser || !currentList.value) return;

    isSaving.value = true;
    if (isSaved.value) {
      const result = await run({
        call: () => unsaveList(listId.value),
        onSuccess: () => {
          isSaved.value = false;
          currentCategoryId.value = null;
        },
        successMessage: t("problem.problemList.messages.unsaveSuccess"),
        errorMessage: t("problem.problemList.messages.unsaveFailed"),
        failureLabel: "unsave list",
      });
      if (result === null) {
        // Restore optimistic flag on failure so the UI mirrors server state.
        isSaved.value = true;
      }
    } else {
      const result = await run({
        call: () => saveList(listId.value),
        onSuccess: () => {
          isSaved.value = true;
        },
        successMessage: t("problem.problemList.messages.saveSuccess"),
        errorMessage: t("problem.problemList.messages.saveFailed"),
        failureLabel: "save list",
      });
      if (result === null) {
        isSaved.value = false;
      }
    }
    isSaving.value = false;
  }

  async function handleMoveToCategory(categoryId: string | null) {
    if (!currentUser || !isSaved.value) return;

    const previousCategoryId = currentCategoryId.value;
    const result = await run({
      call: () => moveListToCategory(listId.value, categoryId),
      onSuccess: () => {
        currentCategoryId.value = categoryId;
      },
      successMessage: categoryId
        ? t("problem.problemList.messages.moveSuccess")
        : t("problem.problemList.messages.removeCategorySuccess"),
      errorMessage: t("problem.problemList.messages.moveFailed"),
      failureLabel: "move list to category",
    });
    if (result === null) {
      // Roll back the local mirror when the call fails so the chip doesn't drift.
      currentCategoryId.value = previousCategoryId;
    }
  }

  async function handleAddProblem(problem: Problem) {
    if (!currentUser || problemIdsInList.value.has(problem.id)) return;

    await run({
      call: () => addProblemToList(listId.value, problem.id),
      onSuccess: async () => {
        await loadProblemList(listId.value);
      },
      successMessage: t("problem.problemList.messages.addSuccess", {
        title: problem.title,
      }),
      errorMessage: t("problem.problemList.messages.addFailed"),
      failureLabel: "add problem to list",
    });
  }

  async function handleRemoveProblem(problem: Problem) {
    if (!currentUser) return;

    const previousProblems = problems.value;
    const result = await run({
      call: () => removeProblemFromList(listId.value, problem.id),
      onSuccess: () => {
        problems.value = problems.value.filter((p) => p.id !== problem.id);
      },
      successMessage: t("problem.problemList.messages.removeSuccess", {
        title: problem.title,
      }),
      errorMessage: t("problem.problemList.messages.removeFailed"),
      failureLabel: "remove problem from list",
    });
    // Restore the local mirror when the call fails so the UI doesn't drop a row optimistically.
    if (result === null) {
      problems.value = previousProblems;
    }
  }

  return {
    currentList,
    problems,
    isSaved,
    isSaving,
    isForking,
    isDeleting,
    editForm,
    userCategories,
    currentCategoryId,
    isOwner,
    canSave,
    problemIdsInList,
    formatDate,
    handleShare,
    handleFork,
    handleDelete,
    handleSaveEdit,
    handleToggleSave,
    handleMoveToCategory,
    handleAddProblem,
    handleRemoveProblem,
    getDifficultyColor: getDifficultyBadgeClass,
  };
}
