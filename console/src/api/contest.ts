import { z } from "zod";
import { apiGet, apiPost, apiDelete } from "@/utils/request";
import { mapSubmission } from "@/api/submission";
import {
  contestListItemSchema,
  contestDetailSchema,
  contestProblemSummarySchema,
  globalRankingEntrySchema,
  paginatedSchema,
} from "./contest.schema";
import type {
  ContestListItem,
  ContestDetail,
  ContestProblemSummary,
  GlobalRankingEntry,
  PaginatedResult,
  ContestFilters,
  ContestAnnouncement,
  RankingEntry,
  LiveRankingEntry,
  ContestRankingEntry,
  VirtualContestSession,
  UserContestHistory,
  ParticipationStatus,
} from "@/types/contest";
import type { SubmissionRecord } from "@/types/submission";

// ============================================================================
// PARSING HELPERS
// ============================================================================

function parsePaginated<T extends z.ZodTypeAny>(
  itemSchema: T,
  result: unknown,
): PaginatedResult<z.infer<T>> {
  return paginatedSchema(itemSchema).parse(result);
}

// ============================================================================
// CONTEST QUERIES
// ============================================================================

export async function fetchUpcomingContests(): Promise<
  PaginatedResult<ContestListItem>
> {
  const result = await apiGet("/contest/upcoming");
  return parsePaginated(contestListItemSchema, result);
}

export async function fetchRunningContests(): Promise<
  PaginatedResult<ContestListItem>
> {
  const result = await apiGet("/contest/running");
  return parsePaginated(contestListItemSchema, result);
}

export async function fetchPastContests(
  page: number = 1,
  pageSize: number = 10,
): Promise<PaginatedResult<ContestListItem>> {
  const result = await apiGet("/contest/past", {
    params: { page, pageSize },
  });
  return parsePaginated(contestListItemSchema, result);
}

export async function fetchContestDetail(
  contestId: string,
): Promise<ContestDetail> {
  const raw = await apiGet(`/contest/${contestId}`);
  return contestDetailSchema.parse(raw);
}

// ============================================================================
// CONTEST LIST (filtered) — GET /contest
// ============================================================================

export async function getContests(
  filters?: ContestFilters,
): Promise<PaginatedResult<ContestListItem>> {
  const params = new URLSearchParams();

  if (filters) {
    if (filters.status) {
      const statuses = Array.isArray(filters.status)
        ? filters.status
        : [filters.status];
      statuses.forEach((s) => params.append("status", s));
    }
    if (filters.contestType) {
      const types = Array.isArray(filters.contestType)
        ? filters.contestType
        : [filters.contestType];
      types.forEach((t) => params.append("contestType", t));
    }
    if (filters.isRated !== undefined) {
      params.append("isRated", String(filters.isRated));
    }
    if (filters.search) {
      params.append("search", filters.search);
    }
    if (filters.startDateFrom) {
      params.append("startDateFrom", filters.startDateFrom);
    }
    if (filters.startDateTo) {
      params.append("startDateTo", filters.startDateTo);
    }
    if (filters.page !== undefined) {
      params.append("page", String(filters.page));
    }
    if (filters.pageSize !== undefined) {
      params.append("pageSize", String(filters.pageSize));
    }
    if (filters.sort) {
      params.append("sort", filters.sort);
    }
    if (filters.direction) {
      params.append("direction", filters.direction);
    }
  }

  const queryString = params.toString();
  const url = queryString ? `/contest?${queryString}` : "/contest";

  const result = await apiGet(url);
  return parsePaginated(contestListItemSchema, result);
}

export async function getContestProblems(
  slug: string,
): Promise<ContestProblemSummary[]> {
  const data = await apiGet<unknown[]>(`/contest/${slug}/problems`);
  return z.array(contestProblemSummarySchema).parse(data || []);
}

export async function getAnnouncements(
  slug: string,
): Promise<ContestAnnouncement[]> {
  return apiGet<ContestAnnouncement[]>(`/contest/${slug}/announcements`);
}

// ============================================================================
// RANKINGS
// ============================================================================

export async function fetchContestRanking(
  contestId: string,
  options?: { page?: number; limit?: number; includeVirtual?: boolean },
): Promise<PaginatedResult<ContestRankingEntry>> {
  const { page = 1, limit = 50, includeVirtual = true } = options || {};
  return apiGet<PaginatedResult<ContestRankingEntry>>(
    `/contest/${contestId}/ranking`,
    { params: { page, limit, includeVirtual } },
  );
}

export async function fetchLiveRanking(
  contestId: string,
  limit: number = 100,
): Promise<LiveRankingEntry[]> {
  return apiGet<LiveRankingEntry[]>(`/contest/${contestId}/live-ranking`, {
    params: { limit },
  });
}

export async function fetchGlobalRankings(options?: {
  page?: number;
  limit?: number;
  country?: string;
}): Promise<PaginatedResult<GlobalRankingEntry>> {
  const { page = 1, limit = 50, country } = options || {};
  const result = await apiGet("/contest/rankings/global", {
    params: country ? { page, limit, country } : { page, limit },
  });
  return parsePaginated(globalRankingEntrySchema, result);
}

export async function getRanking(
  slug: string,
  options?: { page?: number; limit?: number; includeVirtual?: boolean },
): Promise<PaginatedResult<RankingEntry>> {
  const { page = 1, limit = 50, includeVirtual = true } = options || {};
  return apiGet<PaginatedResult<RankingEntry>>(`/contest/${slug}/ranking`, {
    params: { page, limit, includeVirtual },
  });
}

// ============================================================================
// PARTICIPATION
// ============================================================================

export async function registerForContest(contestId: string): Promise<void> {
  return apiPost<void>(`/contest/${contestId}/register`);
}

export async function unregisterFromContest(contestId: string): Promise<void> {
  return apiDelete<void>(`/contest/${contestId}/register`);
}

export async function fetchParticipationStatus(
  contestId: string,
): Promise<ParticipationStatus> {
  return apiGet<ParticipationStatus>(`/contest/${contestId}/participation`);
}

export async function checkIn(slug: string): Promise<void> {
  return apiPost<void>(`/contest/${slug}/check-in`);
}

// ============================================================================
// VIRTUAL CONTEST
// ============================================================================

export async function startVirtualContest(
  contestId: string,
): Promise<VirtualContestSession> {
  return apiPost<VirtualContestSession>(`/contest/${contestId}/virtual/start`);
}

export async function fetchVirtualSession(
  contestId: string,
): Promise<VirtualContestSession | null> {
  return apiGet<VirtualContestSession | null>(
    `/contest/${contestId}/virtual/session`,
  );
}

export async function finishVirtualContest(
  contestId: string,
  sessionId: string,
): Promise<void> {
  return apiPost<void>(`/contest/${contestId}/virtual/finish`, {
    sessionId,
  });
}

// ============================================================================
// USER CONTESTS
// ============================================================================

export async function fetchUserContests(
  type: "registered" | "participated" | "virtual" = "participated",
): Promise<ContestListItem[]> {
  const result = await apiGet<unknown[]>("/contest/user/my-contests", {
    params: { type },
  });
  return z.array(contestListItemSchema).parse(result || []);
}

export async function fetchUserContestHistory(): Promise<UserContestHistory[]> {
  return apiGet<UserContestHistory[]>("/contest/user/history");
}

// ============================================================================
// CONTEST SUBMISSIONS
// ============================================================================

export interface ContestSubmissionDto {
  language: string;
  code: string;
}

export type ContestSubmissionResult = SubmissionRecord;

export async function submitContestProblem(
  contestId: string,
  problemId: number,
  dto: ContestSubmissionDto,
): Promise<ContestSubmissionResult> {
  const response = await apiPost<unknown>(
    `/contest/${contestId}/problems/${problemId}/submissions`,
    dto,
  );
  return mapSubmission(response) as ContestSubmissionResult;
}

export async function fetchContestProblemSubmissions(
  contestId: string,
  problemId: number,
): Promise<ContestSubmissionResult[]> {
  const data = await apiGet<unknown[]>(
    `/contest/${contestId}/problems/${problemId}/submissions`,
  );
  return data.map((item) => mapSubmission(item) as ContestSubmissionResult);
}
