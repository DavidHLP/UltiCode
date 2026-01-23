import type { Problem } from "./problem";

export interface ProblemList {
  id: string;
  name: string;
  description?: string;
  problemCount: number;
  favoritesCount: number;
  authorId?: string;
  isPublic?: boolean;
  isFeatured?: boolean;
  bannerTag?: string;
  bannerIcon?: string;
  bannerTheme?: string;
  bannerOrder?: number;
  createdAt?: string;
  updatedAt?: string;
  isSaved?: boolean; // Whether current user saved this list
  categoryId?: string; // User's category for this list
  problems?: Problem[];
}

// User's custom category for organizing saved lists
export interface ProblemListCategory {
  id: string;
  name: string;
  sortOrder: number;
  lists: ProblemList[];
}

export interface ProblemListCategoryOption {
  id: string;
  name: string;
  sortOrder: number;
}

// Response from getUserProblemLists API
export interface UserProblemListsResponse {
  myLists: ProblemList[]; // Lists created by user
  savedLists: ProblemList[]; // Lists saved by user (from others)
  featured: ProblemList[]; // Featured lists
  categories: ProblemListCategory[]; // User's custom categories
}

export interface ProblemListStats {
  listId: string;
  totalCount: number;
  solvedCount: number;
  attemptedCount: number;
  todoCount: number;
  progress: number;
  total_lists?: number; // Keep these if needed for other components
  total_problems?: number;
}

export type ProblemListId = string;

export interface ProblemListItem {
  id: string;
  name: string;
  description?: string;
  authorId?: string;
  isPublic?: boolean;
  isFeatured?: boolean;
  bannerTag?: string;
  bannerIcon?: string;
  bannerTheme?: string;
  bannerOrder?: number;
  favoritesCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProblemListDetailResponse {
  list: ProblemList | null;
  problems: Problem[];
  stats: ProblemListStats | null;
  viewer?: {
    isSaved: boolean;
    categoryId: string | null;
  };
  categories?: ProblemListCategoryOption[];
}
