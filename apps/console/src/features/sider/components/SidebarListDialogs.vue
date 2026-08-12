<script setup lang="ts">
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { useI18n } from "vue-i18n";
import type { ProblemList, ProblemListCategory } from "@/types/problem-list";

const { t } = useI18n();

defineProps<{
  // Create Category
  isCreateCategoryOpen: boolean;
  isCreatingCategory: boolean;
  createCategoryForm: { name: string };
  // Edit Category
  isEditCategoryOpen: boolean;
  isEditingCategory: boolean;
  editCategoryForm: { name: string };
  // Delete Category
  isDeleteCategoryOpen: boolean;
  isDeletingCategory: boolean;
  categoryToDelete: ProblemListCategory | null;
  // Delete List
  isDeleteListOpen: boolean;
  isDeletingList: boolean;
  listToDelete: ProblemList | null;
  // Create List
  isCreateListOpen: boolean;
  isCreatingList: boolean;
  createListForm: { name: string; description: string; isPublic: boolean };
}>();

const emit = defineEmits<{
  (e: "update:isCreateCategoryOpen", value: boolean): void;
  (e: "update:isEditCategoryOpen", value: boolean): void;
  (e: "update:isDeleteCategoryOpen", value: boolean): void;
  (e: "update:isDeleteListOpen", value: boolean): void;
  (e: "update:isCreateListOpen", value: boolean): void;
  (e: "update:createCategoryForm", value: { name: string }): void;
  (e: "update:editCategoryForm", value: { name: string }): void;
  (
    e: "update:createListForm",
    value: { name: string; description: string; isPublic: boolean },
  ): void;
  (e: "createCategory"): void;
  (e: "editCategory"): void;
  (e: "deleteCategory"): void;
  (e: "deleteList"): void;
  (e: "createList"): void;
}>();
</script>

<template>
  <!-- Create Category Dialog -->
  <Dialog
    :open="isCreateCategoryOpen"
    @update:open="emit('update:isCreateCategoryOpen', $event)"
  >
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{
          t("sidebar.problemLists.createCategory")
        }}</DialogTitle>
        <DialogDescription>{{
          t("sidebar.problemLists.createCategory")
        }}</DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="category-name">{{
            t("sidebar.problemLists.categoryName")
          }}</Label>
          <Input
            id="category-name"
            :model-value="createCategoryForm.name"
            :placeholder="t('sidebar.problemLists.categoryNamePlaceholder')"
            @update:model-value="
              emit('update:createCategoryForm', { name: String($event) })
            "
            @keydown.enter="emit('createCategory')"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="emit('update:isCreateCategoryOpen', false)"
          :disabled="isCreatingCategory"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button @click="emit('createCategory')" :disabled="isCreatingCategory">
          {{
            isCreatingCategory
              ? t("common.status.saving")
              : t("common.actions.create")
          }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Edit Category Dialog -->
  <Dialog
    :open="isEditCategoryOpen"
    @update:open="emit('update:isEditCategoryOpen', $event)"
  >
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{
          t("sidebar.problemLists.renameCategory")
        }}</DialogTitle>
        <DialogDescription>{{
          t("sidebar.problemLists.renameCategory")
        }}</DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="edit-category-name">{{
            t("sidebar.problemLists.categoryName")
          }}</Label>
          <Input
            id="edit-category-name"
            :model-value="editCategoryForm.name"
            @update:model-value="
              emit('update:editCategoryForm', { name: String($event) })
            "
            @keydown.enter="emit('editCategory')"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="emit('update:isEditCategoryOpen', false)"
          :disabled="isEditingCategory"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button @click="emit('editCategory')" :disabled="isEditingCategory">
          {{
            isEditingCategory
              ? t("common.status.saving")
              : t("common.actions.save")
          }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Delete Category Confirmation -->
  <AlertDialog
    :open="isDeleteCategoryOpen"
    @update:open="emit('update:isDeleteCategoryOpen', $event)"
  >
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>{{
          t("sidebar.problemLists.deleteCategory")
        }}</AlertDialogTitle>
        <AlertDialogDescription>
          {{
            t("sidebar.problemLists.deleteCategoryConfirm", {
              name: categoryToDelete?.name,
            })
          }}
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel :disabled="isDeletingCategory">{{
          t("common.actions.cancel")
        }}</AlertDialogCancel>
        <AlertDialogAction
          class="bg-status-error-surface text-foreground-strong border border-destructive hover:bg-status-error-surface/80"
          @click="emit('deleteCategory')"
          :disabled="isDeletingCategory"
        >
          {{
            isDeletingCategory
              ? t("common.status.failed")
              : t("common.actions.delete")
          }}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>

  <!-- Delete List Confirmation -->
  <AlertDialog
    :open="isDeleteListOpen"
    @update:open="emit('update:isDeleteListOpen', $event)"
  >
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>{{
          t("problem.problemList.delete")
        }}</AlertDialogTitle>
        <AlertDialogDescription>
          {{
            t("problem.problemList.detail.deleteConfirmDesc", {
              name: listToDelete?.name,
            })
          }}
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel :disabled="isDeletingList">{{
          t("common.actions.cancel")
        }}</AlertDialogCancel>
        <AlertDialogAction
          class="bg-status-error-surface text-foreground-strong border border-destructive hover:bg-status-error-surface/80"
          @click="emit('deleteList')"
          :disabled="isDeletingList"
        >
          {{
            isDeletingList
              ? t("common.status.failed")
              : t("common.actions.delete")
          }}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>

  <!-- Create List Dialog -->
  <Dialog
    :open="isCreateListOpen"
    @update:open="emit('update:isCreateListOpen', $event)"
  >
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{ t("problem.problemList.create") }}</DialogTitle>
        <DialogDescription>{{
          t("problem.problemList.create")
        }}</DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="new-list-name">{{ t("common.labels.name") }}</Label>
          <Input
            id="new-list-name"
            :model-value="createListForm.name"
            :placeholder="t('problem.problemList.namePlaceholder')"
            @update:model-value="
              emit('update:createListForm', {
                ...createListForm,
                name: String($event),
              })
            "
            @keydown.enter="emit('createList')"
          />
        </div>
        <div class="space-y-2">
          <Label for="new-list-desc">{{
            t("problem.problemList.description")
          }}</Label>
          <Textarea
            id="new-list-desc"
            :model-value="createListForm.description"
            :placeholder="t('problem.problemList.descriptionPlaceholder')"
            rows="3"
            @update:model-value="
              emit('update:createListForm', {
                ...createListForm,
                description: String($event),
              })
            "
          />
        </div>
        <div class="flex items-center justify-between">
          <div class="space-y-0.5">
            <Label for="new-list-public">{{
              t("problem.problemList.public")
            }}</Label>
            <p class="text-xs text-muted-foreground">
              {{
                createListForm.isPublic
                  ? t("problem.problemList.detail.publicHint")
                  : t("problem.problemList.detail.privateHint")
              }}
            </p>
          </div>
          <Switch
            id="new-list-public"
            :checked="createListForm.isPublic"
            @update:checked="
              emit('update:createListForm', {
                ...createListForm,
                isPublic: $event,
              })
            "
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="emit('update:isCreateListOpen', false)"
          :disabled="isCreatingList"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button
          @click="emit('createList')"
          :disabled="isCreatingList || !createListForm.name.trim()"
        >
          {{
            isCreatingList
              ? t("common.status.saving")
              : t("common.actions.create")
          }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
