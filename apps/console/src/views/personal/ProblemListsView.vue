<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Search,
  Plus,
  FolderPlus,
  Loader2,
  Lock,
  Star,
  Save,
  BookmarkMinus,
  List,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import MyListsTab from "./components/MyListsTab.vue";
import SavedListsTab from "./components/SavedListsTab.vue";
import CategoriesTab from "./components/CategoriesTab.vue";
import CreateListDialog from "./components/CreateListDialog.vue";
import DeleteListDialog from "./components/DeleteListDialog.vue";
import CreateCategoryDialog from "./components/CreateCategoryDialog.vue";
import EditCategoryDialog from "./components/EditCategoryDialog.vue";
import DeleteCategoryDialog from "./components/DeleteCategoryDialog.vue";
import { useProblemLists } from "./composables/useProblemLists";
import type { ProblemList, ProblemListCategory } from "@/types/problem-list";

const router = useRouter();
const { t } = useI18n();

const {
  loading,
  currentUserId,
  searchQuery,
  data,
  sortedOwnLists,
  sortedSavedLists,
  totalSavedCount,
  sortedCategories,
  handleCreateList,
  handleDeleteList,
  handleUnsaveList,
  handleSaveList,
  handleMoveToCategory,
  handleCreateCategory,
  handleEditCategory,
  handleDeleteCategory,
} = useProblemLists();

const activeTab = ref("my-lists");

// Dialog state (D-04: stays in parent)
const isCreateOpen = ref(false);
const isCreating = ref(false);
const isDeleteListOpen = ref(false);
const isDeletingList = ref(false);
const listToDelete = ref<ProblemList | null>(null);

const isCreateCategoryOpen = ref(false);
const isCreatingCategory = ref(false);

const isEditCategoryOpen = ref(false);
const isEditingCategory = ref(false);
const categoryToEdit = ref<ProblemListCategory | null>(null);

const isDeleteCategoryOpen = ref(false);
const isDeletingCategory = ref(false);
const categoryToDelete = ref<ProblemListCategory | null>(null);

// --- Create List ---
function onCreateList(formData: {
  name: string;
  description: string;
  isPublic: boolean;
}) {
  isCreating.value = true;
  handleCreateList(
    formData,
    (newListId: string) => {
      isCreating.value = false;
      router.push(`/problemset/list/${newListId}`);
    },
    () => {
      isCreating.value = false;
      isCreateOpen.value = false;
    },
  );
}

// --- Delete List ---
function openDeleteListDialog(list: ProblemList) {
  listToDelete.value = list;
  isDeleteListOpen.value = true;
}

async function onDeleteListConfirm() {
  if (!listToDelete.value) return;
  isDeletingList.value = true;
  await handleDeleteList(listToDelete.value);
  isDeletingList.value = false;
  isDeleteListOpen.value = false;
  listToDelete.value = null;
}

// --- Save Featured List ---
function onSaveList(list: ProblemList) {
  handleSaveList(list);
}

// --- Create Category ---
function onCreateCategory(formData: { name: string }) {
  isCreatingCategory.value = true;
  handleCreateCategory(formData, () => {
    isCreatingCategory.value = false;
    isCreateCategoryOpen.value = false;
  });
}

// --- Edit Category ---
function openEditCategoryDialog(category: ProblemListCategory) {
  categoryToEdit.value = category;
  isEditCategoryOpen.value = true;
}

function onEditCategory(name: string) {
  if (!categoryToEdit.value) return;
  isEditingCategory.value = true;
  handleEditCategory(categoryToEdit.value, name, () => {
    isEditingCategory.value = false;
    isEditCategoryOpen.value = false;
  });
}

// --- Delete Category ---
function openDeleteCategoryDialog(category: ProblemListCategory) {
  categoryToDelete.value = category;
  isDeleteCategoryOpen.value = true;
}

async function onDeleteCategoryConfirm() {
  if (!categoryToDelete.value) return;
  isDeletingCategory.value = true;
  await handleDeleteCategory(categoryToDelete.value);
  isDeletingCategory.value = false;
  isDeleteCategoryOpen.value = false;
  categoryToDelete.value = null;
}

// --- Categories Tab helpers (list-level operations need list ID + action) ---
function onCategoryMoveToCategory(listId: string, categoryId: string | null) {
  const list = [
    ...data.value.savedLists,
    ...data.value.categories.flatMap((c) => c.lists),
  ].find((l) => l.id === listId);
  if (list) handleMoveToCategory(list, categoryId);
}

function onCategoryUnsave(listId: string) {
  const list = [
    ...data.value.savedLists,
    ...data.value.categories.flatMap((c) => c.lists),
  ].find((l) => l.id === listId);
  if (list) handleUnsaveList(list);
}
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.problemLists.title')"
      :description="t('personal.problemLists.subtitle')"
    >
      <template #actions>
        <div class="flex items-center gap-3">
          <Button
            variant="outline"
            @click="isCreateCategoryOpen = true"
            class="gap-2"
          >
            <FolderPlus class="h-4 w-4" />
            <span class="hidden sm:inline">{{
              t("personal.problemLists.actions.newCategory")
            }}</span>
          </Button>
          <Button
            @click="isCreateOpen = true"
            class="gap-2 shadow-sm"
          >
            <Plus class="h-4 w-4" />
            <span>{{ t("personal.problemLists.actions.newList") }}</span>
          </Button>
        </div>
      </template>
    </PersonalPageHeader>

    <!-- Loading State -->
    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-20 gap-4"
    >
      <Loader2 class="h-10 w-10 animate-spin text-primary" />
      <p class="text-sm text-muted-foreground">
        {{ t("personal.problemLists.loadingLists") }}
      </p>
    </div>

    <!-- Not Logged In State -->
    <div
      v-else-if="!currentUserId"
      class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 bg-muted/5 rounded-none text-center px-6"
    >
      <div
        class="bg-muted/50 w-16 h-16 rounded-none flex items-center justify-center mb-4"
      >
        <Lock class="h-8 w-8 text-muted-foreground/50" />
      </div>
      <h3 class="text-xl font-bold">
        {{ t("personal.profile.authenticationRequired") }}
      </h3>
      <p class="text-muted-foreground mb-6 text-center max-w-xs mt-2">
        {{ t("personal.problemLists.loginToManage") }}
      </p>
      <Button as-child class="px-8 h-10 font-bold">
        <router-link to="/login">{{
          t("personal.profile.signIn")
        }}</router-link>
      </Button>
    </div>

    <!-- Main Content -->
    <div v-else class="space-y-6">
      <Tabs v-model="activeTab" class="w-full">
        <div
          class="flex flex-col sm:flex-row sm:items-center justify-between gap-4"
        >
          <TabsList class="bg-muted/50 p-1 h-11 rounded-full">
            <TabsTrigger
              value="my-lists"
              class="rounded-full px-4 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("personal.problemLists.tabs.myLists") }}
              <Badge
                variant="secondary"
                class="ml-2 h-5 min-w-[20px] px-1 rounded-full text-2xs"
                >{{ data.ownLists.length }}</Badge
              >
            </TabsTrigger>
            <TabsTrigger
              value="saved"
              class="rounded-full px-4 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("personal.problemLists.tabs.saved") }}
              <Badge
                variant="secondary"
                class="ml-2 h-5 min-w-[20px] px-1 rounded-full text-2xs"
                >{{ totalSavedCount }}</Badge
              >
            </TabsTrigger>
            <TabsTrigger
              value="categories"
              class="rounded-full px-4 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("personal.problemLists.tabs.categories") }}
            </TabsTrigger>
            <TabsTrigger
              value="featured"
              class="rounded-full px-4 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("personal.problemLists.tabs.featured") }}
            </TabsTrigger>
          </TabsList>

          <div class="relative w-full sm:w-64">
            <Search
              class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
            />
            <Input
              v-model="searchQuery"
              :placeholder="
                t('personal.problemLists.dialogs.listNamePlaceholder')
              "
              class="pl-10 rounded-full h-10 border-muted-foreground/20 focus:ring-primary/20"
            />
          </div>
        </div>

        <!-- My Lists Tab -->
        <TabsContent value="my-lists" class="mt-0">
          <MyListsTab
            :lists="sortedOwnLists"
            @delete="openDeleteListDialog"
            @create="isCreateOpen = true"
          />
        </TabsContent>

        <!-- Saved Lists Tab -->
        <TabsContent value="saved" class="mt-0">
          <SavedListsTab
            :saved-lists="sortedSavedLists"
            :categories="data.categories"
            :all-saved-count="totalSavedCount"
            @unsave="handleUnsaveList"
            @move-to-category="handleMoveToCategory"
            @edit-category="openEditCategoryDialog"
            @delete-category="openDeleteCategoryDialog"
          />
        </TabsContent>

        <!-- Categories Tab -->
        <TabsContent value="categories" class="mt-0">
          <CategoriesTab
            :categories="sortedCategories"
            :all-categories="data.categories"
            @edit="openEditCategoryDialog"
            @delete="openDeleteCategoryDialog"
            @move-to-category="onCategoryMoveToCategory"
            @unsave="onCategoryUnsave"
          />
        </TabsContent>

        <!-- Featured Tab -->
        <TabsContent value="featured" class="mt-0">
          <div
            v-if="data.featuredLists.length === 0"
            class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-center"
          >
            <div
              class="p-0 flex items-center justify-center w-16 h-16 rounded-none bg-muted/50 mb-4 text-muted-foreground/20"
            >
              <Star class="h-8 w-8" />
            </div>
            <h4 class="text-xl font-bold">
              {{ t("personal.problemLists.emptyStates.noFeatured") }}
            </h4>
            <p class="text-muted-foreground mt-2">
              {{ t("personal.problemLists.emptyStates.noFeaturedDesc") }}
            </p>
          </div>

          <div v-else class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            <div
              v-for="list in data.featuredLists"
              :key="list.id"
              class="group hover:shadow-[var(--shadow-float)] transition-all duration-300 border-muted/60 flex flex-col overflow-hidden rounded-none bg-background text-foreground"
            >
              <div class="pb-3 px-6 pt-6">
                <div class="flex items-start justify-between">
                  <div class="space-y-1.5 flex-1 min-w-0">
                    <div class="flex items-center gap-2">
                      <span
                        class="h-5 px-1.5 text-2xs font-bold uppercase tracking-widest bg-[var(--status-warning-mark)]/10 text-foreground-strong border border-[var(--status-warning-mark)]/20 rounded-none inline-flex items-center"
                      >
                        {{ t("personal.problemLists.listCard.featured") }}
                      </span>
                      <span
                        v-if="list.isSaved"
                        class="h-5 px-1.5 text-2xs font-bold uppercase tracking-widest bg-[var(--primary)]/10 text-[var(--primary)] border border-[var(--primary)]/20 rounded-none inline-flex items-center"
                      >
                        {{ t("personal.problemLists.listCard.saved") }}
                      </span>
                    </div>
                    <h3
                      class="text-lg font-bold group-hover:text-primary transition-colors truncate"
                    >
                      <router-link
                        :to="`/problemset/list/${list.id}`"
                        class="flex items-center gap-2"
                      >
                        {{ list.name }}
                        <Star
                          class="h-4 w-4 text-[var(--status-warning-mark)] fill-[var(--status-warning-mark)]"
                        />
                      </router-link>
                    </h3>
                  </div>

                  <button
                    v-if="!list.isSaved"
                    class="rounded-full gap-1.5 h-8 font-bold text-2xs opacity-0 group-hover:opacity-100 transition-all border border-border bg-background hover:bg-muted px-2 inline-flex items-center"
                    @click="onSaveList(list)"
                  >
                    <Save class="h-3.5 w-3.5" />
                    {{ t("personal.problemLists.actions.save") }}
                  </button>
                  <button
                    v-else
                    class="rounded-full gap-1.5 h-8 font-bold text-2xs text-muted-foreground opacity-0 group-hover:opacity-100 transition-all bg-transparent hover:bg-muted px-2 inline-flex items-center"
                    @click="handleUnsaveList(list)"
                  >
                    <BookmarkMinus class="h-3.5 w-3.5" />
                    {{ t("personal.problemLists.actions.unsave") }}
                  </button>
                </div>
              </div>
              <div class="flex-1 px-6">
                <p
                  v-if="list.description"
                  class="text-sm text-muted-foreground line-clamp-2"
                >
                  {{ list.description }}
                </p>
              </div>
              <div
                class="bg-muted/20 border-t py-3 px-6 mt-auto flex items-center gap-2 text-xs font-bold text-muted-foreground"
              >
                <List class="h-4 w-4 text-primary/70" />
                {{
                  t("personal.problemLists.listCard.problemCount", {
                    count: list.problemCount,
                  })
                }}
              </div>
            </div>
          </div>
        </TabsContent>
      </Tabs>
    </div>

    <!-- Create List Dialog -->
    <CreateListDialog
      :open="isCreateOpen"
      :loading="isCreating"
      @update:open="isCreateOpen = $event"
      @submit="onCreateList"
    />

    <!-- Delete List Confirmation -->
    <DeleteListDialog
      :open="isDeleteListOpen"
      :list-name="listToDelete?.name"
      :loading="isDeletingList"
      @update:open="isDeleteListOpen = $event"
      @confirm="onDeleteListConfirm"
    />

    <!-- Create Category Dialog -->
    <CreateCategoryDialog
      :open="isCreateCategoryOpen"
      :loading="isCreatingCategory"
      @update:open="isCreateCategoryOpen = $event"
      @submit="onCreateCategory"
    />

    <!-- Edit Category Dialog -->
    <EditCategoryDialog
      :open="isEditCategoryOpen"
      :loading="isEditingCategory"
      :category-name="categoryToEdit?.name ?? ''"
      @update:open="isEditCategoryOpen = $event"
      @submit="onEditCategory"
    />

    <!-- Delete Category Confirmation -->
    <DeleteCategoryDialog
      :open="isDeleteCategoryOpen"
      :category-name="categoryToDelete?.name"
      :loading="isDeletingCategory"
      @update:open="isDeleteCategoryOpen = $event"
      @confirm="onDeleteCategoryConfirm"
    />
  </PersonalPageShell>
</template>
