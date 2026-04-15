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

export function useProblemLists() {
  const { t } = useI18n();
  const loading = ref(true);
  const currentUserId = useAuthStore().fetchCurrentUserId();
  const searchQuery = ref("");

  // Data state
  const data = ref<UserProblemListsResponse>({
    myLists: [],
    savedLists: [],
    featured: [],
    categories: [],
  });

  // Computed
  const sortedMyLists = computed(() => {
    let lists = [...data.value.myLists].sort(
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
      data.value = await fetchProblemListsOverview(currentUserId);
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
    try {
      const newList = await createProblemList(currentUserId, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        isPublic: form.isPublic,
      });
      toast.success(t("personal.messages.folderCreated"));
      onClose();
      onSuccess(newList.id);
      await loadData();
    } catch (e) {
      console.error("Failed to create problem list", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Delete List ---
  const handleDeleteList = async (list: ProblemList) => {
    if (!currentUserId) return;
    try {
      await deleteProblemList(list.id, currentUserId);
      toast.success(t("personal.messages.folderDeleted"));
      await loadData();
    } catch (e) {
      console.error("Failed to delete problem list", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Unsave List ---
  const handleUnsaveList = async (list: ProblemList) => {
    if (!currentUserId) return;
    try {
      await unsaveList(list.id, currentUserId);
      toast.success(t("personal.messages.bookmarkRemoved"));
      await loadData();
    } catch (e) {
      console.error("Failed to unsave list", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Save Featured List ---
  const handleSaveList = async (list: ProblemList) => {
    if (!currentUserId) return;
    try {
      await saveList(list.id, currentUserId);
      toast.success(t("personal.messages.bookmarkAdded"));
      await loadData();
    } catch (e) {
      console.error("Failed to save list", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Move List to Category ---
  const handleMoveToCategory = async (
    list: ProblemList,
    categoryId: string | null,
  ) => {
    if (!currentUserId) return;
    try {
      await moveListToCategory(list.id, currentUserId, categoryId);
      toast.success(t("personal.messages.profileUpdated"));
      await loadData();
    } catch (e) {
      console.error("Failed to move list", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Create Category ---
  const handleCreateCategory = async (
    form: { name: string },
    onClose: () => void,
  ) => {
    if (!currentUserId || !form.name.trim()) return;
    try {
      await createCategory(currentUserId, {
        name: form.name.trim(),
      });
      toast.success(t("personal.messages.folderCreated"));
      onClose();
      await loadData();
    } catch (e) {
      console.error("Failed to create category", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Edit Category ---
  const handleEditCategory = async (
    category: ProblemListCategory,
    newName: string,
    onClose: () => void,
  ) => {
    if (!currentUserId || !newName.trim()) {
      toast.error(t("personal.problemLists.dialogs.newName"));
      return;
    }
    try {
      await updateCategory(category.id, currentUserId, {
        name: newName.trim(),
      });
      toast.success(t("personal.messages.profileUpdated"));
      onClose();
      await loadData();
    } catch (e) {
      console.error("Failed to update category", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  // --- Delete Category ---
  const handleDeleteCategory = async (category: ProblemListCategory) => {
    if (!currentUserId) return;
    try {
      await deleteCategory(category.id, currentUserId);
      toast.success(t("personal.messages.folderDeleted"));
      await loadData();
    } catch (e) {
      console.error("Failed to delete category", e);
      toast.error(t("personal.messages.saveFailed"));
    }
  };

  onMounted(loadData);

  return {
    loading,
    currentUserId,
    searchQuery,
    data,
    sortedMyLists,
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
