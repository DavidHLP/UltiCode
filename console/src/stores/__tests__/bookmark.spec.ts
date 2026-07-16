import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { fetchFolder, fetchFolders, removeBookmark } from "@/api/bookmark";
import { useBookmarkStore } from "@/stores/bookmark";
import { BookmarkType } from "@/types/bookmark";
import type {
  BookmarkFolder,
  BookmarkFolderDetail,
  BookmarkItem,
} from "@/types/bookmark";

vi.mock("@/api/bookmark", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/api/bookmark")>();
  return {
    ...actual,
    fetchFolder: vi.fn(),
    fetchFolders: vi.fn(),
    removeBookmark: vi.fn(),
  };
});

function makeFolder(overrides: Partial<BookmarkFolder> = {}): BookmarkFolder {
  return {
    id: "folder-1",
    name: "Favorites",
    description: null,
    icon: null,
    color: null,
    isDefault: false,
    itemCount: 2,
    sortOrder: 0,
    createdAt: "2026-07-16T00:00:00Z",
    updatedAt: "2026-07-16T00:00:00Z",
    ...overrides,
  };
}

function makeItem(overrides: Partial<BookmarkItem> = {}): BookmarkItem {
  return {
    id: "bookmark-1",
    targetId: "problem-1",
    targetType: BookmarkType.PROBLEM,
    sortOrder: 0,
    note: null,
    createdAt: "2026-07-16T00:00:00Z",
    ...overrides,
  };
}

function makeDetail(
  folder: BookmarkFolder = makeFolder(),
  items: BookmarkItem[] = [makeItem(), makeItem({ id: "bookmark-2" })],
): BookmarkFolderDetail {
  return { ...folder, itemCount: items.length, items };
}

describe("useBookmarkStore bookmark workspace state", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("loads and exposes selected folder details", async () => {
    const folder = makeFolder();
    const detail = makeDetail(folder);
    vi.mocked(fetchFolder).mockResolvedValue(detail);

    const store = useBookmarkStore();
    store.folders = [folder];
    await store.selectFolder(folder.id);

    expect(fetchFolder).toHaveBeenCalledWith(folder.id);
    expect(store.selectedFolderId).toBe(folder.id);
    expect(store.selectedFolderDetails).toEqual(detail);
    expect(store.isLoadingDetails).toBe(false);
    expect(store.error).toBeNull();
  });

  it("removes an item from details and keeps the folder count consistent", async () => {
    const folder = makeFolder();
    const detail = makeDetail(folder);
    vi.mocked(fetchFolder).mockResolvedValue(detail);
    vi.mocked(removeBookmark).mockResolvedValue();

    const store = useBookmarkStore();
    store.folders = [folder];
    await store.selectFolder(folder.id);
    await store.removeBookmark(folder.id, "bookmark-1");

    expect(removeBookmark).toHaveBeenCalledWith(folder.id, "bookmark-1");
    expect(store.selectedFolderDetails?.items.map((item) => item.id)).toEqual([
      "bookmark-2",
    ]);
    expect(store.selectedFolderDetails?.itemCount).toBe(1);
    expect(store.folders[0]?.itemCount).toBe(1);
  });

  it("selects a folder and clears selection state on reset", async () => {
    const folder = makeFolder();
    vi.mocked(fetchFolder).mockResolvedValue(makeDetail(folder));

    const store = useBookmarkStore();
    store.folders = [folder];
    await store.selectFolder(folder.id);
    store.resetSelection();

    expect(store.selectedFolderId).toBeNull();
    expect(store.selectedFolderDetails).toBeNull();
    expect(store.isLoadingDetails).toBe(false);
    expect(store.folders).toEqual([folder]);
  });

  it("records detail loading errors and clears loading state", async () => {
    const failure = new Error("detail unavailable");
    vi.mocked(fetchFolder).mockRejectedValue(failure);

    const store = useBookmarkStore();
    await expect(store.loadFolderDetails("folder-1")).rejects.toBe(failure);

    expect(store.selectedFolderDetails).toBeNull();
    expect(store.isLoadingDetails).toBe(false);
    expect(store.error).toBe(failure.message);
  });

  it("preserves details and counts when item removal fails", async () => {
    const folder = makeFolder();
    const detail = makeDetail(folder);
    const failure = new Error("remove unavailable");
    vi.mocked(fetchFolder).mockResolvedValue(detail);
    vi.mocked(removeBookmark).mockRejectedValue(failure);

    const store = useBookmarkStore();
    store.folders = [folder];
    await store.selectFolder(folder.id);
    await expect(store.removeBookmark(folder.id, "bookmark-1")).rejects.toBe(
      failure,
    );

    expect(store.selectedFolderDetails?.items).toHaveLength(2);
    expect(store.folders[0]?.itemCount).toBe(2);
    expect(store.error).toBe(failure.message);
  });

  it("records folder loading errors", async () => {
    const failure = new Error("folders unavailable");
    vi.mocked(fetchFolders).mockRejectedValue(failure);

    const store = useBookmarkStore();
    await expect(store.loadFolders(true)).rejects.toBe(failure);

    expect(store.isLoading).toBe(false);
    expect(store.error).toBe(failure.message);
  });
});
