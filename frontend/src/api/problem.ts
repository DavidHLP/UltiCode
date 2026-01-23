import type { Problem } from "@/types/problem";
import { apiGet } from "@/utils/request";

function mapProblem(problem: unknown): Problem {
  if (!problem || typeof problem !== "object") return problem as Problem;
  const p = problem as Record<string, unknown>;
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
    tags: Array.isArray(p.tagRelations)
      ? p.tagRelations
          .map((r: { tag?: { label: string } }) => r.tag?.label)
          .filter((l): l is string => typeof l === "string")
      : (p.tags as string[]) || [],
  } as Problem;
}

export async function fetchProblems(
  userId?: string,
  filters: { category?: string; search?: string } = {},
): Promise<Problem[]> {
  const params = new URLSearchParams();
  if (userId) params.append("userId", userId);
  if (filters.category) params.append("category", filters.category);
  if (filters.search) params.append("search", filters.search);

  const queryString = params.toString() ? `?${params.toString()}` : "";
  const response = await apiGet<{ items: unknown[] }>(
    `/problems${queryString}`,
  );
  return response.items.map(mapProblem);
}

export async function searchProblems(query: string): Promise<Problem[]> {
  if (!query.trim()) return [];
  const params = new URLSearchParams({ search: query });
  const response = await apiGet<{ items: unknown[] }>(
    `/problems?${params.toString()}`,
  );
  return response.items.map(mapProblem);
}

export async function fetchProblemById(
  id: string | number,
  userId?: string,
): Promise<Problem> {
  const query = userId ? `?userId=${userId}` : "";
  const data = await apiGet<unknown>(`/problems/${id}${query}`);
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
