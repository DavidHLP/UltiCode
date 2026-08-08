<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { ListPlus, Plus } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { useSidebarLists } from "./composables/useSidebarLists";
import SidebarListSections from "./components/SidebarListSections.vue";
import SidebarListDialogs from "./components/SidebarListDialogs.vue";

const { t } = useI18n();

const {
  data,
  allCategories,
  loadError,
  isCreateCategoryOpen,
  isCreatingCategory,
  createCategoryForm,
  handleCreateCategory,
  isEditCategoryOpen,
  isEditingCategory,
  editCategoryForm,
  openEditCategoryDialog,
  handleEditCategory,
  isDeleteCategoryOpen,
  isDeletingCategory,
  categoryToDelete,
  openDeleteCategoryDialog,
  handleDeleteCategory,
  isDeleteListOpen,
  isDeletingList,
  listToDelete,
  openDeleteListDialog,
  handleDeleteList,
  handleUnsaveList,
  handleMoveListToCategory,
  isCreateListOpen,
  isCreatingList,
  createListForm,
  handleCreateList,
  loadData,
} = useSidebarLists();
</script>

<template>
  <!-- Action Buttons -->
  <div class="px-4 py-2 space-y-2">
    <Button
      variant="ghost"
      size="sm"
      class="w-full justify-start gap-2 text-[var(--silver-500)] dark:text-[var(--silver-400)] hover:text-[var(--accent-electric)] rounded-none border border-dashed border-silver/20 bg-transparent hover:border-[var(--accent-electric)]/50 hover:bg-[var(--accent-electric)]/5 transition-all duration-200 font-data text-xs h-8"
      @click="isCreateListOpen = true"
    >
      <ListPlus class="h-3.5 w-3.5" />
      {{ t("sidebar.problemLists.newList") }}
    </Button>
    <Button
      variant="ghost"
      size="sm"
      class="w-full justify-start gap-2 text-[var(--silver-500)] dark:text-[var(--silver-400)] hover:text-[var(--accent-electric)] rounded-none border border-dashed border-silver/20 bg-transparent hover:border-[var(--accent-electric)]/50 hover:bg-[var(--accent-electric)]/5 transition-all duration-200 font-data text-xs h-8"
      @click="isCreateCategoryOpen = true"
    >
      <Plus class="h-3.5 w-3.5" />
      {{ t("sidebar.problemLists.newCategory") }}
    </Button>
  </div>

  <!-- List Sections -->
  <SidebarListSections
    :data="data"
    :all-categories="allCategories"
    :has-error="loadError"
    @delete-list="openDeleteListDialog"
    @unsave-list="handleUnsaveList"
    @move-list-to-category="handleMoveListToCategory"
    @edit-category="openEditCategoryDialog"
    @delete-category="openDeleteCategoryDialog"
    @retry-load="loadData(true)"
  />

  <!-- Dialogs -->
  <SidebarListDialogs
    :is-create-category-open="isCreateCategoryOpen"
    :is-creating-category="isCreatingCategory"
    :create-category-form="createCategoryForm"
    :is-edit-category-open="isEditCategoryOpen"
    :is-editing-category="isEditingCategory"
    :edit-category-form="editCategoryForm"
    :is-delete-category-open="isDeleteCategoryOpen"
    :is-deleting-category="isDeletingCategory"
    :category-to-delete="categoryToDelete"
    :is-delete-list-open="isDeleteListOpen"
    :is-deleting-list="isDeletingList"
    :list-to-delete="listToDelete"
    :is-create-list-open="isCreateListOpen"
    :is-creating-list="isCreatingList"
    :create-list-form="createListForm"
    @update:is-create-category-open="isCreateCategoryOpen = $event"
    @update:is-edit-category-open="isEditCategoryOpen = $event"
    @update:is-delete-category-open="isDeleteCategoryOpen = $event"
    @update:is-delete-list-open="isDeleteListOpen = $event"
    @update:is-create-list-open="isCreateListOpen = $event"
    @update:create-category-form="createCategoryForm = $event"
    @update:edit-category-form="editCategoryForm = $event"
    @update:create-list-form="createListForm = $event"
    @create-category="handleCreateCategory"
    @edit-category="handleEditCategory"
    @delete-category="handleDeleteCategory"
    @delete-list="handleDeleteList"
    @create-list="handleCreateList"
  />
</template>
