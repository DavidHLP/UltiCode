import { apiGet, apiPost, apiDelete, apiPatch } from "@/utils/request";
import { mapProblem } from "@/api/problem";
import type {
  ProblemListStats,
  ProblemListId,
  ProblemListItem,
  ProblemList,
  ProblemListCategory,
  UserProblemListsResponse,
  ProblemListDetailResponse,
} from "@/types/problem-list";

// ============================================================================
// Backend Response Interfaces (camelCase from Spring Boot Jackson)
// ============================================================================

interface BackendProblemList {
  id?: string;
  name?: string;
  description?: string | null;
  problemCount?: number | null;
  authorId?: string | null;
  authorName?: string | null;
  authorUsername?: string | null;
  isPublic?: boolean | null;
  isFeatured?: boolean | null;
  bannerTag?: string | null;
  bannerIcon?: string | null;
  bannerTheme?: string | null;
  bannerOrder?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  isSaved?: boolean | null;
}

interface BackendProblemListCategory {
  id?: string;
  name?: string;
  sortOrder?: number | null;
  lists?: unknown[];
  description?: string | null;
  icon?: string | null;
  color?: string | null;
  listCount?: number | null;
  /**
   * Owner user ID. Future-use field — see `ProblemListCategory.userId` JSDoc.
   */
  userId?: string | null;
}

interface BackendViewerState {
  isSaved?: boolean;
  categoryId?: string | null;
}

interface BackendCategoryOption {
  id?: string;
  name?: string;
  sortOrder?: number | null;
}

// ============================================================================
// Mappers
// ============================================================================

function mapProblemList(input: unknown): ProblemList {
  if (!input || typeof input !== "object") {
    return { id: "", name: "", description: undefined, problemCount: 0 };
  }
  const raw = input as BackendProblemList;
  const rawBannerOrder = raw.bannerOrder;
  const parsedBannerOrder =
    typeof rawBannerOrder === "number"
      ? rawBannerOrder
      : typeof rawBannerOrder === "string"
        ? Number(rawBannerOrder)
        : Number.NaN;
  return {
    id: String(raw.id ?? ""),
    name: String(raw.name ?? ""),
    description:
      typeof raw.description === "string" ? raw.description : undefined,
    problemCount: Number(raw.problemCount ?? 0) || 0,
    authorId: raw.authorId ?? undefined,
    authorName: raw.authorName ?? undefined,
    authorUsername: raw.authorUsername ?? undefined,
    isPublic: raw.isPublic ?? undefined,
    isFeatured: raw.isFeatured ?? undefined,
    bannerTag: raw.bannerTag ?? undefined,
    bannerIcon: raw.bannerIcon ?? undefined,
    bannerTheme: raw.bannerTheme ?? undefined,
    bannerOrder: Number.isFinite(parsedBannerOrder)
      ? parsedBannerOrder
      : undefined,
    createdAt: typeof raw.createdAt === "string" ? raw.createdAt : undefined,
    updatedAt: typeof raw.updatedAt === "string" ? raw.updatedAt : undefined,
    isSaved: typeof raw.isSaved === "boolean" ? raw.isSaved : undefined,
  };
}

function mapCategory(input: unknown): ProblemListCategory {
  if (!input || typeof input !== "object") {
    return { id: "", name: "", sortOrder: 0, lists: [] };
  }
  const raw = input as BackendProblemListCategory;
  return {
    id: String(raw.id ?? ""),
    name: String(raw.name ?? ""),
    sortOrder: typeof raw.sortOrder === "number" ? raw.sortOrder : 0,
    lists: Array.isArray(raw.lists) ? raw.lists.map(mapProblemList) : [],
    description:
      typeof raw.description === "string" ? raw.description : undefined,
    icon: typeof raw.icon === "string" ? raw.icon : undefined,
    color: typeof raw.color === "string" ? raw.color : undefined,
    listCount: typeof raw.listCount === "number" ? raw.listCount : undefined,
    userId: typeof raw.userId === "string" ? raw.userId : undefined,
  };
}

function mapUserProblemListsResponse(input: unknown): UserProblemListsResponse {
  if (!input || typeof input !== "object") {
    return { ownLists: [], savedLists: [], featuredLists: [], categories: [] };
  }
  const raw = input as {
    ownLists?: unknown[];
    savedLists?: unknown[];
    featuredLists?: unknown[];
    categories?: unknown[];
  };
  return {
    ownLists: Array.isArray(raw.ownLists)
      ? raw.ownLists.map(mapProblemList)
      : [],
    savedLists: Array.isArray(raw.savedLists)
      ? raw.savedLists.map(mapProblemList)
      : [],
    featuredLists: Array.isArray(raw.featuredLists)
      ? raw.featuredLists.map(mapProblemList)
      : [],
    categories: Array.isArray(raw.categories)
      ? raw.categories.map(mapCategory)
      : [],
  };
}

function mapProblemListItem(input: unknown): ProblemListItem {
  if (!input || typeof input !== "object") {
    return { id: "", name: "" };
  }
  const raw = input as BackendProblemList;
  const rawBannerOrder = raw.bannerOrder;
  const parsedBannerOrder =
    typeof rawBannerOrder === "number"
      ? rawBannerOrder
      : typeof rawBannerOrder === "string"
        ? Number(rawBannerOrder)
        : Number.NaN;
  return {
    id: String(raw.id ?? ""),
    name: String(raw.name ?? ""),
    description:
      typeof raw.description === "string" ? raw.description : undefined,
    authorId: raw.authorId ?? undefined,
    isPublic: raw.isPublic ?? undefined,
    isFeatured: raw.isFeatured ?? undefined,
    bannerTag: raw.bannerTag ?? undefined,
    bannerIcon: raw.bannerIcon ?? undefined,
    bannerTheme: raw.bannerTheme ?? undefined,
    bannerOrder: Number.isFinite(parsedBannerOrder)
      ? parsedBannerOrder
      : undefined,
    createdAt: typeof raw.createdAt === "string" ? raw.createdAt : undefined,
    updatedAt: typeof raw.updatedAt === "string" ? raw.updatedAt : undefined,
  };
}

// ============================================================================
// Main API: Get User's Problem Lists
// ============================================================================

export async function fetchProblemListsOverview(): Promise<UserProblemListsResponse> {
  const data = await apiGet<unknown>("/problem-lists/overview");
  return mapUserProblemListsResponse(data);
}

/**
 * Get featured problem lists (no independent HTTP request).
 *
 * This function is a convenience wrapper around {@link fetchProblemListsOverview}
 * that returns its `featuredLists` field. It does NOT issue a separate network
 * request to the backend, despite the naming — the backend exposes a single
 * `/problem-lists/overview` endpoint that returns all three buckets.
 *
 * Use {@link fetchProblemListsOverview} when you need own + saved + featured
 * in one call; reach for this only when the UI strictly renders the featured strip.
 *
 * @see fetchProblemListsOverview
 */
export async function fetchFeaturedProblemLists(): Promise<ProblemList[]> {
  const data = await fetchProblemListsOverview();
  return data.featuredLists;
}

export async function fetchProblemListOverview(
  listId: ProblemListId,
): Promise<ProblemListDetailResponse> {
  const data = await apiGet<unknown>(`/problem-lists/${listId}/overview`);
  if (!data || typeof data !== "object") {
    return { list: null, problems: [], stats: null };
  }
  const raw = data as {
    problems?: unknown[];
    stats?: unknown;
    isOwner?: boolean;
    viewer?: BackendViewerState;
    categories?: unknown[];
  };
  // Flatten: backend ProblemListDetailVO extends SummaryVO fields + problems
  // Front-end expects { list, problems, stats, ... }
  const listData = mapProblemList(data);
  return {
    list: listData,
    problems: Array.isArray(raw.problems) ? raw.problems.map(mapProblem) : [],
    stats:
      raw.stats && typeof raw.stats === "object"
        ? (raw.stats as ProblemListStats)
        : null,
    isOwner: typeof raw.isOwner === "boolean" ? raw.isOwner : undefined,
    viewer:
      raw.viewer && typeof raw.viewer === "object"
        ? {
            isSaved: Boolean(raw.viewer.isSaved),
            categoryId:
              typeof raw.viewer.categoryId === "string"
                ? String(raw.viewer.categoryId)
                : null,
          }
        : undefined,
    categories: Array.isArray(raw.categories)
      ? raw.categories.map((item: unknown) => {
          const c = item as BackendCategoryOption;
          return {
            id: String(c.id ?? ""),
            name: String(c.name ?? ""),
            sortOrder: typeof c.sortOrder === "number" ? c.sortOrder : 0,
          };
        })
      : undefined,
  };
}

// ============================================================================
// List CRUD
// ============================================================================

export async function createProblemList(data: {
  name: string;
  description?: string;
  isPublic?: boolean;
}): Promise<ProblemListItem> {
  const res = await apiPost<unknown>("/problem-lists", data);
  return mapProblemListItem(res);
}

export async function updateProblemList(
  listId: string,
  data: { name?: string; description?: string; isPublic?: boolean },
): Promise<void> {
  await apiPatch(`/problem-lists/${listId}`, data);
}

export async function deleteProblemList(listId: string): Promise<void> {
  await apiDelete(`/problem-lists/${listId}`);
}

export async function forkProblemList(
  listId: string,
): Promise<ProblemListItem> {
  const res = await apiPost<unknown>(`/problem-lists/${listId}/fork`);
  return mapProblemListItem(res);
}

// ============================================================================
// Problem Management in List
// ============================================================================

export async function addProblemToList(
  listId: string,
  problemId: number,
): Promise<void> {
  await apiPost(`/problem-lists/${listId}/problems`, { problemId });
}

export async function removeProblemFromList(
  listId: string,
  problemId: number,
): Promise<void> {
  await apiDelete(`/problem-lists/${listId}/problems/${problemId}`);
}

export async function batchAddProblemToLists(
  problemId: number,
  listIds: string[],
): Promise<void> {
  await apiPost(`/problem-lists/problems/${problemId}/batch-add`, { listIds });
}

export async function batchRemoveProblemFromLists(
  problemId: number,
  listIds: string[],
): Promise<void> {
  await apiPost(`/problem-lists/problems/${problemId}/batch-remove`, {
    listIds,
  });
}

export interface ProblemListWithStatus extends ProblemList {
  containsProblem: boolean;
  canEdit: boolean;
}

export async function getUserListsForProblem(
  problemId: number,
): Promise<ProblemListWithStatus[]> {
  const data = await apiGet<unknown>(
    `/problem-lists/problems/${problemId}/user-lists`,
  );
  if (!data || typeof data !== "object") {
    return [];
  }
  const raw = data as { lists?: unknown[] };
  const lists = Array.isArray(raw.lists) ? raw.lists : [];
  return lists.map((item) => {
    const rawItem = item as BackendProblemList & {
      hasProblem?: boolean;
      canEdit?: boolean;
    };
    return {
      ...mapProblemList(item),
      containsProblem: Boolean(rawItem.hasProblem),
      canEdit: Boolean(rawItem.canEdit),
    };
  });
}

// ============================================================================
// Save/Unsave List
// ============================================================================

export async function saveList(
  listId: string,
  categoryId?: string,
): Promise<void> {
  await apiPost(`/problem-lists/${listId}/save`, { categoryId });
}

export async function unsaveList(listId: string): Promise<void> {
  await apiDelete(`/problem-lists/${listId}/save`);
}

export async function moveListToCategory(
  listId: string,
  categoryId: string | null,
): Promise<void> {
  await apiPatch(`/problem-lists/${listId}/category`, { categoryId });
}

// ============================================================================
// Category Management
// ============================================================================

export async function createCategory(data: {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
}): Promise<ProblemListCategory> {
  const res = await apiPost<unknown>("/problem-lists/categories", data);
  return mapCategory(res);
}

export async function updateCategory(
  categoryId: string,
  data: {
    name?: string;
    description?: string;
    icon?: string;
    color?: string;
    sortOrder?: number;
  },
): Promise<ProblemListCategory> {
  const res = await apiPatch<unknown>(
    `/problem-lists/categories/${categoryId}`,
    data,
  );
  return mapCategory(res);
}

export async function deleteCategory(categoryId: string): Promise<void> {
  await apiDelete(`/problem-lists/categories/${categoryId}`);
}
