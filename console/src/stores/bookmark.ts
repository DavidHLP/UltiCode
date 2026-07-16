import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type {
  AddBookmarkInput,
  BookmarkFolder,
  BookmarkFolderDetail,
  BookmarkItem,
  BookmarkType,
  CreateFolderInput,
  UpdateFolderInput,
} from "@/types/bookmark";
import {
  addBookmark as apiAddBookmark,
  createFolder as apiCreateFolder,
  deleteFolder as apiDeleteFolder,
  fetchFolder,
  fetchFolders,
  removeBookmark as apiRemoveBookmark,
  removeBookmarkByTarget as apiRemoveBookmarkByTarget,
  reorderFolders as apiReorderFolders,
  updateFolder as apiUpdateFolder,
} from "@/api/bookmark";

export const useBookmarkStore = defineStore("bookmark", () => {
  const folders = ref<BookmarkFolder[]>([]);
  const selectedFolderId = ref<string | null>(null);
  const selectedFolderDetails = ref<BookmarkFolderDetail | null>(null);
  const isLoading = ref(false);
  const isLoadingDetails = ref(false);
  const isLoaded = ref(false);
  const error = ref<string | null>(null);
  let detailRequestId = 0;

  const defaultFolder = computed(() => folders.value.find((f) => f.isDefault));

  const customFolders = computed(() =>
    folders.value.filter((f) => !f.isDefault),
  );

  const selectedFolder = computed(() =>
    folders.value.find((folder) => folder.id === selectedFolderId.value),
  );

  function getErrorMessage(err: unknown, fallback: string) {
    return err instanceof Error ? err.message : fallback;
  }

  async function loadFolders(force = false) {
    if (isLoaded.value && !force) return;

    isLoading.value = true;
    error.value = null;
    try {
      folders.value = await fetchFolders();
      isLoaded.value = true;
      if (
        selectedFolderId.value &&
        !folders.value.some((folder) => folder.id === selectedFolderId.value)
      ) {
        resetSelection();
      }
    } catch (err) {
      error.value = getErrorMessage(err, "Failed to load folders");
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function loadFolderDetails(id: string) {
    const requestId = ++detailRequestId;
    isLoadingDetails.value = true;
    error.value = null;
    try {
      const details = await fetchFolder(id);
      if (requestId === detailRequestId) {
        selectedFolderDetails.value = details;
      }
    } catch (err) {
      if (requestId === detailRequestId) {
        selectedFolderDetails.value = null;
        error.value = getErrorMessage(err, "Failed to load folder details");
      }
      throw err;
    } finally {
      if (requestId === detailRequestId) {
        isLoadingDetails.value = false;
      }
    }
  }

  async function selectFolder(id: string) {
    if (
      selectedFolderId.value === id &&
      selectedFolderDetails.value?.id === id
    ) {
      return;
    }

    selectedFolderId.value = id;
    selectedFolderDetails.value = null;
    return loadFolderDetails(id);
  }

  function resetSelection() {
    detailRequestId += 1;
    selectedFolderId.value = null;
    selectedFolderDetails.value = null;
    isLoadingDetails.value = false;
    error.value = null;
  }

  async function createFolder(
    data: CreateFolderInput,
  ): Promise<BookmarkFolder> {
    error.value = null;
    try {
      const newFolder = await apiCreateFolder(data);
      folders.value.push(newFolder);
      return newFolder;
    } catch (err) {
      error.value = getErrorMessage(err, "Failed to create folder");
      throw err;
    }
  }

  async function updateFolder(
    id: string,
    data: UpdateFolderInput,
  ): Promise<BookmarkFolder> {
    error.value = null;
    try {
      const updated = await apiUpdateFolder(id, data);
      const index = folders.value.findIndex((f) => f.id === id);
      if (index !== -1) {
        folders.value[index] = updated;
      }
      if (selectedFolderDetails.value?.id === id) {
        selectedFolderDetails.value = {
          ...selectedFolderDetails.value,
          ...updated,
          items: selectedFolderDetails.value.items,
        };
      }
      return updated;
    } catch (err) {
      error.value = getErrorMessage(err, "Failed to update folder");
      throw err;
    }
  }

  async function removeFolder(id: string): Promise<void> {
    error.value = null;
    try {
      await apiDeleteFolder(id);
      folders.value = folders.value.filter((f) => f.id !== id);
      if (selectedFolderId.value === id) {
        resetSelection();
      }
    } catch (err) {
      error.value = getErrorMessage(err, "Failed to delete folder");
      throw err;
    }
  }

  async function reorderFolders(ids: string[]): Promise<void> {
    error.value = null;
    try {
      await apiReorderFolders(ids);
      ids.forEach((id, index) => {
        const folder = folders.value.find((f) => f.id === id);
        if (folder) {
          folder.sortOrder = index;
        }
      });
      folders.value.sort((a, b) => {
        if (a.isDefault && !b.isDefault) return -1;
        if (!a.isDefault && b.isDefault) return 1;
        return a.sortOrder - b.sortOrder;
      });
    } catch (err) {
      error.value = getErrorMessage(err, "Failed to reorder folders");
      throw err;
    }
  }

  function setItemCount(folderId: string, count: number) {
    const itemCount = Math.max(0, count);
    const folder = folders.value.find((f) => f.id === folderId);
    if (folder) {
      folder.itemCount = itemCount;
    }
    if (selectedFolderDetails.value?.id === folderId) {
      selectedFolderDetails.value.itemCount = itemCount;
    }
  }

  function updateItemCount(folderId: string, delta: number) {
    const folder = folders.value.find((f) => f.id === folderId);
    setItemCount(folderId, (folder?.itemCount ?? 0) + delta);
  }

  const folderMutationQueues = new Map<string, Promise<void>>();

  async function runFolderMutation<T>(
    folderId: string,
    operation: () => Promise<T>,
  ): Promise<T> {
    const previous = folderMutationQueues.get(folderId) ?? Promise.resolve();
    let release!: () => void;
    const current = new Promise<void>((resolve) => {
      release = resolve;
    });
    folderMutationQueues.set(folderId, current);
    await previous.catch(() => undefined);
    try {
      return await operation();
    } finally {
      release();
      if (folderMutationQueues.get(folderId) === current) {
        folderMutationQueues.delete(folderId);
      }
    }
  }

  async function addBookmark(
    folderId: string,
    data: AddBookmarkInput,
  ): Promise<BookmarkItem> {
    return runFolderMutation(folderId, async () => {
      error.value = null;
      try {
        const item = await apiAddBookmark(folderId, data);
        const details = selectedFolderDetails.value;
        if (details?.id === folderId) {
          if (!details.items.some((existing) => existing.id === item.id)) {
            details.items.push(item);
          }
          setItemCount(folderId, details.items.length);
        } else {
          updateItemCount(folderId, 1);
        }
        return item;
      } catch (err) {
        error.value = getErrorMessage(err, "Failed to add bookmark");
        throw err;
      }
    });
  }

  async function removeBookmark(
    folderId: string,
    bookmarkId: string,
  ): Promise<void> {
    return runFolderMutation(folderId, async () => {
      error.value = null;
      try {
        await apiRemoveBookmark(folderId, bookmarkId);
        const details = selectedFolderDetails.value;
        if (details?.id === folderId) {
          details.items = details.items.filter((item) => item.id !== bookmarkId);
          setItemCount(folderId, details.items.length);
        } else {
          updateItemCount(folderId, -1);
        }
      } catch (err) {
        error.value = getErrorMessage(err, "Failed to remove bookmark");
        throw err;
      }
    });
  }

  async function removeBookmarkByTarget(
    folderId: string,
    targetType: BookmarkType,
    targetId: string,
  ): Promise<void> {
    return runFolderMutation(folderId, async () => {
      error.value = null;
      try {
        await apiRemoveBookmarkByTarget(folderId, targetType, targetId);
        const details = selectedFolderDetails.value;
        if (details?.id === folderId) {
          details.items = details.items.filter(
            (item) =>
              item.targetType !== targetType || item.targetId !== targetId,
          );
          setItemCount(folderId, details.items.length);
        } else {
          updateItemCount(folderId, -1);
        }
      } catch (err) {
        error.value = getErrorMessage(err, "Failed to remove bookmark");
        throw err;
      }
    });
  }

  function clearError() {
    error.value = null;
  }

  function reset() {
    resetSelection();
    folders.value = [];
    isLoaded.value = false;
    isLoading.value = false;
    error.value = null;
  }

  return {
    folders,
    selectedFolderId,
    selectedFolder,
    selectedFolderDetails,
    isLoading,
    isLoadingDetails,
    isLoaded,
    error,
    defaultFolder,
    customFolders,
    loadFolders,
    loadFolderDetails,
    selectFolder,
    resetSelection,
    createFolder,
    updateFolder,
    removeFolder,
    reorderFolders,
    addBookmark,
    removeBookmark,
    removeBookmarkByTarget,
    updateItemCount,
    clearError,
    reset,
  };
});
