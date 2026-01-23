import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  BookmarkFolder,
  CreateFolderInput,
  UpdateFolderInput,
} from "@/types/bookmark";
import {
  fetchFolders,
  createFolder as apiCreateFolder,
  updateFolder as apiUpdateFolder,
  deleteFolder as apiDeleteFolder,
  reorderFolders as apiReorderFolders,
} from "@/api/bookmark";

export const useBookmarkStore = defineStore("bookmark", () => {
  const folders = ref<BookmarkFolder[]>([]);
  const isLoading = ref(false);
  const isLoaded = ref(false);

  const defaultFolder = computed(() => folders.value.find((f) => f.isDefault));

  const customFolders = computed(() =>
    folders.value.filter((f) => !f.isDefault),
  );

  async function loadFolders(force = false) {
    if (isLoaded.value && !force) return;

    isLoading.value = true;
    try {
      folders.value = await fetchFolders();
      isLoaded.value = true;
    } finally {
      isLoading.value = false;
    }
  }

  async function createFolder(
    data: CreateFolderInput,
  ): Promise<BookmarkFolder> {
    const newFolder = await apiCreateFolder(data);
    folders.value.push(newFolder);
    return newFolder;
  }

  async function updateFolder(
    id: string,
    data: UpdateFolderInput,
  ): Promise<BookmarkFolder> {
    const updated = await apiUpdateFolder(id, data);
    const index = folders.value.findIndex((f) => f.id === id);
    if (index !== -1) {
      folders.value[index] = updated;
    }
    return updated;
  }

  async function removeFolder(id: string): Promise<void> {
    await apiDeleteFolder(id);
    folders.value = folders.value.filter((f) => f.id !== id);
  }

  async function reorderFolders(ids: string[]): Promise<void> {
    await apiReorderFolders(ids);
    // Update local sort order
    ids.forEach((id, index) => {
      const folder = folders.value.find((f) => f.id === id);
      if (folder) {
        folder.sortOrder = index;
      }
    });
    // Re-sort the array
    folders.value.sort((a, b) => {
      if (a.isDefault && !b.isDefault) return -1;
      if (!a.isDefault && b.isDefault) return 1;
      return a.sortOrder - b.sortOrder;
    });
  }

  function updateItemCount(folderId: string, delta: number) {
    const folder = folders.value.find((f) => f.id === folderId);
    if (folder) {
      folder.itemCount = Math.max(0, folder.itemCount + delta);
    }
  }

  function reset() {
    folders.value = [];
    isLoaded.value = false;
    isLoading.value = false;
  }

  return {
    folders,
    isLoading,
    isLoaded,
    defaultFolder,
    customFolders,
    loadFolders,
    createFolder,
    updateFolder,
    removeFolder,
    reorderFolders,
    updateItemCount,
    reset,
  };
});
