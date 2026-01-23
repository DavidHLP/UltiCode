<script setup lang="ts">
import { ref, computed, watch } from "vue";
import {
  Bookmark,
  BookmarkCheck,
  FolderPlus,
  Loader2,
  Check,
} from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuSeparator,
  DropdownMenuItem,
  DropdownMenuLabel,
} from "@/components/ui/dropdown-menu";
import ActionItem from "./ActionItem.vue";
import { useBookmarkStore } from "@/stores/bookmark";
import {
  addBookmark,
  removeBookmarkByTarget,
  getBookmarkFolders,
} from "@/api/bookmark";
import {
  getUserListsForProblem,
  batchAddProblemToLists,
  batchRemoveProblemFromLists,
  createProblemList,
  type ProblemListWithStatus,
} from "@/api/problem-list";
import type { BookmarkType } from "@/types/bookmark";
import { toast } from "vue-sonner";
import { isAuthenticated, fetchCurrentUserId } from "@/utils/auth";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  problemId: number;
  targetType: BookmarkType;
  variant?: "default" | "ghost" | "outline";
  size?: "default" | "sm" | "icon";
  count?: number;
}>();

const emit = defineEmits<{
  change: [isFavorite: boolean, folders: string[], listIds: string[]];
}>();

const { t } = useI18n();

const bookmarkStore = useBookmarkStore();

// Bookmark folder state
const itemFolders = ref<string[]>([]);

// Problem list state
const userLists = ref<ProblemListWithStatus[]>([]);

const isLoading = ref(false);
const isOpen = ref(false);
const isAuthed = ref(false);
const showCreateDialog = ref(false);
const newListName = ref("");
const isCreating = ref(false);

// Check if problem is in default folder (favorited)
const isFavorited = computed(() => {
  const defaultId = bookmarkStore.defaultFolder?.id;
  return defaultId ? itemFolders.value.includes(defaultId) : false;
});

// Track which lists contain this problem
const selectedListIds = computed(() =>
  userLists.value.filter((list) => list.containsProblem).map((list) => list.id),
);

// Combined state: is saved if in any folder or any list
const isSaved = computed(
  () => itemFolders.value.length > 0 || selectedListIds.value.length > 0,
);

async function loadData() {
  if (!isOpen.value) return;

  isAuthed.value = isAuthenticated();
  if (!isAuthed.value) {
    itemFolders.value = [];
    userLists.value = [];
    isLoading.value = false;
    return;
  }

  const userId = fetchCurrentUserId();
  if (!userId) {
    itemFolders.value = [];
    userLists.value = [];
    isLoading.value = false;
    return;
  }

  isLoading.value = true;
  try {
    // Load bookmark folders in parallel
    await bookmarkStore.loadFolders();
    const [folders, lists] = await Promise.all([
      getBookmarkFolders(props.targetType, props.problemId.toString()),
      getUserListsForProblem(userId, props.problemId),
    ]);

    itemFolders.value = folders;
    userLists.value = lists;
  } catch (error) {
    console.error("Failed to load data:", error);
    toast.error(t("problem.save.toast.loadFailed"));
  } finally {
    isLoading.value = false;
  }
}

async function toggleFolder(folderId: string) {
  if (!isAuthenticated()) {
    toast.error(t("problem.save.toast.loginRequired"));
    return;
  }

  const isInFolder = itemFolders.value.includes(folderId);

  try {
    if (isInFolder) {
      await removeBookmarkByTarget(
        folderId,
        props.targetType,
        props.problemId.toString(),
      );
      itemFolders.value = itemFolders.value.filter((id) => id !== folderId);
      bookmarkStore.updateItemCount(folderId, -1);
    } else {
      await addBookmark(folderId, {
        targetId: props.problemId.toString(),
        targetType: props.targetType,
      });
      itemFolders.value.push(folderId);
      bookmarkStore.updateItemCount(folderId, 1);
    }

    emitChange();
  } catch (error) {
    console.error("Failed to toggle bookmark folder:", error);
    toast.error(t("bookmark.toasts.updateFailed"));
  }
}

async function toggleList(listId: string) {
  if (!isAuthenticated()) {
    toast.error(t("problem.save.toast.loginRequired"));
    return;
  }

  const userId = fetchCurrentUserId();
  if (!userId) return;

  const list = userLists.value.find((l) => l.id === listId);
  if (!list) return;

  const isInList = list.containsProblem;

  try {
    if (isInList) {
      await batchRemoveProblemFromLists(userId, props.problemId, [listId]);
      list.containsProblem = false;
      toast.success(
        t("problem.save.toast.removedFromList", { name: list.name }),
      );
    } else {
      await batchAddProblemToLists(userId, props.problemId, [listId]);
      list.containsProblem = true;
      toast.success(t("problem.save.toast.addedToList", { name: list.name }));
    }

    emitChange();
  } catch (error) {
    console.error("Failed to toggle list:", error);
    toast.error(t("problem.save.toast.updateListFailed"));
  }
}

function emitChange() {
  emit("change", isFavorited.value, itemFolders.value, selectedListIds.value);
}

function handleOpenChange(open: boolean) {
  isOpen.value = open;
  if (open) {
    loadData();
  }
}

function openCreateDialog() {
  showCreateDialog.value = true;
  newListName.value = "";
}

async function handleCreateList() {
  if (!newListName.value.trim()) {
    toast.error(t("problem.save.toast.enterName"));
    return;
  }

  const userId = fetchCurrentUserId();
  if (!userId) return;

  isCreating.value = true;
  try {
    const newList = await createProblemList(userId, {
      name: newListName.value.trim(),
      isPublic: false,
    });

    // Add the problem to the newly created list
    await batchAddProblemToLists(userId, props.problemId, [newList.id]);

    // Reload the lists
    await loadData();

    toast.success(
      t("problem.save.toast.createdAndAdded", { name: newList.name }),
    );
    showCreateDialog.value = false;
    emitChange();
  } catch (error) {
    console.error("Failed to create list:", error);
    toast.error(t("problem.save.toast.createFailed"));
  } finally {
    isCreating.value = false;
  }
}

watch(
  () => props.problemId,
  () => {
    itemFolders.value = [];
    userLists.value = [];
  },
);
</script>

<template>
  <div>
    <DropdownMenu @update:open="handleOpenChange">
      <DropdownMenuTrigger as-child>
        <ActionItem
          :icon="isSaved ? BookmarkCheck : Bookmark"
          :count="count"
          :label="t('problem.save.title')"
          :active="isSaved"
          class="cursor-pointer"
          :class="{ 'fill-primary/20': isSaved }"
          :aria-label="t('problem.save.title')"
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent class="w-72" align="end">
        <DropdownMenuLabel>{{ t("problem.save.title") }}</DropdownMenuLabel>
        <DropdownMenuSeparator />

        <template v-if="isLoading">
          <div class="flex items-center justify-center py-4">
            <Loader2 class="h-4 w-4 animate-spin" />
          </div>
        </template>
        <template v-else-if="!isAuthed">
          <div class="px-3 py-2 text-sm text-muted-foreground">
            {{ t("problem.save.loginRequired") }}
          </div>
        </template>
        <template v-else>
          <!-- Bookmark Folders Section -->
          <div v-if="bookmarkStore.defaultFolder">
            <DropdownMenuLabel class="text-xs text-muted-foreground px-2 py-1">
              {{ t("problem.save.quickSave") }}
            </DropdownMenuLabel>
            <DropdownMenuCheckboxItem
              :checked="isFavorited"
              @select.prevent="toggleFolder(bookmarkStore.defaultFolder.id)"
              :class="{
                'bg-primary/10 border-l-2 border-primary': isFavorited,
              }"
              class="transition-colors"
            >
              <span class="flex items-center gap-2 flex-1">
                <Bookmark class="h-4 w-4" />
                <span
                  :class="{
                    'font-semibold text-primary': isFavorited,
                  }"
                >
                  {{ bookmarkStore.defaultFolder.name }}
                </span>
                <Check
                  v-if="isFavorited"
                  class="h-3 w-3 text-primary ml-auto"
                />
              </span>
            </DropdownMenuCheckboxItem>

            <DropdownMenuSeparator
              v-if="
                bookmarkStore.customFolders.length > 0 || userLists.length > 0
              "
            />
          </div>

          <!-- Custom Folders -->
          <div v-if="bookmarkStore.customFolders.length > 0">
            <DropdownMenuLabel class="text-xs text-muted-foreground px-2 py-1">
              {{ t("problem.save.folders") }}
            </DropdownMenuLabel>
            <DropdownMenuCheckboxItem
              v-for="folder in bookmarkStore.customFolders"
              :key="folder.id"
              :checked="itemFolders.includes(folder.id)"
              @select.prevent="toggleFolder(folder.id)"
              :class="{
                'bg-primary/10 border-l-2 border-primary': itemFolders.includes(
                  folder.id,
                ),
              }"
            >
              <span class="flex items-center gap-2 flex-1">
                <span
                  :class="{
                    'font-semibold text-primary': itemFolders.includes(
                      folder.id,
                    ),
                  }"
                  >{{ folder.name }}</span
                >
                <Check
                  v-if="itemFolders.includes(folder.id)"
                  class="h-3 w-3 text-primary ml-auto"
                />
              </span>
            </DropdownMenuCheckboxItem>
            <DropdownMenuSeparator v-if="userLists.length > 0" />
          </div>

          <!-- Problem Lists Section -->
          <div v-if="userLists.length > 0">
            <DropdownMenuLabel class="text-xs text-muted-foreground px-2 py-1">
              {{ t("problem.save.lists") }}
            </DropdownMenuLabel>
            <div class="max-h-[200px] overflow-y-auto">
              <DropdownMenuCheckboxItem
                v-for="list in userLists"
                :key="list.id"
                :checked="list.containsProblem"
                @select.prevent="toggleList(list.id)"
                :class="{
                  'bg-primary/10 border-l-2 border-primary':
                    list.containsProblem,
                }"
                class="flex items-start gap-2 py-2 transition-colors"
              >
                <div class="flex flex-col flex-1 min-w-0">
                  <div class="flex items-center gap-2">
                    <span
                      class="truncate"
                      :class="{
                        'font-semibold text-primary': list.containsProblem,
                        'font-medium': !list.containsProblem,
                      }"
                      >{{ list.name }}</span
                    >
                    <Check
                      v-if="list.containsProblem"
                      class="h-4 w-4 text-primary flex-shrink-0 ml-auto"
                    />
                  </div>
                  <div
                    class="flex items-center gap-2 text-xs"
                    :class="{
                      'text-primary/70': list.containsProblem,
                      'text-muted-foreground': !list.containsProblem,
                    }"
                  >
                    <span
                      >{{ list.problemCount }} {{ t("bookmark.items") }}</span
                    >
                    <span>•</span>
                    <span
                      >{{ list.favoritesCount }}
                      {{ t("problem.save.saves") }}</span
                    >
                  </div>
                </div>
              </DropdownMenuCheckboxItem>
            </div>
          </div>

          <!-- Empty State -->
          <div
            v-if="
              bookmarkStore.customFolders.length === 0 &&
              userLists.length === 0 &&
              !bookmarkStore.defaultFolder
            "
            class="px-3 py-4 text-sm text-muted-foreground text-center"
          >
            <p class="mb-2">{{ t("problem.save.empty") }}</p>
            <p class="text-xs">{{ t("problem.save.createHint") }}</p>
          </div>

          <DropdownMenuSeparator />

          <!-- Actions -->
          <DropdownMenuItem class="cursor-pointer" @select="openCreateDialog">
            <FolderPlus class="mr-2 h-4 w-4" />
            {{ t("problem.save.createList") }}
          </DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenu>

    <!-- Create List Dialog -->
    <Dialog v-model:open="showCreateDialog">
      <DialogContent class="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>{{ t("problem.save.createListTitle") }}</DialogTitle>
          <DialogDescription>
            {{ t("problem.save.createListDesc") }}
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-4 py-4">
          <div class="grid gap-2">
            <Label for="list-name">{{ t("problem.save.listName") }}</Label>
            <Input
              id="list-name"
              v-model="newListName"
              :placeholder="t('problem.save.listNamePlaceholder')"
              @keyup.enter="handleCreateList"
            />
          </div>
        </div>
        <DialogFooter>
          <Button
            variant="outline"
            @click="showCreateDialog = false"
            :disabled="isCreating"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button @click="handleCreateList" :disabled="isCreating">
            <Loader2 v-if="isCreating" class="mr-2 h-4 w-4 animate-spin" />
            {{ t("problem.save.createAndAdd") }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
