import { ref, computed, onMounted } from "vue";
import { toast } from "vue-sonner";
import { useAuthStore } from "@/stores/auth";
import type {
  ProblemList,
  ProblemListCategory,
} from "@/types/problem-list";
import {
  createCategory,
  updateCategory,
  deleteCategory,
  unsaveList,
  moveListToCategory,
  deleteProblemList,
  createProblemList,
} from "@/api/problem-list";
import { useProblemListsStore } from "@/stores/problemLists";
import { useProblemListMutations } from "@/composables/useProblemListMutations";

/**
 * Sidebar Problem List composable (architecture-review candidate #2).
 *
 * <p>Screen-specific shape (sorted lists, search query) stays here. The
 * mutation policy (HTTP call + toast + reload) concentrates in
 * {@link useProblemListMutations}. The sidebar still ships literal
 * English toasts because its screens have not yet been internationalised;
 * once that lands, swap the literals for {@code t()} calls and the helper
 * keeps working unchanged.
 */
export function useSidebarLists() {
  const currentUserId = useAuthStore().fetchCurrentUserId();
  const problemListsStore = useProblemListsStore();
  const { run } = useProblemListMutations();

  const data = computed(() => problemListsStore.data);
  const isLoading = computed(() => problemListsStore.isLoading);

  const loadData = async (force = false) => {
    await problemListsStore.loadOverview(force);
  };

  onMounted(() => {
    void loadData();
  });

  const allCategories = computed(() => problemListsStore.categories);

  // --- Create Category ---
  const isCreateCategoryOpen = ref(false);
  const isCreatingCategory = ref(false);
  const createCategoryForm = ref({ name: "" });

  // Synchronous validation toast stays inline: the mutation helper
  // assumes the call already passed client-side checks.
  const handleCreateCategory = async () => {
    if (!currentUserId) return;
    if (!createCategoryForm.value.name.trim()) {
      toast.error("Category name is required");
      return;
    }
    isCreatingCategory.value = true;
    await run({
      call: () =>
        createCategory({
          name: createCategoryForm.value.name.trim(),
        }),
      onSuccess: () => {
        isCreateCategoryOpen.value = false;
        createCategoryForm.value = { name: "" };
      },
      successMessage: "Category created successfully",
      errorMessage: "Failed to create category",
      failureLabel: "create category",
      reload: () => loadData(true),
    });
    isCreatingCategory.value = false;
  };

  // --- Edit Category ---
  const isEditCategoryOpen = ref(false);
  const isEditingCategory = ref(false);
  const categoryToEdit = ref<ProblemListCategory | null>(null);
  const editCategoryForm = ref({ name: "" });

  const openEditCategoryDialog = (category: ProblemListCategory) => {
    categoryToEdit.value = category;
    editCategoryForm.value = { name: category.name };
    isEditCategoryOpen.value = true;
  };

  const handleEditCategory = async () => {
    if (!currentUserId || !categoryToEdit.value) return;
    if (!editCategoryForm.value.name.trim()) {
      toast.error("Category name is required");
      return;
    }
    isEditingCategory.value = true;
    await run({
      call: () =>
        updateCategory(categoryToEdit.value!.id, {
          name: editCategoryForm.value.name.trim(),
        }),
      onSuccess: () => {
        isEditCategoryOpen.value = false;
      },
      successMessage: "Category updated successfully",
      errorMessage: "Failed to update category",
      failureLabel: "update category",
      reload: () => loadData(true),
    });
    isEditingCategory.value = false;
  };

  // --- Delete Category ---
  const isDeleteCategoryOpen = ref(false);
  const isDeletingCategory = ref(false);
  const categoryToDelete = ref<ProblemListCategory | null>(null);

  const openDeleteCategoryDialog = (category: ProblemListCategory) => {
    categoryToDelete.value = category;
    isDeleteCategoryOpen.value = true;
  };

  const handleDeleteCategory = async () => {
    if (!currentUserId || !categoryToDelete.value) return;
    isDeletingCategory.value = true;
    await run({
      call: () => deleteCategory(categoryToDelete.value!.id),
      onSuccess: () => {
        isDeleteCategoryOpen.value = false;
      },
      successMessage: "Category deleted successfully",
      errorMessage: "Failed to delete category",
      failureLabel: "delete category",
      reload: () => loadData(true),
    });
    isDeletingCategory.value = false;
  };

  // --- Delete List ---
  const isDeleteListOpen = ref(false);
  const isDeletingList = ref(false);
  const listToDelete = ref<ProblemList | null>(null);

  const openDeleteListDialog = (list: ProblemList) => {
    listToDelete.value = list;
    isDeleteListOpen.value = true;
  };

  const handleDeleteList = async () => {
    if (!listToDelete.value || !currentUserId) return;
    isDeletingList.value = true;
    const target = listToDelete.value;
    await run({
      call: () => deleteProblemList(target.id),
      onSuccess: () => {
        isDeleteListOpen.value = false;
      },
      successMessage: `Deleted "${target.name}"`,
      errorMessage: "Failed to delete list",
      failureLabel: "delete list",
      reload: () => loadData(true),
    });
    isDeletingList.value = false;
  };

  // --- Unsave List ---
  const handleUnsaveList = async (list: ProblemList) => {
    if (!currentUserId) return;
    await run({
      call: () => unsaveList(list.id),
      successMessage: `Removed "${list.name}" from saved`,
      errorMessage: "Failed to remove from saved",
      failureLabel: "unsave list",
      reload: () => loadData(true),
    });
  };

  // --- Move List to Category ---
  const handleMoveListToCategory = async (
    list: ProblemList,
    categoryId: string | null,
  ) => {
    if (!currentUserId) return;
    await run({
      call: () => moveListToCategory(list.id, categoryId),
      successMessage: categoryId
        ? `Moved "${list.name}" to category`
        : `Removed "${list.name}" from category`,
      errorMessage: "Failed to move list",
      failureLabel: "move list",
      reload: () => loadData(true),
    });
  };

  // --- Create List ---
  const isCreateListOpen = ref(false);
  const isCreatingList = ref(false);
  const createListForm = ref({ name: "", description: "", isPublic: false });

  const handleCreateList = async () => {
    if (!currentUserId) return;
    if (!createListForm.value.name.trim()) {
      toast.error("List name is required");
      return;
    }
    isCreatingList.value = true;
    await run({
      call: () =>
        createProblemList({
          name: createListForm.value.name.trim(),
          description: createListForm.value.description.trim() || undefined,
          isPublic: createListForm.value.isPublic,
        }),
      onSuccess: (newList) => {
        isCreateListOpen.value = false;
        createListForm.value = { name: "", description: "", isPublic: false };
        window.location.href = `/problemset/list/${newList.id}`;
      },
      successMessage: "Problem list created successfully",
      errorMessage: "Failed to create list",
      failureLabel: "create list",
      reload: () => loadData(true),
    });
    isCreatingList.value = false;
  };

  return {
    // Data
    data,
    isLoading,
    allCategories,
    loadError: computed(() => problemListsStore.loadError),
    loadData,

    // Category CRUD
    isCreateCategoryOpen,
    isCreatingCategory,
    createCategoryForm,
    handleCreateCategory,
    isEditCategoryOpen,
    isEditingCategory,
    categoryToEdit,
    editCategoryForm,
    openEditCategoryDialog,
    handleEditCategory,
    isDeleteCategoryOpen,
    isDeletingCategory,
    categoryToDelete,
    openDeleteCategoryDialog,
    handleDeleteCategory,

    // List CRUD
    isDeleteListOpen,
    isDeletingList,
    listToDelete,
    openDeleteListDialog,
    handleDeleteList,
    handleUnsaveList,
    handleMoveListToCategory,

    // Create list
    isCreateListOpen,
    isCreatingList,
    createListForm,
    handleCreateList,
  };
}