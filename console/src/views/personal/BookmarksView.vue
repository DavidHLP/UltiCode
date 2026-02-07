<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import {
  Plus,
  Loader2,
  Trash2,
  MessageSquare,
  FileText,
  List,
  CheckCircle2,
  FolderOpen,
  MoreVertical,
  Calendar,
  ExternalLink,
  Search,
} from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
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
} from "@/components/ui/dropdown-menu";
import BookmarkFolderList from "@/components/bookmark/BookmarkFolderList.vue";
import CreateFolderDialog from "@/components/bookmark/CreateFolderDialog.vue";
import { useBookmarkStore } from "@/stores/bookmark";
import { fetchFolder, removeBookmark } from "@/api/bookmark";
import { BookmarkType } from "@/api/bookmark";
import { useI18n } from "vue-i18n";
import type {
  BookmarkFolder,
  BookmarkFolderWithItems,
  CreateFolderInput,
  UpdateFolderInput,
  Bookmark,
} from "@/types/bookmark";
import { toast } from "vue-sonner";
import { RouterLink } from "vue-router";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";

// Interfaces for metadata
interface ProblemMetadata {
  slug: string;
  difficulty: string;
}

interface SolutionMetadata {
  problemSlug: string;
  problemTitle: string;
  authorName: string;
}

interface ForumMetadata {
  communityName: string;
  communitySlug: string;
  authorName: string;
  authorAvatar: string;
}

const store = useBookmarkStore();
const { t } = useI18n();

const selectedFolderId = ref<string | null>(null);
const selectedFolderDetails = ref<BookmarkFolderWithItems | null>(null);
const isLoadingDetails = ref(false);
const searchQuery = ref("");

const showCreateDialog = ref(false);
const editingFolder = ref<BookmarkFolder | null>(null);
const showDeleteDialog = ref(false);
const deletingFolder = ref<BookmarkFolder | null>(null);

// Computed
const selectedFolder = computed(() =>
  store.folders.find((f) => f.id === selectedFolderId.value),
);

const filteredItems = computed(() => {
  if (!selectedFolderDetails.value) return [];
  if (!searchQuery.value.trim()) return selectedFolderDetails.value.items;

  const query = searchQuery.value.toLowerCase();
  return selectedFolderDetails.value.items.filter(
    (item) =>
      item.title?.toLowerCase().includes(query) ||
      item.note?.toLowerCase().includes(query),
  );
});

// Methods
async function loadFolderDetails(id: string) {
  isLoadingDetails.value = true;
  try {
    selectedFolderDetails.value = await fetchFolder(id);
  } catch (error) {
    console.error("Failed to load folder details:", error);
    toast.error(t("personal.messages.loadFailed"));
  } finally {
    isLoadingDetails.value = false;
  }
}

function handleSelectFolder(folder: BookmarkFolder) {
  if (selectedFolderId.value === folder.id) return;
  selectedFolderId.value = folder.id;
  searchQuery.value = "";
  loadFolderDetails(folder.id);
}

// Auto-select first folder if none selected
watch(
  () => store.folders,
  (folders) => {
    if (folders.length > 0 && !selectedFolderId.value) {
      const firstFolder = folders[0];
      if (firstFolder) {
        handleSelectFolder(firstFolder);
      }
    }
  },
  { immediate: true },
);

function handleEditFolder(folder: BookmarkFolder) {
  editingFolder.value = folder;
  showCreateDialog.value = true;
}

function handleDeleteFolder(folder: BookmarkFolder) {
  deletingFolder.value = folder;
  showDeleteDialog.value = true;
}

async function handleCreateFolder(data: CreateFolderInput) {
  try {
    const newFolder = await store.createFolder(data);
    toast.success(t("personal.messages.folderCreated"));
    handleSelectFolder(newFolder);
  } catch (error) {
    console.error("Failed to create folder:", error);
    toast.error(t("personal.messages.saveFailed"));
  }
}

async function handleUpdateFolder(id: string, data: UpdateFolderInput) {
  try {
    await store.updateFolder(id, data);
    toast.success(t("personal.messages.profileUpdated"));
    if (selectedFolderId.value === id) {
      loadFolderDetails(id);
    }
  } catch (error) {
    console.error("Failed to update folder:", error);
    toast.error(t("personal.messages.saveFailed"));
  }
}

async function confirmDelete() {
  if (!deletingFolder.value) return;

  try {
    await store.removeFolder(deletingFolder.value.id);
    toast.success(t("personal.messages.folderDeleted"));
    if (selectedFolderId.value === deletingFolder.value.id) {
      selectedFolderId.value = null;
      selectedFolderDetails.value = null;
    }
  } catch (error) {
    console.error("Failed to delete folder:", error);
    toast.error(t("personal.messages.saveFailed"));
  } finally {
    showDeleteDialog.value = false;
    deletingFolder.value = null;
  }
}

async function handleRemoveItem(bookmarkId: string) {
  if (!selectedFolderId.value) return;
  try {
    await removeBookmark(selectedFolderId.value, bookmarkId);
    toast.success(t("personal.messages.bookmarkRemoved"));
    // Refresh
    loadFolderDetails(selectedFolderId.value);
    // Optionally refresh folder list count
    store.loadFolders();
  } catch (error) {
    console.error("Failed to remove item", error);
    toast.error(t("personal.messages.saveFailed"));
  }
}

function handleDialogClose() {
  showCreateDialog.value = false;
  editingFolder.value = null;
}

// Helpers for item display
function getItemIcon(type: BookmarkType) {
  switch (type) {
    case BookmarkType.FORUM_POST:
      return MessageSquare;
    case BookmarkType.PROBLEM:
      return FileText;
    case BookmarkType.PROBLEM_LIST:
      return List;
    case BookmarkType.SOLUTION:
      return CheckCircle2;
    default:
      return FileText;
  }
}

function getItemUrl(item: Bookmark) {
  switch (item.targetType) {
    case BookmarkType.FORUM_POST:
      return { name: "forum-thread", params: { postId: item.targetId } };
    case BookmarkType.PROBLEM: {
      const meta = item.metadata as unknown as ProblemMetadata;
      return {
        name: "problem-detail",
        params: { slug: meta?.slug || item.targetId },
      };
    }
    case BookmarkType.PROBLEM_LIST:
      return { name: "problem-list-detail", params: { id: item.targetId } };
    case BookmarkType.SOLUTION: {
      const meta = item.metadata as unknown as SolutionMetadata;
      if (meta?.problemSlug) {
        return {
          name: "problem-detail",
          params: { slug: meta.problemSlug },
          query: { solutionId: item.targetId },
        };
      }
      return "#";
    }
    default:
      return "#";
  }
}

function getForumMetadata(item: Bookmark): ForumMetadata | undefined {
  return item.metadata as unknown as ForumMetadata;
}

onMounted(() => {
  store.loadFolders(true);
});
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.bookmarks.title')"
      :description="t('personal.bookmarks.subtitle')"
    >
      <template #actions>
        <Button
          @click="showCreateDialog = true"
          class="rounded-full gap-2 shadow-sm"
        >
          <Plus class="h-4 w-4" />
          {{ t("personal.bookmarks.newCollection") }}
        </Button>
      </template>
    </PersonalPageHeader>

    <div class="grid grid-cols-1 lg:grid-cols-4 gap-8 items-start">
      <!-- Sidebar (Folders) -->
      <Card
        class="lg:col-span-1 border-none shadow-none bg-muted/20 sticky top-24 rounded-2xl"
      >
        <CardHeader class="pb-3 px-4">
          <CardTitle
            class="text-sm font-semibold uppercase tracking-widest text-muted-foreground/70"
            >{{ t("personal.bookmarks.folders") }}</CardTitle
          >
        </CardHeader>
        <CardContent class="px-2 pb-4">
          <div v-if="store.isLoading" class="flex justify-center py-8">
            <Loader2 class="h-6 w-6 animate-spin text-primary" />
          </div>
          <BookmarkFolderList
            v-else
            :folders="store.folders"
            :selected-id="selectedFolderId ?? undefined"
            @select="handleSelectFolder"
            @edit="handleEditFolder"
            @delete="handleDeleteFolder"
          />
        </CardContent>
      </Card>

      <!-- Main Content (Items) -->
      <div class="lg:col-span-3 space-y-6">
        <div v-if="selectedFolder" class="flex flex-col gap-6">
          <div
            class="flex flex-col sm:flex-row sm:items-center justify-between gap-4"
          >
            <div>
              <h3 class="text-2xl font-semibold tracking-tight">
                {{ selectedFolder.name }}
              </h3>
              <p
                v-if="selectedFolder.description"
                class="text-sm text-muted-foreground mt-1"
              >
                {{ selectedFolder.description }}
              </p>
            </div>
            <div class="relative w-full sm:w-64">
              <Search
                class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
              />
              <Input
                v-model="searchQuery"
                :placeholder="t('personal.bookmarks.searchPlaceholder')"
                class="pl-10 rounded-full h-10 border-muted-foreground/20 focus:ring-primary/20"
              />
            </div>
          </div>

          <Card class="border-none shadow-sm overflow-hidden rounded-2xl">
            <CardContent class="p-0">
              <div
                v-if="isLoadingDetails"
                class="flex flex-col items-center justify-center py-20 gap-4"
              >
                <Loader2 class="h-10 w-10 animate-spin text-primary" />
                <p class="text-sm text-muted-foreground">
                  {{ t("personal.bookmarks.loadingItems") }}
                </p>
              </div>

              <div v-else-if="!selectedFolderDetails" class="py-24 text-center">
                <div
                  class="bg-muted/50 w-16 h-16 rounded-2xl flex items-center justify-center mx-auto mb-4"
                >
                  <FolderOpen class="h-8 w-8 text-muted-foreground/50" />
                </div>
                <p class="text-muted-foreground">
                  {{ t("personal.bookmarks.noCollectionSelected") }}
                </p>
              </div>

              <div
                v-else-if="selectedFolderDetails.items.length === 0"
                class="flex flex-col items-center justify-center py-24 text-center px-6 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5 m-4"
              >
                <div
                  class="p-0 flex items-center justify-center w-16 h-16 rounded-2xl bg-muted/50 mb-4"
                >
                  <FileText class="h-8 w-8 text-muted-foreground/50" />
                </div>
                <h4 class="text-lg font-bold">
                  {{ t("personal.bookmarks.emptyCollection") }}
                </h4>
                <p class="text-sm text-muted-foreground mt-1 max-w-[280px]">
                  {{ t("personal.bookmarks.emptyCollectionDesc") }}
                </p>
              </div>

              <div v-else class="divide-y divide-border/50">
                <div
                  v-for="item in filteredItems"
                  :key="item.id"
                  class="group flex items-start gap-4 p-5 hover:bg-muted/40 transition-all duration-200"
                >
                  <!-- Icon -->
                  <div
                    class="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border bg-background group-hover:border-primary/50 group-hover:bg-primary/5 transition-all shadow-sm"
                  >
                    <component
                      :is="getItemIcon(item.targetType)"
                      class="h-6 w-6 text-muted-foreground group-hover:text-primary transition-colors"
                    />
                  </div>

                  <!-- Content -->
                  <div class="flex-1 min-w-0">
                    <div class="flex items-start justify-between gap-4">
                      <div class="space-y-1">
                        <RouterLink
                          :to="getItemUrl(item)"
                          class="text-base font-semibold hover:text-primary transition-colors line-clamp-1 flex items-center gap-2"
                        >
                          {{ item.title ?? `Item ${item.targetId}` }}
                          <ExternalLink
                            class="h-3 w-3 opacity-0 group-hover:opacity-50 transition-opacity"
                          />
                        </RouterLink>

                        <div
                          class="flex flex-wrap items-center gap-x-3 gap-y-1"
                        >
                          <Badge
                            variant="secondary"
                            class="text-[9px] font-semibold uppercase tracking-widest px-1.5 h-4 rounded-md"
                          >
                            {{
                              item.targetType.toLowerCase().replace("_", " ")
                            }}
                          </Badge>

                          <template v-if="item.metadata">
                            <Separator
                              orientation="vertical"
                              class="h-3 bg-muted-foreground/30"
                            />
                            <div
                              v-if="getForumMetadata(item)?.communityName"
                              class="text-xs font-medium text-muted-foreground"
                            >
                              r/{{ getForumMetadata(item)?.communityName }}
                            </div>
                            <div
                              v-if="getForumMetadata(item)?.authorName"
                              class="text-xs font-medium text-muted-foreground flex items-center gap-1"
                            >
                              by
                              <span class="font-semibold text-foreground/80">{{
                                getForumMetadata(item)?.authorName
                              }}</span>
                            </div>
                          </template>

                          <div
                            class="text-xs font-medium text-muted-foreground flex items-center gap-1"
                          >
                            <Calendar class="h-3 w-3" />
                            {{ new Date(item.createdAt).toLocaleDateString() }}
                          </div>
                        </div>
                      </div>

                      <DropdownMenu>
                        <DropdownMenuTrigger as-child>
                          <Button
                            variant="ghost"
                            size="icon"
                            class="h-8 w-8 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                          >
                            <MoreVertical class="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" class="w-48">
                          <DropdownMenuItem as-child class="gap-2">
                            <RouterLink :to="getItemUrl(item)">
                              <ExternalLink class="h-4 w-4" />
                              {{ t("personal.bookmarks.actions.openItem") }}
                            </RouterLink>
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            class="text-destructive focus:text-destructive gap-2"
                            @click="handleRemoveItem(item.id)"
                          >
                            <Trash2 class="h-4 w-4" />
                            {{
                              t(
                                "personal.bookmarks.actions.removeFromCollection",
                              )
                            }}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>

                    <div v-if="item.note" class="mt-3 relative">
                      <div
                        class="absolute left-0 top-0 bottom-0 w-1 bg-primary/20 rounded-full"
                      ></div>
                      <p class="text-sm text-muted-foreground pl-4 italic py-1">
                        "{{ item.note }}"
                      </p>
                    </div>
                  </div>
                </div>

                <div
                  v-if="filteredItems.length === 0 && searchQuery"
                  class="py-20 text-center"
                >
                  <p class="text-muted-foreground">
                    {{ t("personal.bookmarks.noResults") }}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <div
          v-else
          class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-2xl bg-muted/5 text-center px-6"
        >
          <FolderOpen class="h-12 w-12 text-muted-foreground/30 mb-4" />
          <h3 class="text-xl font-bold">
            {{ t("personal.bookmarks.noCollectionSelected") }}
          </h3>
          <p class="text-muted-foreground mt-2">
            {{ t("personal.bookmarks.noCollectionSelectedDesc") }}
          </p>
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <CreateFolderDialog
      :open="showCreateDialog"
      :folder="editingFolder"
      @update:open="handleDialogClose"
      @create="handleCreateFolder"
      @update="handleUpdateFolder"
    />

    <!-- Delete Confirmation Dialog -->
    <AlertDialog
      :open="showDeleteDialog"
      @update:open="showDeleteDialog = $event"
    >
      <AlertDialogContent class="rounded-2xl">
        <AlertDialogHeader>
          <AlertDialogTitle>{{
            t("personal.bookmarks.dialogs.deleteTitle")
          }}</AlertDialogTitle>
          <AlertDialogDescription>
            {{
              t("personal.bookmarks.dialogs.deleteConfirm", {
                name: deletingFolder?.name,
              })
            }}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel
            @click="showDeleteDialog = false"
            class="rounded-full"
          >
            {{ t("common.actions.cancel") }}
          </AlertDialogCancel>
          <AlertDialogAction
            class="bg-destructive text-destructive-foreground hover:bg-destructive/90 rounded-full"
            @click="confirmDelete"
          >
            {{ t("common.actions.delete") }}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </PersonalPageShell>
</template>
