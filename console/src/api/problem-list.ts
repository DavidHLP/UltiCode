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
// Backend Response Interfaces (snake_case from Spring Boot)
// ============================================================================

interface BackendProblemList {
  id?: unknown;
  name?: unknown;
  description?: unknown;
  problemCount?: unknown;
  problem_count?: unknown;
  favoritesCount?: unknown;
  favorites_count?: unknown;
  authorId?: unknown;
  author_id?: unknown;
  authorName?: unknown;
  author_name?: unknown;
  authorUsername?: unknown;
  author_username?: unknown;
  isPublic?: unknown;
  is_public?: unknown;
  isFeatured?: unknown;
  is_featured?: unknown;
  bannerTag?: unknown;
  banner_tag?: unknown;
  bannerIcon?: unknown;
  banner_icon?: unknown;
  bannerTheme?: unknown;
  banner_theme?: unknown;
  bannerOrder?: unknown;
  banner_order?: unknown;
  createdAt?: unknown;
  created_at?: unknown;
  updatedAt?: unknown;
  updated_at?: unknown;
  isSaved?: unknown;
  categoryId?: unknown;
  containsProblem?: unknown;
  canEdit?: unknown;
  hasProblem?: unknown;
  problemCountStatus?: unknown;
  problem_count_status?: unknown;
}

interface BackendProblemListCategory {
  id?: unknown;
  name?: unknown;
  sortOrder?: unknown;
  sort_order?: unknown;
  lists?: unknown[];
  description?: unknown;
  icon?: unknown;
  color?: unknown;
  listCount?: unknown;
  list_count?: unknown;
}

interface BackendUserProblemListsResponse {
  ownLists?: unknown[];
  savedLists?: unknown[];
  featuredLists?: unknown[];
  categories?: unknown[];
}

interface BackendProblemListDetailResponse {
  list?: unknown;
  problems?: unknown[];
  stats?: unknown;
  isOwner?: unknown;
  viewer?: BackendViewerState;
  categories?: BackendCategoryOption[];
}

interface BackendViewerState {
  isSaved?: unknown;
  categoryId?: unknown;
}

interface BackendCategoryOption {
  id?: unknown;
  name?: unknown;
  sortOrder?: unknown;
  sort_order?: unknown;
}

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
  const raw = input as BackendProblemList;
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
    authorName:
      typeof raw.authorName === "string"
        ? raw.authorName
        : typeof raw.author_name === "string"
          ? raw.author_name
          : undefined,
    authorUsername:
      typeof raw.authorUsername === "string"
        ? raw.authorUsername
        : typeof raw.author_username === "string"
          ? raw.author_username
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
  const raw = input as BackendProblemListCategory;
  const rawListCount = raw.listCount ?? raw.list_count;
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
    description:
      typeof raw.description === "string" ? raw.description : undefined,
    icon: typeof raw.icon === "string" ? raw.icon : undefined,
    color: typeof raw.color === "string" ? raw.color : undefined,
    listCount: typeof rawListCount === "number" ? rawListCount : undefined,
  };
}

function mapUserProblemListsResponse(input: unknown): UserProblemListsResponse {
  if (!input || typeof input !== "object") {
    return { myLists: [], savedLists: [], featured: [], categories: [] };
  }
  const raw = input as BackendUserProblemListsResponse;
  return {
    myLists: Array.isArray(raw.ownLists) ? raw.ownLists.map(mapProblemList) : [],
    savedLists: Array.isArray(raw.savedLists)
      ? raw.savedLists.map(mapProblemList)
      : [],
    featured: Array.isArray(raw.featuredLists)
      ? raw.featuredLists.map(mapProblemList)
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
  const raw = input as BackendProblemList;
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
  const raw = data as BackendProblemListDetailResponse;
  return {
    list: raw.list ? mapProblemList(raw.list) : null,
    problems: Array.isArray(raw.problems) ? raw.problems.map(mapProblem) : [],
    stats:
      raw.stats && typeof raw.stats === "object"
        ? (raw.stats as ProblemListStats)
        : null,
    isOwner:
      typeof raw.isOwner === "boolean" ? raw.isOwner : undefined,
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
      ? raw.categories.map((item: BackendCategoryOption) => ({
          id: String(item.id ?? ""),
          name: String(item.name ?? ""),
          sortOrder:
            typeof item.sortOrder === "number"
              ? item.sortOrder
              : typeof item.sort_order === "number"
                ? item.sort_order
                : 0,
        }))
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
  const data = await apiGet<unknown>(
    `/problem-lists/problems/${problemId}/user-lists?userId=${userId}`,
  );
  if (!data || typeof data !== "object") {
    return [];
  }
  const raw = data as { lists?: unknown[] };
  const lists = Array.isArray(raw.lists) ? raw.lists : [];
  return lists.map((item) => {
    const rawItem = item as BackendProblemList;
    return {
      ...mapProblemList(item),
      containsProblem: Boolean(rawItem.hasProblem ?? rawItem.containsProblem),
      canEdit: Boolean(rawItem.canEdit),
    };
  });
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
