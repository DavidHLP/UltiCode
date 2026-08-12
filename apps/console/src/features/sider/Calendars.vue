<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { ListPlus, Plus } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { useSidebarLists } from "./composables/useSidebarLists";
import SidebarListSections from "./components/SidebarListSections.vue";
import SidebarListDialogs from "./components/SidebarListDialogs.vue";
import { SidebarMenuItem as SharedSidebarMenuItem } from "@/shared/sidebar-menu/src";

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
  <div class="flex flex-col gap-0.5 px-1 py-1">
    <SharedSidebarMenuItem
      as="button"
      class="w-full text-left text-xs font-medium"
      @click="isCreateListOpen = true"
    >
      <template #icon>
        <ListPlus class="h-3.5 w-3.5" />
      </template>
      {{ t("sidebar.problemLists.newList") }}
    </SharedSidebarMenuItem>
    <SharedSidebarMenuItem
      as="button"
      class="w-full text-left text-xs font-medium"
      @click="isCreateCategoryOpen = true"
    >
      <template #icon>
        <Plus class="h-3.5 w-3.5" />
      </template>
      {{ t("sidebar.problemLists.newCategory") }}
    </SharedSidebarMenuItem>
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
