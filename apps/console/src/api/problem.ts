import { normalizePublicProblem } from "@ulticode/domain-types";
import type { Problem } from "@/types/problem";
import type { PageResult } from "@ulticode/domain-types";
import { readPage } from "@/api/projection";
import { apiGet } from "@/utils/request";

// ============================================================================
// Backend Response Interface (snake_case from Spring Boot)
// ============================================================================

function mapProblem(problem: unknown): Problem {
  return normalizePublicProblem(problem) as Problem;
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

export type PaginatedProblems = PageResult<Problem>;

export async function fetchProblems(
  filters: ProblemFilters = {},
  page: number = 1,
  pageSize: number = 50,
): Promise<PageResult<Problem>> {
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

  const response = await apiGet<unknown>(`/problems?${params.toString()}`);
  const pageResult = readPage<unknown>(response);
  return {
    ...pageResult,
    items: pageResult.items.map(mapProblem),
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
