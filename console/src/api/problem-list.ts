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
// Mappers
// ============================================================================

function mapProblemList(input: unknown): ProblemList {
  if (!input || typeof input !== "object") {
    return {
      id: "",
      name: "",
      description: undefined,
      problemCount: 0,
      favoritesCount: 0,
    };
  }
  const raw = input as Record<string, unknown>;
  const rawCount = raw.problemCount ?? raw.problem_count ?? 0;
  const rawFavorites = raw.favoritesCount ?? raw.favorites_count ?? 0;
  const rawBannerOrder = raw.bannerOrder ?? raw.banner_order;
  const createdRaw = raw.createdAt ?? raw.created_at;
  const updatedRaw = raw.updatedAt ?? raw.updated_at;
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
    problemCount: Number(rawCount) || 0,
    favoritesCount: Number(rawFavorites) || 0,
    authorId:
      typeof raw.authorId === "string"
        ? raw.authorId
        : typeof raw.author_id === "string"
          ? raw.author_id
          : undefined,
    isPublic:
      typeof raw.isPublic === "boolean"
        ? raw.isPublic
        : typeof raw.is_public === "boolean"
          ? raw.is_public
          : undefined,
    isFeatured:
      typeof raw.isFeatured === "boolean"
        ? raw.isFeatured
        : typeof raw.is_featured === "boolean"
          ? raw.is_featured
          : undefined,
    bannerTag:
      typeof raw.bannerTag === "string"
        ? raw.bannerTag
        : typeof raw.banner_tag === "string"
          ? raw.banner_tag
          : undefined,
    bannerIcon:
      typeof raw.bannerIcon === "string"
        ? raw.bannerIcon
        : typeof raw.banner_icon === "string"
          ? raw.banner_icon
          : undefined,
    bannerTheme:
      typeof raw.bannerTheme === "string"
        ? raw.bannerTheme
        : typeof raw.banner_theme === "string"
          ? raw.banner_theme
          : undefined,
    bannerOrder: Number.isFinite(parsedBannerOrder)
      ? parsedBannerOrder
      : undefined,
    createdAt:
      createdRaw instanceof Date
        ? createdRaw.toISOString()
        : typeof createdRaw === "string"
          ? createdRaw
          : undefined,
    updatedAt:
      updatedRaw instanceof Date
        ? updatedRaw.toISOString()
        : typeof updatedRaw === "string"
          ? updatedRaw
          : undefined,
    isSaved: typeof raw.isSaved === "boolean" ? raw.isSaved : undefined,
    categoryId: typeof raw.categoryId === "string" ? raw.categoryId : undefined,
  };
}

function mapCategory(input: unknown): ProblemListCategory {
  if (!input || typeof input !== "object") {
    return { id: "", name: "", sortOrder: 0, lists: [] };
  }
  const raw = input as Record<string, unknown>;
  return {
    id: String(raw.id ?? ""),
    name: String(raw.name ?? ""),
    sortOrder:
      typeof raw.sortOrder === "number"
        ? raw.sortOrder
        : typeof raw.sort_order === "number"
          ? raw.sort_order
          : 0,
    lists: Array.isArray(raw.lists) ? raw.lists.map(mapProblemList) : [],
  };
}

function mapUserProblemListsResponse(input: unknown): UserProblemListsResponse {
  if (!input || typeof input !== "object") {
    return { myLists: [], savedLists: [], featured: [], categories: [] };
  }
  const raw = input as Record<string, unknown>;
  return {
    myLists: Array.isArray(raw.myLists) ? raw.myLists.map(mapProblemList) : [],
    savedLists: Array.isArray(raw.savedLists)
      ? raw.savedLists.map(mapProblemList)
      : [],
    featured: Array.isArray(raw.featured)
      ? raw.featured.map(mapProblemList)
      : [],
    categories: Array.isArray(raw.categories)
      ? raw.categories.map(mapCategory)
      : [],
  };
}

function mapProblemListItem(input: unknown): ProblemListItem {
  if (!input || typeof input !== "object") {
    return {
      id: "",
      name: "",
    };
  }
  const raw = input as Record<string, unknown>;
  const createdRaw = raw.createdAt ?? raw.created_at;
  const updatedRaw = raw.updatedAt ?? raw.updated_at;
  const rawBannerOrder = raw.bannerOrder ?? raw.banner_order;
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
    authorId:
      typeof raw.authorId === "string"
        ? raw.authorId
        : typeof raw.author_id === "string"
          ? raw.author_id
          : undefined,
    isPublic:
      typeof raw.isPublic === "boolean"
        ? raw.isPublic
        : typeof raw.is_public === "boolean"
          ? raw.is_public
          : undefined,
    isFeatured:
      typeof raw.isFeatured === "boolean"
        ? raw.isFeatured
        : typeof raw.is_featured === "boolean"
          ? raw.is_featured
          : undefined,
    bannerTag:
      typeof raw.bannerTag === "string"
        ? raw.bannerTag
        : typeof raw.banner_tag === "string"
          ? raw.banner_tag
          : undefined,
    bannerIcon:
      typeof raw.bannerIcon === "string"
        ? raw.bannerIcon
        : typeof raw.banner_icon === "string"
          ? raw.banner_icon
          : undefined,
    bannerTheme:
      typeof raw.bannerTheme === "string"
        ? raw.bannerTheme
        : typeof raw.banner_theme === "string"
          ? raw.banner_theme
          : undefined,
    bannerOrder: Number.isFinite(parsedBannerOrder)
      ? parsedBannerOrder
      : undefined,
    favoritesCount:
      typeof raw.favoritesCount === "number"
        ? raw.favoritesCount
        : typeof raw.favorites_count === "number"
          ? raw.favorites_count
          : undefined,
    createdAt:
      createdRaw instanceof Date
        ? createdRaw.toISOString()
        : typeof createdRaw === "string"
          ? createdRaw
          : undefined,
    updatedAt:
      updatedRaw instanceof Date
        ? updatedRaw.toISOString()
        : typeof updatedRaw === "string"
          ? updatedRaw
          : undefined,
  };
}

// ============================================================================
// Main API: Get User's Problem Lists
// ============================================================================

export async function fetchProblemListsOverview(
  userId?: string,
): Promise<UserProblemListsResponse> {
  const query = userId ? `?userId=${userId}` : "";
  const data = await apiGet<unknown>(`/problem-lists/overview${query}`);
  return mapUserProblemListsResponse(data);
}

export async function fetchFeaturedProblemLists(): Promise<ProblemList[]> {
  const data = await fetchProblemListsOverview();
  return data.featured;
}

export async function fetchProblemListOverview(
  listId: ProblemListId,
  userId?: string,
): Promise<ProblemListDetailResponse> {
  const query = userId ? `?userId=${userId}` : "";
  const data = await apiGet<unknown>(
    `/problem-lists/${listId}/overview${query}`,
  );
  if (!data || typeof data !== "object") {
    return { list: null, problems: [], stats: null };
  }
  const raw = data as Record<string, unknown>;
  return {
    list: raw.list ? mapProblemList(raw.list) : null,
    problems: Array.isArray(raw.problems) ? raw.problems.map(mapProblem) : [],
    stats:
      raw.stats && typeof raw.stats === "object"
        ? (raw.stats as ProblemListStats)
        : null,
    viewer:
      raw.viewer && typeof raw.viewer === "object"
        ? {
            isSaved: Boolean((raw.viewer as Record<string, unknown>).isSaved),
            categoryId:
              typeof (raw.viewer as Record<string, unknown>).categoryId ===
              "string"
                ? String((raw.viewer as Record<string, unknown>).categoryId)
                : null,
          }
        : undefined,
    categories: Array.isArray(raw.categories)
      ? raw.categories.map((item) => {
          const entry = item as Record<string, unknown>;
          return {
            id: String(entry.id ?? ""),
            name: String(entry.name ?? ""),
            sortOrder:
              typeof entry.sortOrder === "number"
                ? entry.sortOrder
                : typeof entry.sort_order === "number"
                  ? entry.sort_order
                  : 0,
          };
        })
      : undefined,
  };
}

// ============================================================================
// List CRUD
// ============================================================================

export async function createProblemList(
  userId: string,
  data: { name: string; description?: string; isPublic?: boolean },
): Promise<ProblemListItem> {
  const res = await apiPost<unknown>(`/problem-lists?userId=${userId}`, data);
  return mapProblemListItem(res);
}

export async function updateProblemList(
  listId: string,
  userId: string,
  data: { name?: string; description?: string; isPublic?: boolean },
): Promise<void> {
  await apiPatch(`/problem-lists/${listId}?userId=${userId}`, data);
}

export async function deleteProblemList(
  listId: string,
  userId: string,
): Promise<void> {
  await apiDelete(`/problem-lists/${listId}?userId=${userId}`);
}

export async function forkProblemList(
  listId: string,
  userId: string,
): Promise<string> {
  const res = await apiPost<{ id: string }>(
    `/problem-lists/${listId}/fork?userId=${userId}`,
  );
  return res.id;
}

// ============================================================================
// Problem Management in List
// ============================================================================

export async function addProblemToList(
  listId: string,
  userId: string,
  problemId: number,
): Promise<void> {
  await apiPost(`/problem-lists/${listId}/problems?userId=${userId}`, {
    problemId,
  });
}

export async function removeProblemFromList(
  listId: string,
  userId: string,
  problemId: number,
): Promise<void> {
  await apiDelete(
    `/problem-lists/${listId}/problems/${problemId}?userId=${userId}`,
  );
}

export async function batchAddProblemToLists(
  userId: string,
  problemId: number,
  listIds: string[],
): Promise<void> {
  await apiPost(
    `/problem-lists/problems/${problemId}/batch-add?userId=${userId}`,
    { listIds },
  );
}

export async function batchRemoveProblemFromLists(
  userId: string,
  problemId: number,
  listIds: string[],
): Promise<void> {
  await apiPost(
    `/problem-lists/problems/${problemId}/batch-remove?userId=${userId}`,
    { listIds },
  );
}

export interface ProblemListWithStatus extends ProblemList {
  containsProblem: boolean;
  canEdit: boolean;
}

export async function getUserListsForProblem(
  userId: string,
  problemId: number,
): Promise<ProblemListWithStatus[]> {
  const data = await apiGet<unknown[]>(
    `/problem-lists/problems/${problemId}/user-lists?userId=${userId}`,
  );
  if (!Array.isArray(data)) {
    return [];
  }
  return data.map((item) => ({
    ...mapProblemList(item),
    containsProblem: Boolean((item as Record<string, unknown>).containsProblem),
    canEdit: Boolean((item as Record<string, unknown>).canEdit),
  }));
}

// ============================================================================
// Save/Unsave List
// ============================================================================

export async function saveList(
  listId: string,
  userId: string,
  categoryId?: string,
): Promise<void> {
  await apiPost(`/problem-lists/${listId}/save?userId=${userId}`, {
    categoryId,
  });
}

export async function unsaveList(
  listId: string,
  userId: string,
): Promise<void> {
  await apiDelete(`/problem-lists/${listId}/save?userId=${userId}`);
}

export async function moveListToCategory(
  listId: string,
  userId: string,
  categoryId: string | null,
): Promise<void> {
  await apiPatch(`/problem-lists/${listId}/category?userId=${userId}`, {
    categoryId,
  });
}

// ============================================================================
// Category Management
// ============================================================================

export async function createCategory(
  userId: string,
  data: { name: string; sortOrder?: number },
): Promise<ProblemListCategory> {
  const res = await apiPost<unknown>(
    `/problem-lists/categories?userId=${userId}`,
    data,
  );
  return mapCategory(res);
}

export async function updateCategory(
  categoryId: string,
  userId: string,
  data: { name?: string; sortOrder?: number },
): Promise<ProblemListCategory> {
  const res = await apiPatch<unknown>(
    `/problem-lists/categories/${categoryId}?userId=${userId}`,
    data,
  );
  return mapCategory(res);
}

export async function deleteCategory(
  categoryId: string,
  userId: string,
): Promise<void> {
  await apiDelete(`/problem-lists/categories/${categoryId}?userId=${userId}`);
}

// ============================================================================
// Legacy APIs - kept for backward compatibility but deprecated
// ============================================================================
