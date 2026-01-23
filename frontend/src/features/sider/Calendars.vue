<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import {
  ChevronRight,
  MoreHorizontal,
  Plus,
  Pencil,
  Trash2,
  FolderInput,
  Star,
  Bookmark,
  User,
  BookmarkMinus,
  ListPlus,
} from "lucide-vue-next";
import type {
  ProblemList,
  ProblemListCategory,
  UserProblemListsResponse,
} from "@/types/problem-list";
import { RouterLink } from "vue-router";
import { ref, computed, onMounted } from "vue";
import { useI18n } from "vue-i18n";
import { fetchCurrentUserId } from "@/utils/auth";
import {
  fetchProblemListsOverview,
  createCategory,
  updateCategory,
  deleteCategory,
  unsaveList,
  moveListToCategory,
  deleteProblemList,
  createProblemList,
} from "@/api/problem-list";
import { toast } from "vue-sonner";

const { t } = useI18n();
const currentUserId = fetchCurrentUserId();

// Data state
const data = ref<UserProblemListsResponse>({
  myLists: [],
  savedLists: [],
  featured: [],
  categories: [],
});
const isLoading = ref(false);

// Load data
const loadData = async () => {
  if (!currentUserId) return;
  isLoading.value = true;
  try {
    data.value = await fetchProblemListsOverview(currentUserId);
  } catch (e) {
    console.error("Failed to load problem lists", e);
  } finally {
    isLoading.value = false;
  }
};

onMounted(loadData);

// --- Create Category Dialog ---
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
    await createCategory(currentUserId, {
      name: createCategoryForm.value.name.trim(),
    });
    toast.success("Category created successfully");
    isCreateCategoryOpen.value = false;
    createCategoryForm.value = { name: "" };
    await loadData();
  } catch (e) {
    console.error("Failed to create category", e);
    toast.error("Failed to create category");
  } finally {
    isCreatingCategory.value = false;
  }
};

// --- Edit Category Dialog ---
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
    await updateCategory(categoryToEdit.value.id, currentUserId, {
      name: editCategoryForm.value.name.trim(),
    });
    toast.success("Category updated successfully");
    isEditCategoryOpen.value = false;
    await loadData();
  } catch (e) {
    console.error("Failed to update category", e);
    toast.error("Failed to update category");
  } finally {
    isEditingCategory.value = false;
  }
};

// --- Delete Category Dialog ---
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
    await deleteCategory(categoryToDelete.value.id, currentUserId);
    toast.success("Category deleted successfully");
    isDeleteCategoryOpen.value = false;
    await loadData();
  } catch (e) {
    console.error("Failed to delete category", e);
    toast.error("Failed to delete category");
  } finally {
    isDeletingCategory.value = false;
  }
};

// --- Delete List Dialog (for own lists) ---
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
    await deleteProblemList(listToDelete.value.id, currentUserId);
    toast.success(`Deleted "${listToDelete.value.name}"`);
    isDeleteListOpen.value = false;
    await loadData();
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
    await unsaveList(list.id, currentUserId);
    toast.success(`Removed "${list.name}" from saved`);
    await loadData();
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
    await moveListToCategory(list.id, currentUserId, categoryId);
    toast.success(
      categoryId
        ? `Moved "${list.name}" to category`
        : `Removed "${list.name}" from category`,
    );
    await loadData();
  } catch (e) {
    console.error("Failed to move list", e);
    toast.error("Failed to move list");
  }
};

// All categories for move menu
const allCategories = computed(() => data.value.categories);

// --- Create List Dialog ---
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
    const newList = await createProblemList(currentUserId, {
      name: createListForm.value.name.trim(),
      description: createListForm.value.description.trim() || undefined,
      isPublic: createListForm.value.isPublic,
    });
    toast.success("Problem list created successfully");
    isCreateListOpen.value = false;
    createListForm.value = { name: "", description: "", isPublic: false };
    await loadData();
    // Navigate to the new list
    window.location.href = `/problemset/list/${newList.id}`;
  } catch (e) {
    console.error("Failed to create list", e);
    toast.error("Failed to create list");
  } finally {
    isCreatingList.value = false;
  }
};
</script>

<template>
  <!-- Action Buttons -->
  <div class="px-4 py-2 space-y-1">
    <Button
      variant="ghost"
      size="sm"
      class="w-full justify-start gap-2 text-muted-foreground hover:text-foreground"
      @click="isCreateListOpen = true"
    >
      <ListPlus class="h-4 w-4" />
      {{ t("sidebar.problemLists.newList") }}
    </Button>
    <Button
      variant="ghost"
      size="sm"
      class="w-full justify-start gap-2 text-muted-foreground hover:text-foreground"
      @click="isCreateCategoryOpen = true"
    >
      <Plus class="h-4 w-4" />
      {{ t("sidebar.problemLists.newCategory") }}
    </Button>
  </div>

  <!-- My Lists Section -->
  <div class="px-4 py-2" v-if="data.myLists.length > 0">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <User class="h-4 w-4 mr-1 text-blue-500" />
          <span class="text-sm font-semibold">{{
            t("sidebar.problemLists.myLists")
          }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in data.myLists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <!-- Actions for own lists -->
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-40">
                  <DropdownMenuItem
                    class="text-destructive focus:text-destructive"
                    @click="openDeleteListDialog(item)"
                  >
                    <Trash2 class="mr-2 h-4 w-4" />
                    {{ t("common.actions.delete") }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Saved Lists Section -->
  <div class="px-4 py-2" v-if="data.savedLists.length > 0">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <Bookmark class="h-4 w-4 mr-1 text-green-500" />
          <span class="text-sm font-semibold">{{
            t("sidebar.problemLists.savedLists")
          }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in data.savedLists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <!-- Actions for saved lists -->
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-48">
                  <!-- Move to Category Sub-menu -->
                  <DropdownMenuSub v-if="allCategories.length > 0">
                    <DropdownMenuSubTrigger>
                      <FolderInput class="mr-2 h-4 w-4" />
                      {{ t("problem.problemList.detail.moveToCategory") }}
                    </DropdownMenuSubTrigger>
                    <DropdownMenuSubContent class="w-40">
                      <DropdownMenuItem
                        v-for="cat in allCategories"
                        :key="cat.id"
                        @click="handleMoveListToCategory(item, cat.id)"
                      >
                        {{ cat.name }}
                      </DropdownMenuItem>
                    </DropdownMenuSubContent>
                  </DropdownMenuSub>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem @click="handleUnsaveList(item)">
                    <BookmarkMinus class="mr-2 h-4 w-4" />
                    {{ t("problem.problemList.detail.unsave") }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Featured Section -->
  <div class="px-4 py-2" v-if="data.featured.length > 0">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <Star class="h-4 w-4 mr-1 text-yellow-500" />
          <span class="text-sm font-semibold">{{
            t("sidebar.problemLists.featured")
          }}</span>
        </CollapsibleTrigger>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in data.featured" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- User Categories -->
  <div v-for="category in data.categories" :key="category.id" class="px-4 py-2">
    <Collapsible :default-open="true">
      <div class="flex items-center justify-between group">
        <CollapsibleTrigger class="flex flex-1 items-center gap-1">
          <ChevronRight
            class="h-4 w-4 transform transition-transform duration-200 ui-open:rotate-90"
          />
          <span class="text-sm font-semibold">{{ category.name }}</span>
        </CollapsibleTrigger>

        <!-- Category Actions Menu -->
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
            >
              <MoreHorizontal class="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-40">
            <DropdownMenuItem @click="openEditCategoryDialog(category)">
              <Pencil class="mr-2 h-4 w-4" />
              {{ t("common.actions.edit") }}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              class="text-destructive focus:text-destructive"
              @click="openDeleteCategoryDialog(category)"
            >
              <Trash2 class="mr-2 h-4 w-4" />
              {{ t("common.actions.delete") }}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <CollapsibleContent class="py-2">
        <ul class="space-y-1">
          <li v-for="item in category.lists" :key="item.id" class="group/item">
            <div
              class="flex items-center justify-between gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
            >
              <RouterLink
                :to="`/problemset/list/${item.id}`"
                class="flex flex-1 items-center gap-2 truncate"
              >
                <span class="flex-1 truncate">{{ item.name }}</span>
                <span class="text-xs text-muted-foreground">{{
                  item.problemCount
                }}</span>
              </RouterLink>

              <!-- Actions for category lists -->
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-5 w-5 opacity-0 group-hover/item:opacity-100 transition-opacity"
                    @click.prevent.stop
                  >
                    <MoreHorizontal class="h-3 w-3" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" class="w-48">
                  <DropdownMenuItem
                    @click="handleMoveListToCategory(item, null)"
                  >
                    <FolderInput class="mr-2 h-4 w-4" />
                    {{ t("problem.problemList.detail.removeFromCategory") }}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem @click="handleUnsaveList(item)">
                    <BookmarkMinus class="mr-2 h-4 w-4" />
                    {{ t("problem.problemList.detail.unsave") }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </li>
        </ul>
      </CollapsibleContent>
    </Collapsible>
  </div>

  <!-- Create Category Dialog -->
  <Dialog v-model:open="isCreateCategoryOpen">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{
          t("sidebar.problemLists.createCategory")
        }}</DialogTitle>
        <DialogDescription>
          {{ t("sidebar.problemLists.createCategory") }}
        </DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="category-name">{{
            t("sidebar.problemLists.categoryName")
          }}</Label>
          <Input
            id="category-name"
            v-model="createCategoryForm.name"
            :placeholder="t('sidebar.problemLists.categoryNamePlaceholder')"
            @keydown.enter="handleCreateCategory"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="isCreateCategoryOpen = false"
          :disabled="isCreatingCategory"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button @click="handleCreateCategory" :disabled="isCreatingCategory">
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
  <Dialog v-model:open="isEditCategoryOpen">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{
          t("sidebar.problemLists.renameCategory")
        }}</DialogTitle>
        <DialogDescription>
          {{ t("sidebar.problemLists.renameCategory") }}
        </DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="edit-category-name">{{
            t("sidebar.problemLists.categoryName")
          }}</Label>
          <Input
            id="edit-category-name"
            v-model="editCategoryForm.name"
            @keydown.enter="handleEditCategory"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="isEditCategoryOpen = false"
          :disabled="isEditingCategory"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button @click="handleEditCategory" :disabled="isEditingCategory">
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
  <AlertDialog v-model:open="isDeleteCategoryOpen">
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
          class="bg-destructive text-white hover:bg-destructive/90"
          @click="handleDeleteCategory"
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
  <AlertDialog v-model:open="isDeleteListOpen">
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
          class="bg-destructive text-white hover:bg-destructive/90"
          @click="handleDeleteList"
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
  <Dialog v-model:open="isCreateListOpen">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>{{ t("problem.problemList.create") }}</DialogTitle>
        <DialogDescription>
          {{ t("problem.problemList.create") }}
        </DialogDescription>
      </DialogHeader>
      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="new-list-name">{{ t("common.labels.name") }}</Label>
          <Input
            id="new-list-name"
            v-model="createListForm.name"
            :placeholder="t('problem.problemList.namePlaceholder')"
            @keydown.enter="handleCreateList"
          />
        </div>
        <div class="space-y-2">
          <Label for="new-list-desc">{{
            t("problem.problemList.description")
          }}</Label>
          <Textarea
            id="new-list-desc"
            v-model="createListForm.description"
            :placeholder="t('problem.problemList.descriptionPlaceholder')"
            rows="3"
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
            v-model:checked="createListForm.isPublic"
          />
        </div>
      </div>
      <DialogFooter>
        <Button
          variant="outline"
          @click="isCreateListOpen = false"
          :disabled="isCreatingList"
        >
          {{ t("common.actions.cancel") }}
        </Button>
        <Button
          @click="handleCreateList"
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
