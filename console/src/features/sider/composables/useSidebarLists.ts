import { ref, computed, onMounted } from "vue";
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
import { toast } from "vue-sonner";

export function useSidebarLists() {
  const currentUserId = useAuthStore().fetchCurrentUserId();
  const problemListsStore = useProblemListsStore();

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

  const handleCreateCategory = async () => {
    if (!currentUserId) return;
    if (!createCategoryForm.value.name.trim()) {
      toast.error("Category name is required");
      return;
    }
    isCreatingCategory.value = true;
    try {
      await createCategory({
        name: createCategoryForm.value.name.trim(),
      });
      toast.success("Category created successfully");
      isCreateCategoryOpen.value = false;
      createCategoryForm.value = { name: "" };
      await loadData(true);
    } catch (e) {
      console.error("Failed to create category", e);
      toast.error("Failed to create category");
    } finally {
      isCreatingCategory.value = false;
    }
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
    try {
      await updateCategory(categoryToEdit.value.id, {
        name: editCategoryForm.value.name.trim(),
      });
      toast.success("Category updated successfully");
      isEditCategoryOpen.value = false;
      await loadData(true);
    } catch (e) {
      console.error("Failed to update category", e);
      toast.error("Failed to update category");
    } finally {
      isEditingCategory.value = false;
    }
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
    try {
      await deleteCategory(categoryToDelete.value.id);
      toast.success("Category deleted successfully");
      isDeleteCategoryOpen.value = false;
      await loadData(true);
    } catch (e) {
      console.error("Failed to delete category", e);
      toast.error("Failed to delete category");
    } finally {
      isDeletingCategory.value = false;
    }
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
    try {
      await deleteProblemList(listToDelete.value.id);
      toast.success(`Deleted "${listToDelete.value.name}"`);
      isDeleteListOpen.value = false;
      await loadData(true);
    } catch (e) {
      console.error("Failed to delete list", e);
      toast.error("Failed to delete list");
    } finally {
      isDeletingList.value = false;
    }
  };

  // --- Unsave List ---
  const handleUnsaveList = async (list: ProblemList) => {
    if (!currentUserId) return;
    try {
      await unsaveList(list.id);
      toast.success(`Removed "${list.name}" from saved`);
      await loadData(true);
    } catch (e) {
      console.error("Failed to unsave list", e);
      toast.error("Failed to remove from saved");
    }
  };

  // --- Move List to Category ---
  const handleMoveListToCategory = async (
    list: ProblemList,
    categoryId: string | null,
  ) => {
    if (!currentUserId) return;
    try {
      await moveListToCategory(list.id, categoryId);
      toast.success(
        categoryId
          ? `Moved "${list.name}" to category`
          : `Removed "${list.name}" from category`,
      );
      await loadData(true);
    } catch (e) {
      console.error("Failed to move list", e);
      toast.error("Failed to move list");
    }
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
    try {
      const newList = await createProblemList({
        name: createListForm.value.name.trim(),
        description: createListForm.value.description.trim() || undefined,
        isPublic: createListForm.value.isPublic,
      });
      toast.success("Problem list created successfully");
      isCreateListOpen.value = false;
      createListForm.value = { name: "", description: "", isPublic: false };
      await loadData(true);
      window.location.href = `/problemset/list/${newList.id}`;
    } catch (e) {
      console.error("Failed to create list", e);
      toast.error("Failed to create list");
    } finally {
      isCreatingList.value = false;
    }
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