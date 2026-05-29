import { apiGet, apiPost, apiDelete } from "@/utils/request";
import { mapSubmission } from "@/api/submission";
import type {
  ContestListItem,
  ContestDetail,
  ContestRankingEntry,
  ContestProblemSummary,
  GlobalRankingEntry,
  ParticipationStatus,
  VirtualContestSession,
  UserContestHistory,
  RatingHistoryEntry,
  PaginatedResult,
  ContestFilters,
  ContestAnnouncement,
  RankingEntry,
  ContestScoringMode,
  ContestTieBreaker,
  LiveRankingEntry,
} from "@/types/contest";
import type { SubmissionRecord } from "@/types/submission";

// ============================================================================
// Mapper Functions (pure camelCase — backend returns camelCase via Jackson)
// ============================================================================

function toNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function mapContestListItem(raw: Record<string, unknown>): ContestListItem {
  const startTime = raw.startTime as string | undefined;
  const endTime = raw.endTime as string | undefined;
  const duration = toNumber(raw.duration) ?? 0;

  return {
    id: raw.id as string,
    slug: raw.slug as string,
    title: raw.title as string,
    status: raw.status as ContestListItem["status"],
    startTime: startTime ?? "",
    endTime: endTime ?? "",
    duration,
    contestType: raw.contestType as ContestListItem["contestType"],
    participantCount: toNumber(raw.participantCount) ?? 0,
    problemCount: toNumber(raw.problemCount) ?? 0,
    isPremium: raw.isPremium as boolean,
    isPublished: raw.isPublished as boolean,
    isVisible: raw.isVisible as boolean,
    maxParticipants: toNumber(raw.maxParticipants) ?? 0,
    registeredCount: toNumber(raw.registeredCount) ?? 0,
    isParticipating: raw.isParticipating as boolean,
    userRanking: toNumber(raw.userRanking) ?? 0,
    isRated: raw.isRated as boolean,
    scoringMode: raw.scoringMode as ContestScoringMode,
    penaltyPerWrong: toNumber(raw.penaltyPerWrong) ?? 0,
    coverImage: (raw.coverImage as string) ?? "",
  };
}

function mapContestDetail(raw: Record<string, unknown>): ContestDetail {
  const base = mapContestListItem(raw);
  return {
    ...base,
    description: (raw.description as string) ?? "",
    isVirtual: raw.isVirtual as boolean,
    submissionCount: toNumber(raw.submissionCount) ?? 0,
    rules: (raw.rules as string) ?? "",
    registrationStart: (raw.registrationStart as string) ?? "",
    registrationEnd: (raw.registrationEnd as string) ?? "",
    freezeTime: (raw.freezeTime as string) ?? "",
    actualStartTime: (raw.actualStartTime as string) ?? "",
    actualEndTime: (raw.actualEndTime as string) ?? "",
    tieBreaker: raw.tieBreaker as ContestTieBreaker,
    scoringRuleId: (raw.scoringRuleId as string) ?? "",
    createdAt: (raw.createdAt as string) ?? "",
    updatedAt: (raw.updatedAt as string) ?? "",
    createdById: toNumber(raw.createdById) ?? 0,
    createdByUsername: (raw.createdByUsername as string) ?? "",
    problemIds: (raw.problemIds as number[]) ?? [],
    tags: (raw.tags as string[]) ?? [],
    userScore: toNumber(raw.userScore) ?? 0,
  };
}

function mapContestProblem(raw: Record<string, unknown>): ContestProblemSummary {
  return {
    id: raw.id as string,
    contestId: raw.contestId as string,
    problemId: toNumber(raw.problemId) ?? 0,
    problemIndex: (raw.problemIndex as string) ?? "",
    score: toNumber(raw.score) ?? 0,
    penaltyPerWrong: toNumber(raw.penaltyPerWrong) ?? 0,
    title: (raw.title as string) ?? "",
    slug: (raw.slug as string) ?? "",
    difficulty: (raw.difficulty as string) ?? "",
    solvedCount: toNumber(raw.solvedCount) ?? 0,
    submissionCount: toNumber(raw.submissionCount) ?? 0,
    acceptanceRate: toNumber(raw.acceptanceRate) ?? 0,
  };
}

function mapGlobalRankingEntry(
  raw: Record<string, unknown>,
): GlobalRankingEntry {
  const rating = toNumber(raw.rating) ?? 0;
  const ratingTitle = (raw.ratingTitle as GlobalRankingEntry["ratingTitle"]) ?? "NEWBIE";
  const maxRating = toNumber(raw.maxRating) ?? rating;
  const maxRatingTitle = (raw.maxRatingTitle as GlobalRankingEntry["maxRatingTitle"]) ?? ratingTitle;

  return {
    rank: toNumber(raw.rank) ?? 0,
    userId: (raw.userId as string) ?? "",
    username: (raw.username as string) ?? "",
    avatar: (raw.avatar as string | null) ?? null,
    country: (raw.country as string | null) ?? null,
    rating,
    maxRating,
    ratingTitle,
    maxRatingTitle,
    contestsAttended: toNumber(raw.contestsAttended) ?? 0,
    badge: (raw.badge as string | null) ?? null,
  };
}

function mapPaginatedContestList(
  result: PaginatedResult<Record<string, unknown>>,
): PaginatedResult<ContestListItem> {
  return {
    items: (result.items || []).map((r) => mapContestListItem(r)),
    total: result.total ?? 0,
    page: result.page ?? 1,
    pageSize: result.pageSize ?? 20,
    totalPages: result.totalPages ?? 0,
  };
}

// ============================================================================
// CONTEST QUERIES
// ============================================================================

export async function fetchUpcomingContests(): Promise<PaginatedResult<ContestListItem>> {
  const result = await apiGet<PaginatedResult<Record<string, unknown>>>(
    "/contest/upcoming",
  );
  return mapPaginatedContestList(result);
}

export async function fetchRunningContests(): Promise<PaginatedResult<ContestListItem>> {
  const result = await apiGet<PaginatedResult<Record<string, unknown>>>(
    "/contest/running",
  );
  return mapPaginatedContestList(result);
}

export async function fetchPastContests(
  page: number = 1,
  pageSize: number = 10,
): Promise<PaginatedResult<ContestListItem>> {
  const result = await apiGet<PaginatedResult<Record<string, unknown>>>(
    "/contest/past",
    { params: { page, pageSize } },
  );
  return mapPaginatedContestList(result);
}

export async function fetchContestDetail(
  contestId: string,
): Promise<ContestDetail> {
  const raw = await apiGet<Record<string, unknown>>(
    `/contest/${contestId}`,
  );
  return mapContestDetail(raw);
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

  const result = await apiGet<PaginatedResult<Record<string, unknown>>>(
    url,
  );
  return mapPaginatedContestList(result);
}

export async function getContest(slug: string): Promise<ContestDetail> {
  return fetchContestDetail(slug);
}

export async function getContestProblems(
  slug: string,
): Promise<ContestProblemSummary[]> {
  const data = await apiGet<Record<string, unknown>[]>(
    `/contest/${slug}/problems`,
  );
  return (data || []).map((r) => mapContestProblem(r));
}

export async function getAnnouncements(
  slug: string,
): Promise<ContestAnnouncement[]> {
  return apiGet<ContestAnnouncement[]>(
    `/contest/${slug}/announcements`,
  );
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
  return apiGet<LiveRankingEntry[]>(
    `/contest/${contestId}/live-ranking`,
    { params: { limit } },
  );
}

export async function fetchGlobalRankings(options?: {
  page?: number;
  limit?: number;
  country?: string;
}): Promise<PaginatedResult<GlobalRankingEntry>> {
  const { page = 1, limit = 50, country } = options || {};
  const result = await apiGet<PaginatedResult<Record<string, unknown>>>(
    "/contest/rankings/global",
    { params: country ? { page, limit, country } : { page, limit } },
  );
  return {
    ...result,
    items: (result.items || []).map((r) =>
      mapGlobalRankingEntry(r as Record<string, unknown>),
    ),
  };
}

export async function getRanking(
  slug: string,
  options?: { page?: number; limit?: number; includeVirtual?: boolean },
): Promise<PaginatedResult<RankingEntry>> {
  const { page = 1, limit = 50, includeVirtual = true } = options || {};
  return apiGet<PaginatedResult<RankingEntry>>(
    `/contest/${slug}/ranking`,
    { params: { page, limit, includeVirtual } },
  );
}

// ============================================================================
// PARTICIPATION
// ============================================================================

export async function registerForContest(contestId: string): Promise<void> {
  return apiPost<void>(`/contest/${contestId}/register`);
}

export async function unregisterFromContest(
  contestId: string,
): Promise<void> {
  return apiDelete<void>(`/contest/${contestId}/register`);
}

export async function fetchParticipationStatus(
  contestId: string,
): Promise<ParticipationStatus> {
  return apiGet<ParticipationStatus>(
    `/contest/${contestId}/participation`,
  );
}

export async function register(slug: string): Promise<void> {
  return registerForContest(slug);
}

export async function checkIn(slug: string): Promise<void> {
  return apiPost<void>(`/contest/${slug}/check-in`);
}

export async function withdraw(slug: string): Promise<void> {
  return unregisterFromContest(slug);
}

export async function getMyParticipation(
  slug: string,
): Promise<ParticipationStatus> {
  return fetchParticipationStatus(slug);
}

// ============================================================================
// VIRTUAL CONTEST
// ============================================================================

export async function startVirtualContest(
  contestId: string,
): Promise<VirtualContestSession> {
  return apiPost<VirtualContestSession>(
    `/contest/${contestId}/virtual/start`,
  );
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
  const result = await apiGet<Record<string, unknown>[]>(
    "/contest/user/my-contests",
    { params: { type } },
  );
  return result.map((r) => mapContestListItem(r));
}

export async function fetchUserContestHistory(): Promise<
  UserContestHistory[]
> {
  return apiGet<UserContestHistory[]>("/contest/user/history");
}

export async function fetchUserRatingHistory(): Promise<
  RatingHistoryEntry[]
> {
  return apiGet<RatingHistoryEntry[]>(
    "/contest/user/rating-history",
  );
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
