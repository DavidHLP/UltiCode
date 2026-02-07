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

export interface Bookmark {
  id: string;
  targetId: string;
  targetType: BookmarkType;
  sortOrder: number;
  note: string | null;
  createdAt: string;
  title?: string;
  metadata?: Record<string, unknown>;
}

export interface BookmarkFolderWithItems
  extends Omit<BookmarkFolder, "itemCount"> {
  items: Bookmark[];
}

export interface CreateFolderInput {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
}

export interface UpdateFolderInput {
  name?: string;
  description?: string;
  icon?: string;
  color?: string;
  sortOrder?: number;
}

export interface AddBookmarkInput {
  targetId: string;
  targetType: BookmarkType;
  note?: string;
}

export interface UpdateBookmarkInput {
  note?: string;
  sortOrder?: number;
}
