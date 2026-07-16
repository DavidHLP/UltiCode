import { ref, computed, onMounted } from "vue";
import { useAuthStore } from "@/stores/auth";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";
import type {
  ProblemList,
  ProblemListCategory,
  UserProblemListsResponse,
} from "@/types/problem-list";
import {
  fetchProblemListsOverview,
  createProblemList,
  deleteProblemList,
  unsaveList,
  saveList,
  createCategory,
  updateCategory,
  deleteCategory,
  moveListToCategory,
} from "@/api/problem-list";
import { useProblemListMutations } from "@/composables/useProblemListMutations";

/**
 * Personal-page Problem List composable (architecture-review candidate #2).
 *
 * <p>Screen-specific shape (sorted lists, search query) stays here. The
 * mutation policy (HTTP call + toast + reload) concentrates in
 * {@link useProblemListMutations} so all three console composables
 * (this, the sidebar, the detail) emit identical user feedback on
 * success and failure.
 */
export function useProblemLists() {
  const { t } = useI18n();
  const { run } = useProblemListMutations();
  const loading = ref(true);
  const currentUserId = useAuthStore().fetchCurrentUserId();
  const searchQuery = ref("");

  // Data state
  const data = ref<UserProblemListsResponse>({
    ownLists: [],
    savedLists: [],
    featuredLists: [],
    categories: [],
  });

  // Computed
  const sortedOwnLists = computed(() => {
    let lists = [...data.value.ownLists].sort(
      (a, b) => b.problemCount - a.problemCount,
    );
    if (searchQuery.value.trim()) {
      const q = searchQuery.value.toLowerCase();
      lists = lists.filter(
        (l) =>
          l.name.toLowerCase().includes(q) ||
          l.description?.toLowerCase().includes(q),
      );
    }
    return lists;
  });

  const sortedSavedLists = computed(() => {
    const inCategoryIds = new Set(
      data.value.categories.flatMap((c) => c.lists.map((l) => l.id)),
    );
    let lists = data.value.savedLists.filter((l) => !inCategoryIds.has(l.id));
    if (searchQuery.value.trim()) {
      const q = searchQuery.value.toLowerCase();
      lists = lists.filter((l) => l.name.toLowerCase().includes(q));
    }
    return lists;
  });

  const totalSavedCount = computed(() => {
    return data.value.savedLists.length;
  });

  const sortedCategories = computed(() => {
    return [...data.value.categories].sort((a, b) => a.sortOrder - b.sortOrder);
  });

  const loadData = async () => {
    if (!currentUserId) {
      loading.value = false;
      return;
    }
    try {
      data.value = await fetchProblemListsOverview();
    } catch (e) {
      console.error("Failed to load problem lists", e);
      toast.error(t("personal.messages.loadFailed"));
    } finally {
      loading.value = false;
    }
  };

  // --- Create List ---
  const handleCreateList = async (
    form: { name: string; description: string; isPublic: boolean },
    onSuccess: (newListId: string) => void,
    onClose: () => void,
  ) => {
    if (!currentUserId || !form.name.trim()) return;
    await run({
      call: () =>
        createProblemList({
          name: form.name.trim(),
          description: form.description.trim() || undefined,
          isPublic: form.isPublic,
        }),
      onSuccess: (newList) => {
        onClose();
        onSuccess(newList.id);
      },
      successKey: "personal.messages.folderCreated",
      failureLabel: "create list",
      reload: loadData,
    });
  };

  // --- Delete List ---
  const handleDeleteList = async (list: ProblemList) => {
    if (!currentUserId) return;
    await run({
      call: () => deleteProblemList(list.id),
      successKey: "personal.messages.folderDeleted",
      failureLabel: "delete list",
      reload: loadData,
    });
  };

  // --- Unsave List ---
  const handleUnsaveList = async (list: ProblemList) => {
    if (!currentUserId) return;
    await run({
      call: () => unsaveList(list.id),
      successKey: "personal.messages.bookmarkRemoved",
      failureLabel: "unsave list",
      reload: loadData,
    });
  };

  // --- Save Featured List ---
  const handleSaveList = async (list: ProblemList) => {
    if (!currentUserId) return;
    await run({
      call: () => saveList(list.id),
      successKey: "personal.messages.bookmarkAdded",
      failureLabel: "save list",
      reload: loadData,
    });
  };

  // --- Move List to Category ---
  const handleMoveToCategory = async (
    list: ProblemList,
    categoryId: string | null,
  ) => {
    if (!currentUserId) return;
    await run({
      call: () => moveListToCategory(list.id, categoryId),
      successKey: "personal.messages.profileUpdated",
      failureLabel: "move list",
      reload: loadData,
    });
  };

  // --- Create Category ---
  const handleCreateCategory = async (
    form: { name: string },
    onClose: () => void,
  ) => {
    if (!currentUserId || !form.name.trim()) return;
    await run({
      call: () => createCategory({ name: form.name.trim() }),
      onSuccess: () => onClose(),
      successKey: "personal.messages.folderCreated",
      failureLabel: "create category",
      reload: loadData,
    });
  };

  // --- Edit Category ---
  // The synchronous validation toast stays inline: the mutation helper
  // assumes the call already passed client-side checks. Failing before
  // the HTTP call is a UI concern, not a mutation concern.
  const handleEditCategory = async (
    category: ProblemListCategory,
    newName: string,
    onClose: () => void,
  ) => {
    if (!currentUserId || !newName.trim()) {
      toast.error(t("personal.problemLists.dialogs.newName"));
      return;
    }
    await run({
      call: () => updateCategory(category.id, { name: newName.trim() }),
      onSuccess: () => onClose(),
      successKey: "personal.messages.profileUpdated",
      failureLabel: "edit category",
      reload: loadData,
    });
  };

  // --- Delete Category ---
  const handleDeleteCategory = async (category: ProblemListCategory) => {
    if (!currentUserId) return;
    await run({
      call: () => deleteCategory(category.id),
      successKey: "personal.messages.folderDeleted",
      failureLabel: "delete category",
      reload: loadData,
    });
  };

  onMounted(loadData);

  return {
    loading,
    currentUserId,
    searchQuery,
    data,
    sortedOwnLists,
    sortedSavedLists,
    totalSavedCount,
    sortedCategories,
    loadData,
    handleCreateList,
    handleDeleteList,
    handleUnsaveList,
    handleSaveList,
    handleMoveToCategory,
    handleCreateCategory,
    handleEditCategory,
    handleDeleteCategory,
  };
}