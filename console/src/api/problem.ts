import type { Problem } from "@/types/problem";
import { apiGet } from "@/utils/request";

// ============================================================================
// Backend Response Interface (snake_case from Spring Boot)
// ============================================================================

interface BackendProblem {
  id?: unknown;
  acceptanceRate?: unknown;
  acceptance_rate?: unknown;
  completedTime?: unknown;
  completed_time?: unknown;
  isPremium?: unknown;
  is_premium?: unknown;
  hasSolution?: unknown;
  has_solution?: unknown;
  tagRelations?: unknown;
  tags?: unknown;
  [key: string]: unknown;
}

function mapProblem(problem: unknown): Problem {
  if (!problem || typeof problem !== "object") return problem as Problem;
  const p = problem as BackendProblem;
  const rawAcceptance = p.acceptanceRate ?? p.acceptance_rate;
  const parsedAcceptance =
    rawAcceptance === undefined || rawAcceptance === null
      ? undefined
      : Number(rawAcceptance);
  const acceptanceRate =
    parsedAcceptance === undefined || Number.isNaN(parsedAcceptance)
      ? undefined
      : parsedAcceptance;
  const completedRaw = p.completedTime ?? p.completed_time;
  const completedTime =
    completedRaw === null || completedRaw === undefined
      ? undefined
      : completedRaw instanceof Date
        ? completedRaw.toISOString()
        : String(completedRaw);
  // Convert id to number (bigint from backend comes as string in JSON)
  const rawId = p.id;
  const id =
    typeof rawId === "number"
      ? rawId
      : typeof rawId === "string"
        ? Number(rawId)
        : rawId;
  return {
    ...p,
    id,
    acceptance_rate:
      acceptanceRate ??
      (typeof p.acceptance_rate === "number" ? p.acceptance_rate : undefined),
    acceptanceRate,
    isPremium: (p.isPremium ?? p.is_premium) as boolean | undefined,
    hasSolution: (p.hasSolution ?? p.has_solution) as boolean | undefined,
    completedTime,
    tags: Array.isArray(p.tags)
      ? p.tags
          .map((tag) =>
            typeof tag === "string" ? tag : (tag as { label?: string })?.label,
          )
          .filter((l): l is string => typeof l === "string")
      : Array.isArray(p.tagRelations)
        ? p.tagRelations
            .map((r: { tag?: { label: string } }) => r.tag?.label)
            .filter((l): l is string => typeof l === "string")
        : [],
  } as Problem;
}

export interface ProblemFilters {
  category?: string;
  search?: string;
  difficulty?: string;
  status?: string;
  tag?: string;
  isPremium?: boolean;
  sortBy?: string;
  sortOrder?: string;
}

export interface PaginatedProblems {
  items: Problem[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export async function fetchProblems(
  filters: ProblemFilters = {},
  page: number = 1,
  pageSize: number = 50,
): Promise<PaginatedProblems> {
  const params = new URLSearchParams();
  params.append("page", String(page));
  params.append("pageSize", String(pageSize));
  if (filters.search) params.append("search", filters.search);
  if (filters.difficulty) params.append("difficulty", filters.difficulty);
  if (filters.status) params.append("status", filters.status);
  if (filters.tag) params.append("tag", filters.tag);
  if (filters.category && filters.category !== "all")
    params.append("category", filters.category);
  if (filters.isPremium !== undefined)
    params.append("isPremium", String(filters.isPremium));
  if (filters.sortBy) params.append("sortBy", filters.sortBy);
  if (filters.sortOrder) params.append("sortOrder", filters.sortOrder);

  const response = await apiGet<{
    items: unknown[];
    total: number;
    page: number;
    pageSize: number;
    totalPages: number;
  }>(`/problems?${params.toString()}`);
  return {
    items: response.items.map(mapProblem),
    total: response.total,
    page: response.page,
    pageSize: response.pageSize,
    totalPages: response.totalPages,
  };
}

export async function searchProblems(query: string): Promise<Problem[]> {
  if (!query.trim()) return [];
  const result = await fetchProblems({ search: query.trim() });
  return result.items;
}

export async function fetchProblemById(
  id: string | number,
  userId?: string,
): Promise<Problem> {
  const query = userId ? `?userId=${userId}` : "";
  const isNumeric = typeof id === "number" || !isNaN(Number(id));
  const endpoint = isNumeric ? `/problems/${id}` : `/problems/slug/${id}`;
  const data = await apiGet<unknown>(`${endpoint}${query}`);
  return mapProblem(data);
}

export async function fetchRandomProblem(): Promise<Problem> {
  const data = await apiGet<unknown>("/problems/random");
  return mapProblem(data);
}

export async function fetchAdjacentProblems(
  id: number,
): Promise<{ prev: string | null; next: string | null }> {
  return apiGet<{ prev: string | null; next: string | null }>(
    `/problems/${id}/adjacent`,
  );
}

export { mapProblem };
