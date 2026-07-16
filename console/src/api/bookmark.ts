import { apiDelete, apiGet, apiPatch, apiPost } from "@/utils/request";
import {
  BookmarkType,
  type BookmarkFolder,
  type BookmarkFolderDetail,
  type BookmarkItem,
} from "@/types/bookmark";

export { BookmarkType };
export type {
  BookmarkFolder,
  BookmarkFolderDetail,
  BookmarkItem,
} from "@/types/bookmark";

/**
 * Response shape for `GET /bookmarks/item/{targetType}/{targetId}` and
 * `POST /bookmarks/quick`. Matches backend {@code ItemFoldersVO} /
 * {@code QuickFavoriteVO}.
 */
export interface ItemFoldersVO {
  targetId: string;
  targetType: BookmarkType;
  isFavorited: boolean;
  folders: BookmarkFolder[];
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
): Promise<ItemFoldersVO> {
  return apiGet<ItemFoldersVO>(`/bookmarks/item/${targetType}/${targetId}`);
}

export async function reorderFolders(folderIds: string[]): Promise<void> {
  await apiPost("/bookmarks/folders/reorder", { folderIds });
}

/**
 * Response shape for `POST /bookmarks/quick`. Matches backend
 * {@code QuickFavoriteVO}: {@code isFavorited} reflects current state;
 * {@code folderIds} lists the folders containing the item (empty when
 * just un-favorited).
 */
export interface ToggleBookmarkResponse {
  isFavorited: boolean;
  folderIds: string[];
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
