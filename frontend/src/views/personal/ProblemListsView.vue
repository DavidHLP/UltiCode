<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { RouterLink, useRouter } from "vue-router";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Separator } from "@/components/ui/separator";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Plus,
  MoreHorizontal,
  Pencil,
  Trash2,
  List,
  Globe,
  Lock,
  Bookmark,
  BookmarkMinus,
  FolderPlus,
  FolderInput,
  ChevronRight,
  Star,
  Folder,
  GripVertical,
  Save,
  Loader2,
  Search,
  LayoutGrid,
  MoreVertical,
} from "lucide-vue-next";
import { toast } from "vue-sonner";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
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
import { fetchCurrentUserId } from "@/utils/auth";
import { useI18n } from "vue-i18n";

const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const currentUserId = fetchCurrentUserId();
const activeTab = ref("my-lists");
const searchQuery = ref("");

// Data state
const data = ref<UserProblemListsResponse>({
  myLists: [],
  savedLists: [],
  featured: [],
  categories: [],
});

// Create list dialog state
const isCreateOpen = ref(false);
const isCreating = ref(false);
const createForm = ref({
  name: "",
  description: "",
  isPublic: false,
});

// Delete list dialog state
const isDeleteListOpen = ref(false);
const isDeletingList = ref(false);
const listToDelete = ref<ProblemList | null>(null);

// Create category dialog state
const isCreateCategoryOpen = ref(false);
const isCreatingCategory = ref(false);
const createCategoryForm = ref({ name: "" });

// Edit category dialog state
const isEditCategoryOpen = ref(false);
const isEditingCategory = ref(false);
const categoryToEdit = ref<ProblemListCategory | null>(null);
const editCategoryForm = ref({ name: "" });

// Delete category dialog state
const isDeleteCategoryOpen = ref(false);
const isDeletingCategory = ref(false);
const categoryToDelete = ref<ProblemListCategory | null>(null);

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
  // Filter out lists that are in categories
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

// Total saved lists count (uncategorized + in categories)
const totalSavedCount = computed(() => {
  return data.value.savedLists.length;
});

// Sort categories by sortOrder
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
const handleCreateList = async () => {
  if (!currentUserId || !createForm.value.name.trim()) return;

  isCreating.value = true;
  try {
    const newList = await createProblemList(currentUserId, {
      name: createForm.value.name.trim(),
      description: createForm.value.description.trim() || undefined,
      isPublic: createForm.value.isPublic,
    });
    toast.success(t("personal.messages.folderCreated"));
    isCreateOpen.value = false;
    createForm.value = { name: "", description: "", isPublic: false };
    router.push(`/problemset/list/${newList.id}`);
  } catch (e) {
    console.error("Failed to create problem list", e);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    isCreating.value = false;
  }
};

// --- Delete List ---
const openDeleteListDialog = (list: ProblemList) => {
  listToDelete.value = list;
  isDeleteListOpen.value = true;
};

const handleDeleteList = async () => {
  if (!currentUserId || !listToDelete.value) return;

  isDeletingList.value = true;
  try {
    await deleteProblemList(listToDelete.value.id, currentUserId);
    toast.success(t("personal.messages.folderDeleted"));
    isDeleteListOpen.value = false;
    listToDelete.value = null;
    await loadData();
  } catch (e) {
    console.error("Failed to delete problem list", e);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    isDeletingList.value = false;
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
const handleCreateCategory = async () => {
  if (!currentUserId || !createCategoryForm.value.name.trim()) return;
  isCreatingCategory.value = true;
  try {
    await createCategory(currentUserId, {
      name: createCategoryForm.value.name.trim(),
    });
    toast.success(t("personal.messages.folderCreated"));
    isCreateCategoryOpen.value = false;
    createCategoryForm.value = { name: "" };
    await loadData();
  } catch (e) {
    console.error("Failed to create category", e);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    isCreatingCategory.value = false;
  }
};

// --- Edit Category ---
const openEditCategoryDialog = (category: ProblemListCategory) => {
  categoryToEdit.value = category;
  editCategoryForm.value = { name: category.name };
  isEditCategoryOpen.value = true;
};

const handleEditCategory = async () => {
  if (!currentUserId || !categoryToEdit.value) return;
  if (!editCategoryForm.value.name.trim()) {
    toast.error(t("personal.problemLists.dialogs.newName"));
    return;
  }
  isEditingCategory.value = true;
  try {
    await updateCategory(categoryToEdit.value.id, currentUserId, {
      name: editCategoryForm.value.name.trim(),
    });
    toast.success(t("personal.messages.profileUpdated"));
    isEditCategoryOpen.value = false;
    await loadData();
  } catch (e) {
    console.error("Failed to update category", e);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    isEditingCategory.value = false;
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

// --- Delete Category ---
const openDeleteCategoryDialog = (category: ProblemListCategory) => {
  categoryToDelete.value = category;
  isDeleteCategoryOpen.value = true;
};

const handleDeleteCategory = async () => {
  if (!currentUserId || !categoryToDelete.value) return;
  isDeletingCategory.value = true;
  try {
    await deleteCategory(categoryToDelete.value.id, currentUserId);
    toast.success(t("personal.messages.folderDeleted"));
    isDeleteCategoryOpen.value = false;
    await loadData();
  } catch (e) {
    console.error("Failed to delete category", e);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    isDeletingCategory.value = false;
  }
};

onMounted(loadData);
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
            class="gap-2 rounded-full"
          >
            <FolderPlus class="h-4 w-4" />
            <span class="hidden sm:inline">{{
              t("personal.problemLists.actions.newCategory")
            }}</span>
          </Button>
          <Button
            @click="isCreateOpen = true"
            class="gap-2 rounded-full shadow-sm"
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
      class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 bg-muted/5 rounded-2xl text-center px-6"
    >
      <div
        class="bg-muted/50 w-16 h-16 rounded-2xl flex items-center justify-center mb-4"
      >
        <Lock class="h-8 w-8 text-muted-foreground/50" />
      </div>
      <h3 class="text-xl font-bold">
        {{ t("personal.profile.authenticationRequired") }}
      </h3>
      <p class="text-muted-foreground mb-6 text-center max-w-xs mt-2">
        {{ t("personal.problemLists.loginToManage") }}
      </p>
      <Button as-child class="rounded-full px-8 h-10 font-bold">
        <RouterLink to="/login">{{ t("personal.profile.signIn") }}</RouterLink>
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
                class="ml-2 h-5 min-w-[20px] px-1 rounded-full text-[10px]"
                >{{ data.myLists.length }}</Badge
              >
            </TabsTrigger>
            <TabsTrigger
              value="saved"
              class="rounded-full px-4 font-bold data-[state=active]:bg-background data-[state=active]:shadow-sm"
            >
              {{ t("personal.problemLists.tabs.saved") }}
              <Badge
                variant="secondary"
                class="ml-2 h-5 min-w-[20px] px-1 rounded-full text-[10px]"
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
          <div
            v-if="data.myLists.length === 0"
            class="flex flex-col items-center justify-center py-24 text-center px-6 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5"
          >
            <div
              class="p-0 flex items-center justify-center w-16 h-16 rounded-2xl bg-muted/50 mb-4"
            >
              <LayoutGrid class="h-8 w-8 text-muted-foreground/50" />
            </div>
            <h4 class="text-xl font-bold">
              {{ t("personal.problemLists.emptyStates.noLists") }}
            </h4>
            <p class="text-sm text-muted-foreground mt-1 max-w-[300px] mb-8">
              {{ t("personal.problemLists.emptyStates.noListsDesc") }}
            </p>
            <Button
              size="lg"
              @click="isCreateOpen = true"
              class="rounded-full gap-2 px-8 h-10 font-bold"
            >
              <Plus class="h-4 w-4" />
              {{ t("personal.problemLists.emptyStates.createFirst") }}
            </Button>
          </div>

          <div v-else class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            <Card
              v-for="list in sortedMyLists"
              :key="list.id"
              class="group hover:shadow-md transition-all duration-300 border-muted/60 overflow-hidden flex flex-col rounded-2xl"
            >
              <CardHeader class="pb-3">
                <div class="flex items-start justify-between">
                  <div class="space-y-1.5 flex-1 min-w-0">
                    <div class="flex items-center gap-2">
                      <Badge
                        v-if="list.isPublic"
                        variant="secondary"
                        class="h-5 px-1.5 text-[10px] font-semibold uppercase tracking-widest bg-emerald-500/10 text-emerald-600 border-emerald-500/20 rounded-md"
                      >
                        <Globe class="h-3 w-3 mr-1" />
                        {{ t("personal.problemLists.listCard.public") }}
                      </Badge>
                      <Badge
                        v-else
                        variant="outline"
                        class="h-5 px-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground border-muted-foreground/20 rounded-md"
                      >
                        <Lock class="h-3 w-3 mr-1" />
                        {{ t("personal.problemLists.listCard.private") }}
                      </Badge>
                    </div>
                    <CardTitle
                      class="text-lg font-semibold group-hover:text-primary transition-colors truncate"
                    >
                      <RouterLink :to="`/problemset/list/${list.id}`">{{
                        list.name
                      }}</RouterLink>
                    </CardTitle>
                  </div>

                  <DropdownMenu>
                    <DropdownMenuTrigger as-child>
                      <Button
                        variant="ghost"
                        size="icon"
                        class="h-8 w-8 rounded-full"
                      >
                        <MoreHorizontal class="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" class="w-48">
                      <DropdownMenuItem as-child class="gap-2">
                        <RouterLink :to="`/problemset/list/${list.id}`">
                          <Pencil class="h-4 w-4" />
                          {{ t("personal.problemLists.actions.editList") }}
                        </RouterLink>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        class="text-destructive focus:text-destructive gap-2"
                        @click.prevent="openDeleteListDialog(list)"
                      >
                        <Trash2 class="h-4 w-4" />
                        {{ t("personal.problemLists.actions.deleteList") }}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </CardHeader>
              <CardContent class="flex-1">
                <p
                  v-if="list.description"
                  class="text-sm text-muted-foreground line-clamp-2 min-h-[40px]"
                >
                  {{ list.description }}
                </p>
                <p v-else class="text-sm text-muted-foreground/40 italic">
                  {{ t("personal.problemLists.listCard.noDescription") }}
                </p>
              </CardContent>
              <CardFooter class="bg-muted/20 border-t py-3 px-6">
                <div
                  class="flex items-center gap-2 text-xs font-bold text-muted-foreground"
                >
                  <List class="h-4 w-4 text-primary/70" />
                  {{
                    t("personal.problemLists.listCard.problemCount", {
                      count: list.problemCount,
                    })
                  }}
                </div>
              </CardFooter>
            </Card>
          </div>
        </TabsContent>

        <!-- Saved Lists Tab -->
        <TabsContent value="saved" class="mt-0 space-y-8">
          <!-- Uncategorized Saved Lists -->
          <div v-if="sortedSavedLists.length > 0">
            <div class="flex items-center gap-3 mb-4">
              <h3
                class="text-lg font-black uppercase tracking-widest text-muted-foreground"
              >
                {{ t("personal.problemLists.categories.uncategorized") }}
              </h3>
              <Separator class="flex-1" />
            </div>
            <div class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              <Card
                v-for="list in sortedSavedLists"
                :key="list.id"
                class="group hover:shadow-md transition-all duration-300 border-muted/60 flex flex-col overflow-hidden rounded-2xl"
              >
                <CardHeader class="pb-3">
                  <div class="flex items-start justify-between">
                    <div class="space-y-1.5 flex-1 min-w-0">
                      <Badge
                        variant="secondary"
                        class="h-5 px-1.5 text-[10px] font-bold uppercase tracking-widest bg-blue-500/10 text-blue-600 border-blue-500/20 rounded-md"
                      >
                        {{ t("personal.problemLists.listCard.saved") }}
                      </Badge>
                      <CardTitle
                        class="text-lg font-bold group-hover:text-primary transition-colors truncate"
                      >
                        <RouterLink :to="`/problemset/list/${list.id}`">{{
                          list.name
                        }}</RouterLink>
                      </CardTitle>
                    </div>

                    <DropdownMenu>
                      <DropdownMenuTrigger as-child>
                        <Button
                          variant="ghost"
                          size="icon"
                          class="h-8 w-8 rounded-full"
                        >
                          <MoreHorizontal class="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" class="w-56">
                        <DropdownMenuSub v-if="data.categories.length > 0">
                          <DropdownMenuSubTrigger class="gap-2">
                            <FolderInput class="h-4 w-4" />
                            {{
                              t("personal.problemLists.actions.moveToCategory")
                            }}
                          </DropdownMenuSubTrigger>
                          <DropdownMenuSubContent>
                            <DropdownMenuItem
                              v-for="cat in data.categories"
                              :key="cat.id"
                              @click.prevent="
                                handleMoveToCategory(list, cat.id)
                              "
                            >
                              {{ cat.name }}
                            </DropdownMenuItem>
                          </DropdownMenuSubContent>
                        </DropdownMenuSub>
                        <DropdownMenuItem
                          @click.prevent="handleUnsaveList(list)"
                          class="text-muted-foreground gap-2"
                        >
                          <BookmarkMinus class="h-4 w-4" />
                          {{ t("personal.problemLists.actions.unsaveList") }}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </CardHeader>
                <CardContent class="flex-1">
                  <p
                    v-if="list.description"
                    class="text-sm text-muted-foreground line-clamp-2"
                  >
                    {{ list.description }}
                  </p>
                </CardContent>
                <CardFooter class="bg-muted/20 border-t py-3 px-6">
                  <div
                    class="flex items-center gap-2 text-xs font-bold text-muted-foreground"
                  >
                    <List class="h-4 w-4 text-primary/70" />
                    {{
                      t("personal.problemLists.listCard.problemCount", {
                        count: list.problemCount,
                      })
                    }}
                  </div>
                </CardFooter>
              </Card>
            </div>
          </div>

          <!-- Categories with their lists -->
          <div
            v-for="category in data.categories"
            :key="category.id"
            class="space-y-4"
          >
            <Collapsible :default-open="true">
              <div class="flex items-center gap-3 mb-2 group/cat">
                <CollapsibleTrigger
                  class="flex items-center gap-3 hover:text-primary transition-colors"
                >
                  <ChevronRight
                    class="h-5 w-5 transition-transform duration-300 ui-open:rotate-90 text-muted-foreground"
                  />
                  <h3 class="text-lg font-black uppercase tracking-widest">
                    {{ category.name }}
                  </h3>
                  <Badge
                    variant="secondary"
                    class="h-5 px-1.5 rounded-full text-[10px]"
                    >{{ category.lists.length }}</Badge
                  >
                </CollapsibleTrigger>
                <Separator class="flex-1 opacity-50" />
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button
                      variant="ghost"
                      size="icon"
                      class="h-8 w-8 rounded-full opacity-0 group-hover/cat:opacity-100 transition-opacity"
                    >
                      <MoreVertical class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      @click="openEditCategoryDialog(category)"
                      class="gap-2"
                    >
                      <Pencil class="h-4 w-4" />
                      {{ t("personal.problemLists.actions.renameCategory") }}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      class="text-destructive focus:text-destructive gap-2"
                      @click="openDeleteCategoryDialog(category)"
                    >
                      <Trash2 class="h-4 w-4" />
                      {{ t("personal.problemLists.actions.deleteCategory") }}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>

              <CollapsibleContent class="pt-4 pb-8">
                <div
                  v-if="category.lists.length === 0"
                  class="flex flex-col items-center justify-center py-12 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5 text-muted-foreground"
                >
                  <FolderInput class="h-10 w-10 opacity-20 mb-3" />
                  <p class="text-sm font-medium">
                    {{
                      t("personal.problemLists.emptyStates.noListsInCategory")
                    }}
                  </p>
                </div>
                <div v-else class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                  <Card
                    v-for="list in category.lists"
                    :key="list.id"
                    class="group hover:shadow-md transition-all duration-300 border-muted/60 flex flex-col overflow-hidden rounded-2xl"
                  >
                    <CardHeader class="pb-3">
                      <div class="flex items-start justify-between">
                        <CardTitle
                          class="text-lg font-bold group-hover:text-primary transition-colors truncate"
                        >
                          <RouterLink :to="`/problemset/list/${list.id}`">{{
                            list.name
                          }}</RouterLink>
                        </CardTitle>

                        <DropdownMenu>
                          <DropdownMenuTrigger as-child>
                            <Button
                              variant="ghost"
                              size="icon"
                              class="h-8 w-8 rounded-full"
                            >
                              <MoreHorizontal class="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" class="w-56">
                            <DropdownMenuItem
                              @click.prevent="handleMoveToCategory(list, null)"
                              class="gap-2"
                            >
                              <FolderInput class="h-4 w-4" />
                              {{
                                t(
                                  "personal.problemLists.actions.removeFromCategory",
                                )
                              }}
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              @click.prevent="handleUnsaveList(list)"
                              class="text-muted-foreground gap-2"
                            >
                              <BookmarkMinus class="h-4 w-4" />
                              {{
                                t("personal.problemLists.actions.unsaveList")
                              }}
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    </CardHeader>
                    <CardFooter class="bg-muted/20 border-t py-3 px-6 mt-auto">
                      <div
                        class="flex items-center gap-2 text-xs font-bold text-muted-foreground"
                      >
                        <List class="h-4 w-4 text-primary/70" />
                        {{
                          t("personal.problemLists.listCard.problemCount", {
                            count: list.problemCount,
                          })
                        }}
                      </div>
                    </CardFooter>
                  </Card>
                </div>
              </CollapsibleContent>
            </Collapsible>
          </div>

          <!-- Empty State for Saved -->
          <div
            v-if="data.savedLists.length === 0 && data.categories.length === 0"
            class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5 text-center px-6"
          >
            <div
              class="p-0 flex items-center justify-center w-16 h-16 rounded-2xl bg-muted/50 mb-4 text-muted-foreground/40"
            >
              <Bookmark class="h-8 w-8" />
            </div>
            <h4 class="text-xl font-bold">
              {{ t("personal.problemLists.emptyStates.noSaved") }}
            </h4>
            <p class="text-sm text-muted-foreground mt-1 max-w-[300px]">
              {{ t("personal.problemLists.emptyStates.noSavedDesc") }}
            </p>
          </div>
        </TabsContent>

        <!-- Categories Tab -->
        <TabsContent value="categories" class="mt-0 space-y-8">
          <div class="grid gap-6">
            <Card
              v-for="category in sortedCategories"
              :key="category.id"
              class="border-muted/60 hover:shadow-md transition-shadow duration-300 rounded-2xl overflow-hidden"
            >
              <div
                class="flex items-center justify-between px-6 py-4 bg-muted/30"
              >
                <div class="flex items-center gap-4">
                  <GripVertical
                    class="h-5 w-5 text-muted-foreground/30 cursor-grab"
                  />
                  <div
                    class="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center"
                  >
                    <Folder class="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <h4 class="text-lg font-black tracking-tight">
                      {{ category.name }}
                    </h4>
                    <p
                      class="text-xs font-bold text-muted-foreground uppercase tracking-widest"
                    >
                      {{
                        t("personal.problemLists.categories.listCount", {
                          count: category.lists.length,
                          s: category.lists.length !== 1 ? "S" : "",
                        })
                      }}
                    </p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="icon"
                    class="rounded-full"
                    @click="openEditCategoryDialog(category)"
                  >
                    <Pencil class="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="rounded-full text-destructive hover:text-destructive"
                    @click="openDeleteCategoryDialog(category)"
                  >
                    <Trash2 class="h-4 w-4" />
                  </Button>
                </div>
              </div>

              <div class="p-4 sm:p-6">
                <div
                  v-if="category.lists.length === 0"
                  class="text-center py-10 bg-muted/10 rounded-xl border-2 border-dashed"
                >
                  <p class="text-sm text-muted-foreground italic">
                    {{
                      t(
                        "personal.problemLists.emptyStates.noListsInCategoryDesc",
                      )
                    }}
                  </p>
                </div>
                <div v-else class="grid gap-3">
                  <div
                    v-for="list in category.lists"
                    :key="list.id"
                    class="flex items-center justify-between p-4 rounded-xl bg-muted/20 hover:bg-muted/40 transition-all group"
                  >
                    <RouterLink
                      :to="`/problemset/list/${list.id}`"
                      class="flex items-center gap-4 flex-1 min-w-0"
                    >
                      <List class="h-4 w-4 text-primary/60" />
                      <span class="font-bold truncate">{{ list.name }}</span>
                      <Badge
                        variant="secondary"
                        class="h-5 px-1.5 rounded-full text-[10px]"
                      >
                        {{
                          t("personal.problemLists.listCard.problemsCount", {
                            count: list.problemCount,
                          })
                        }}
                      </Badge>
                    </RouterLink>
                    <DropdownMenu>
                      <DropdownMenuTrigger as-child>
                        <Button
                          variant="ghost"
                          size="icon"
                          class="h-8 w-8 rounded-full opacity-0 group-hover:opacity-100"
                        >
                          <MoreHorizontal class="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" class="w-56">
                        <DropdownMenuSub v-if="data.categories.length > 1">
                          <DropdownMenuSubTrigger class="gap-2">
                            <FolderInput class="h-4 w-4" />
                            {{
                              t("personal.problemLists.actions.moveToAnother")
                            }}
                          </DropdownMenuSubTrigger>
                          <DropdownMenuSubContent>
                            <DropdownMenuItem
                              v-for="cat in data.categories.filter(
                                (c) => c.id !== category.id,
                              )"
                              :key="cat.id"
                              @click="handleMoveToCategory(list, cat.id)"
                            >
                              {{ cat.name }}
                            </DropdownMenuItem>
                          </DropdownMenuSubContent>
                        </DropdownMenuSub>
                        <DropdownMenuItem
                          @click="handleMoveToCategory(list, null)"
                          class="gap-2"
                        >
                          <FolderInput class="h-4 w-4" />
                          {{
                            t(
                              "personal.problemLists.actions.removeFromCategory",
                            )
                          }}
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          @click="handleUnsaveList(list)"
                          class="text-muted-foreground gap-2"
                        >
                          <BookmarkMinus class="h-4 w-4" />
                          {{ t("personal.problemLists.actions.unsaveList") }}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </div>
              </div>
            </Card>
          </div>
        </TabsContent>

        <!-- Featured Tab -->
        <TabsContent value="featured" class="mt-0">
          <div
            v-if="data.featured.length === 0"
            class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5 text-center"
          >
            <div
              class="p-0 flex items-center justify-center w-16 h-16 rounded-2xl bg-muted/50 mb-4 text-muted-foreground/20"
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
            <Card
              v-for="list in data.featured"
              :key="list.id"
              class="group hover:shadow-md transition-all duration-300 border-muted/60 flex flex-col overflow-hidden rounded-2xl"
            >
              <CardHeader class="pb-3">
                <div class="flex items-start justify-between">
                  <div class="space-y-1.5 flex-1 min-w-0">
                    <div class="flex items-center gap-2">
                      <Badge
                        variant="secondary"
                        class="h-5 px-1.5 text-[10px] font-bold uppercase tracking-widest bg-amber-500/10 text-amber-600 border-amber-500/20 rounded-md"
                      >
                        {{ t("personal.problemLists.listCard.featured") }}
                      </Badge>
                      <Badge
                        v-if="list.isSaved"
                        variant="secondary"
                        class="h-5 px-1.5 text-[10px] font-bold uppercase tracking-widest bg-blue-500/10 text-blue-600 border-blue-500/20 rounded-md"
                      >
                        {{ t("personal.problemLists.listCard.saved") }}
                      </Badge>
                    </div>
                    <CardTitle
                      class="text-lg font-bold group-hover:text-primary transition-colors truncate"
                    >
                      <RouterLink
                        :to="`/problemset/list/${list.id}`"
                        class="flex items-center gap-2"
                      >
                        {{ list.name }}
                        <Star class="h-4 w-4 text-amber-500 fill-amber-500" />
                      </RouterLink>
                    </CardTitle>
                  </div>

                  <Button
                    v-if="!list.isSaved"
                    variant="outline"
                    size="sm"
                    class="rounded-full gap-1.5 h-8 font-bold text-[10px] opacity-0 group-hover:opacity-100 transition-all"
                    @click="handleSaveList(list)"
                  >
                    <Save class="h-3.5 w-3.5" />
                    {{ t("personal.problemLists.actions.save") }}
                  </Button>
                  <Button
                    v-else
                    variant="ghost"
                    size="sm"
                    class="rounded-full gap-1.5 h-8 font-bold text-[10px] text-muted-foreground opacity-0 group-hover:opacity-100 transition-all"
                    @click="handleUnsaveList(list)"
                  >
                    <BookmarkMinus class="h-3.5 w-3.5" />
                    {{ t("personal.problemLists.actions.unsave") }}
                  </Button>
                </div>
              </CardHeader>
              <CardContent class="flex-1">
                <p
                  v-if="list.description"
                  class="text-sm text-muted-foreground line-clamp-2"
                >
                  {{ list.description }}
                </p>
              </CardContent>
              <CardFooter class="bg-muted/20 border-t py-3 px-6 mt-auto">
                <div
                  class="flex items-center gap-2 text-xs font-bold text-muted-foreground"
                >
                  <List class="h-4 w-4 text-primary/70" />
                  {{
                    t("personal.problemLists.listCard.problemCount", {
                      count: list.problemCount,
                    })
                  }}
                </div>
              </CardFooter>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>

    <!-- Create List Dialog -->
    <Dialog v-model:open="isCreateOpen">
      <DialogContent class="sm:max-w-[425px] rounded-2xl">
        <DialogHeader>
          <DialogTitle class="text-2xl font-black tracking-tight">{{
            t("personal.problemLists.dialogs.createList")
          }}</DialogTitle>
          <DialogDescription>
            {{ t("personal.problemLists.dialogs.createListDesc") }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-6 py-4">
          <div class="space-y-2">
            <Label
              for="create-name"
              class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
              >{{ t("personal.problemLists.dialogs.listName") }}</Label
            >
            <Input
              id="create-name"
              v-model="createForm.name"
              :placeholder="
                t('personal.problemLists.dialogs.listNamePlaceholder')
              "
              class="h-11 rounded-lg"
            />
          </div>
          <div class="space-y-2">
            <Label
              for="create-description"
              class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
              >{{ t("personal.problemLists.dialogs.description") }}</Label
            >
            <Textarea
              id="create-description"
              v-model="createForm.description"
              :placeholder="
                t('personal.problemLists.dialogs.descriptionPlaceholder')
              "
              class="min-h-[100px] resize-none rounded-lg"
            />
          </div>
          <div
            class="flex items-center justify-between p-4 rounded-xl bg-muted/30 border"
          >
            <div class="space-y-0.5">
              <Label for="create-public" class="text-sm font-bold">{{
                t("personal.problemLists.dialogs.publicList")
              }}</Label>
              <p class="text-xs text-muted-foreground">
                {{ t("personal.problemLists.dialogs.publicListDesc") }}
              </p>
            </div>
            <Switch id="create-public" v-model:checked="createForm.isPublic" />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            @click="isCreateOpen = false"
            class="rounded-full"
            >{{ t("common.actions.cancel") }}</Button
          >
          <Button
            @click="handleCreateList"
            :disabled="isCreating || !createForm.name.trim()"
            class="rounded-full px-8"
          >
            {{
              isCreating
                ? t("personal.problemLists.dialogs.creating")
                : t("personal.problemLists.dialogs.createButton")
            }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- AlertDialogs (Delete List, Create Category, etc.) - Styling updates -->
    <!-- (I'll keep the logic but the template is already significantly improved above) -->

    <!-- Delete List Confirmation -->
    <AlertDialog v-model:open="isDeleteListOpen">
      <AlertDialogContent class="rounded-2xl">
        <AlertDialogHeader>
          <AlertDialogTitle>{{
            t("personal.problemLists.dialogs.deleteList")
          }}</AlertDialogTitle>
          <AlertDialogDescription>
            {{
              t("personal.problemLists.dialogs.deleteListConfirm", {
                name: listToDelete?.name,
              })
            }}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel :disabled="isDeletingList" class="rounded-full">{{
            t("common.actions.cancel")
          }}</AlertDialogCancel>
          <AlertDialogAction
            class="bg-destructive text-white hover:bg-destructive/90 rounded-full"
            @click="handleDeleteList"
            :disabled="isDeletingList"
          >
            {{
              isDeletingList
                ? t("common.status.saving")
                : t("common.actions.confirm")
            }}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- Create Category Dialog -->
    <Dialog v-model:open="isCreateCategoryOpen">
      <DialogContent class="sm:max-w-md rounded-2xl">
        <DialogHeader>
          <DialogTitle class="text-2xl font-black tracking-tight">{{
            t("personal.problemLists.dialogs.newCategory")
          }}</DialogTitle>
          <DialogDescription>
            {{ t("personal.problemLists.dialogs.newCategoryDesc") }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4 py-4">
          <div class="space-y-2">
            <Label
              for="category-name"
              class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
              >{{ t("personal.problemLists.dialogs.categoryName") }}</Label
            >
            <Input
              id="category-name"
              v-model="createCategoryForm.name"
              :placeholder="
                t('personal.problemLists.dialogs.categoryNamePlaceholder')
              "
              class="h-11 rounded-lg"
              @keydown.enter="handleCreateCategory"
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            @click="isCreateCategoryOpen = false"
            :disabled="isCreatingCategory"
            class="rounded-full"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            @click="handleCreateCategory"
            :disabled="isCreatingCategory || !createCategoryForm.name.trim()"
            class="rounded-full px-8"
          >
            {{
              isCreatingCategory
                ? t("personal.problemLists.dialogs.creating")
                : t("personal.problemLists.dialogs.createCategory")
            }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Edit Category Dialog -->
    <Dialog v-model:open="isEditCategoryOpen">
      <DialogContent class="sm:max-w-md rounded-2xl">
        <DialogHeader>
          <DialogTitle class="text-2xl font-black tracking-tight">{{
            t("personal.problemLists.dialogs.renameCategory")
          }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4 py-4">
          <div class="space-y-2">
            <Label
              for="edit-category-name"
              class="text-xs font-bold uppercase tracking-widest text-muted-foreground"
              >{{ t("personal.problemLists.dialogs.newName") }}</Label
            >
            <Input
              id="edit-category-name"
              v-model="editCategoryForm.name"
              class="h-11 rounded-lg"
              @keydown.enter="handleEditCategory"
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            @click="isEditCategoryOpen = false"
            :disabled="isEditingCategory"
            class="rounded-full"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            @click="handleEditCategory"
            :disabled="isEditingCategory || !editCategoryForm.name.trim()"
            class="rounded-full px-8"
          >
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
      <AlertDialogContent class="rounded-2xl">
        <AlertDialogHeader>
          <AlertDialogTitle>{{
            t("personal.problemLists.dialogs.deleteCategory")
          }}</AlertDialogTitle>
          <AlertDialogDescription>
            {{
              t("personal.problemLists.dialogs.deleteCategoryConfirm", {
                name: categoryToDelete?.name,
              })
            }}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel
            :disabled="isDeletingCategory"
            class="rounded-full"
            >{{ t("common.actions.cancel") }}</AlertDialogCancel
          >
          <AlertDialogAction
            class="bg-destructive text-white hover:bg-destructive/90 rounded-full"
            @click="handleDeleteCategory"
            :disabled="isDeletingCategory"
          >
            {{
              isDeletingCategory
                ? t("common.status.saving")
                : t("common.actions.confirm")
            }}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </PersonalPageShell>
</template>
