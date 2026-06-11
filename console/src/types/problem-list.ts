import type { Problem } from "./problem";

export interface ProblemList {
  id: string;
  name: string;
  description?: string;
  problemCount: number;
  authorId?: string;
  authorName?: string;
  authorUsername?: string;
  isPublic?: boolean;
  isFeatured?: boolean;
  bannerTag?: string;
  bannerIcon?: string;
  bannerTheme?: string;
  bannerOrder?: number;
  createdAt?: string;
  updatedAt?: string;
  isSaved?: boolean;
}

export interface ProblemListCategory {
  id: string;
  name: string;
  sortOrder: number;
  lists: ProblemList[];
  description?: string;
  icon?: string;
  color?: string;
  listCount?: number;
  /**
   * Owner user ID (populated from backend CategorySummaryVO.userId).
   * Optional for backward compatibility with older API responses.
   *
   * @remarks Future-use field. Wired through for type-contract completeness;
   * currently no consumer in console logic (e.g., for "own categories" filtering).
   * Adding a filter like `c.userId === currentUserId` is a non-breaking change.
   */
  userId?: string;
}

export interface ProblemListCategoryOption {
  id: string;
  name: string;
  sortOrder: number;
}

export interface UserProblemListsResponse {
  ownLists: ProblemList[];
  savedLists: ProblemList[];
  featuredLists: ProblemList[];
  categories: ProblemListCategory[];
}

export interface ProblemListStats {
  listId: string;
  totalCount: number;
  solvedCount: number;
  attemptedCount: number;
  todoCount: number;
  progress: number;
  total_lists?: number;
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
  createdAt?: string;
  updatedAt?: string;
}

export interface ProblemListDetailResponse {
  list: ProblemList | null;
  problems: Problem[];
  stats: ProblemListStats | null;
  isOwner?: boolean;
  viewer?: {
    isSaved: boolean;
    categoryId: string | null;
  };
  categories?: ProblemListCategoryOption[];
}
