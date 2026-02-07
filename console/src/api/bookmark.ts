import { apiDelete, apiGet, apiPatch, apiPost } from "@/utils/request";

export enum BookmarkType {
  PROBLEM = "PROBLEM",
  SOLUTION = "SOLUTION",
  FORUM_POST = "FORUM_POST",
  PROBLEM_LIST = "PROBLEM_LIST",
  SOLUTION_COMMENT = "SOLUTION_COMMENT",
  FORUM_COMMENT = "FORUM_COMMENT",
}

export interface BookmarkFolder {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  isDefault: boolean;
  itemCount: number;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface BookmarkItem {
  id: string;
  targetId: string;
  targetType: BookmarkType;
  sortOrder: number;
  note: string | null;
  createdAt: string;
  title?: string;
  metadata?: Record<string, unknown>;
}

export interface BookmarkFolderDetail extends BookmarkFolder {
  items: BookmarkItem[];
}

export async function fetchFolders(): Promise<BookmarkFolder[]> {
  return apiGet<BookmarkFolder[]>("/bookmarks/folders");
}

export async function fetchFolder(id: string): Promise<BookmarkFolderDetail> {
  return apiGet<BookmarkFolderDetail>(`/bookmarks/folders/${id}`);
}

export async function createFolder(data: {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
}): Promise<BookmarkFolder> {
  return apiPost<BookmarkFolder>("/bookmarks/folders", data);
}

export async function updateFolder(
  id: string,
  data: {
    name?: string;
    description?: string;
    icon?: string;
    color?: string;
    sortOrder?: number;
  },
): Promise<BookmarkFolder> {
  return apiPatch<BookmarkFolder>(`/bookmarks/folders/${id}`, data);
}

export async function deleteFolder(id: string): Promise<void> {
  await apiDelete(`/bookmarks/folders/${id}`);
}

export async function addBookmark(
  folderId: string,
  data: { targetType: BookmarkType; targetId: string; note?: string },
): Promise<BookmarkItem> {
  return apiPost<BookmarkItem>(`/bookmarks/folders/${folderId}/items`, data);
}

export async function removeBookmark(
  folderId: string,
  bookmarkId: string,
): Promise<void> {
  await apiDelete(`/bookmarks/folders/${folderId}/items/${bookmarkId}`);
}

export async function removeBookmarkByTarget(
  folderId: string,
  targetType: BookmarkType,
  targetId: string,
): Promise<void> {
  await apiDelete(
    `/bookmarks/folders/${folderId}/items/target/${targetType}/${targetId}`,
  );
}

export async function getBookmarkFolders(
  targetType: BookmarkType,
  targetId: string,
): Promise<string[]> {
  return apiGet<string[]>(`/bookmarks/item/${targetType}/${targetId}`);
}

export async function reorderFolders(folderIds: string[]): Promise<void> {
  await apiPost("/bookmarks/folders/reorder", { folderIds });
}

export interface ToggleBookmarkResponse {
  isSaved: boolean;
}

export async function toggleBookmark(
  targetType: BookmarkType,
  targetId: string,
): Promise<ToggleBookmarkResponse> {
  return apiPost<ToggleBookmarkResponse>("/bookmarks/quick", {
    targetType,
    targetId,
  });
}
