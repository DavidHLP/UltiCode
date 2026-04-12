import { apiGet, apiPost, apiDelete } from "@/utils/request";
import { mapSubmission } from "@/api/submission";
import type {
  ContestListItem,
  ContestDetail,
  ContestRankingEntry,
  ContestProblemSummary,
  GlobalRankingEntry,
  ContestStats,
  ParticipationStatus,
  VirtualContestSession,
  UserContestHistory,
  RatingHistoryEntry,
  PaginatedResult,
  ContestFilters,
  ContestAnnouncement,
  RankingEntry,
  ContestProblem,
  ContestScoringMode,
  ContestTieBreaker,
} from "@/types/contest";
import type { SubmissionRecord } from "@/types/submission";

// ============================================================================
// Backend Response Interfaces (snake_case from Spring Boot)
// ============================================================================

interface BackendContestListItem {
  start_time?: unknown;
  startTime?: unknown;
  duration_minutes?: unknown;
  durationMinutes?: unknown;
  penalty_per_wrong?: unknown;
  penaltyPerWrong?: unknown;
  scoring_mode?: unknown;
  scoringMode?: unknown;
  tie_breaker?: unknown;
  tieBreaker?: unknown;
  end_time?: unknown;
  endTime?: unknown;
  is_rated?: unknown;
  isRated?: unknown;
  contest_type?: unknown;
  type?: unknown;
  status?: unknown;
  registered_count?: unknown;
  registeredCount?: unknown;
  participant_count?: unknown;
  participantCount?: unknown;
  can_register?: unknown;
  canRegister?: unknown;
  can_start?: unknown;
  canStart?: unknown;
  [key: string]: unknown;
}

interface BackendContestProblem {
  penalty_per_wrong?: unknown;
  penaltyPerWrong?: unknown;
  problem_index?: unknown;
  problemIndex?: unknown;
  problem_id?: unknown;
  problemId?: unknown;
  solved_count?: unknown;
  solvedCount?: unknown;
  submission_count?: unknown;
  submissionCount?: unknown;
  acceptance_rate?: unknown;
  acceptanceRate?: unknown;
  problems?: unknown[];
  [key: string]: unknown;
}

interface BackendGlobalRankingEntry {
  rank?: unknown;
  global_rank?: unknown;
  user_id?: unknown;
  userId?: unknown;
  id?: unknown;
  username?: unknown;
  avatar?: unknown;
  country?: unknown;
  rating?: unknown;
  max_rating?: unknown;
  maxRating?: unknown;
  rating_title?: unknown;
  ratingTitle?: unknown;
  max_rating_title?: unknown;
  maxRatingTitle?: unknown;
  contests_attended?: unknown;
  contestsAttended?: unknown;
  badge?: unknown;
}

interface BackendContestProblemForContest {
  problem_id?: unknown;
  problemId?: unknown;
  [key: string]: unknown;
}

function toNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function mapContestListItem(data: unknown): ContestListItem {
  if (!data || typeof data !== "object") return data as ContestListItem;
  const contest = data as BackendContestListItem;
  const base = data as ContestListItem;
  const startTime = (contest.start_time ?? contest.startTime) as
    | string
    | undefined;
  const durationMinutes = toNumber(
    contest.duration_minutes ?? contest.durationMinutes,
  );
  const penaltyPerWrong = toNumber(
    contest.penalty_per_wrong ?? contest.penaltyPerWrong,
  );
  const scoringMode = (contest.scoring_mode ??
    contest.scoringMode) as ContestScoringMode | undefined;
  const tieBreaker = (contest.tie_breaker ??
    contest.tieBreaker) as ContestTieBreaker | undefined;
  const endTimeRaw = (contest.end_time ?? contest.endTime) as
    | string
    | undefined;
  let endTime = endTimeRaw;

  if (!endTime && startTime && durationMinutes) {
    const startMs = new Date(startTime).getTime();
    if (!Number.isNaN(startMs)) {
      endTime = new Date(startMs + durationMinutes * 60 * 1000).toISOString();
    }
  }

  const isRated =
    typeof contest.is_rated === "boolean"
      ? contest.is_rated
      : (contest.isRated as boolean | undefined);

  return {
    ...base,
    start_time: startTime ?? (contest.start_time as string | undefined),
    duration_minutes:
      durationMinutes ?? (contest.duration_minutes as number | undefined),
    penalty_per_wrong:
      penaltyPerWrong ?? (contest.penalty_per_wrong as number | undefined),
    scoring_mode:
      scoringMode ?? (contest.scoring_mode as ContestScoringMode | undefined),
    tie_breaker:
      tieBreaker ?? (contest.tie_breaker as ContestTieBreaker | undefined),
    startTime,
    endTime,
    end_time: endTime ?? (contest.end_time as string | undefined),
    durationMinutes,
    type: (contest.contest_type ?? contest.type) as ContestListItem["type"],
    isRated,
    penaltyPerWrong,
    scoringMode,
    tieBreaker,
    registeredCount: toNumber(
      contest.registered_count ?? contest.registeredCount,
    ),
    participantCount: toNumber(
      contest.participant_count ?? contest.participantCount,
    ),
    canRegister: (contest.can_register ??
      contest.canRegister ??
      contest.status === "upcoming") as boolean,
    canStart: (contest.can_start ??
      contest.canStart ??
      contest.status === "running") as boolean,
  } as ContestListItem;
}

function mapContestProblem(data: unknown): ContestProblemSummary {
  if (!data || typeof data !== "object") return data as ContestProblemSummary;
  const problem = data as BackendContestProblem;
  const penaltyPerWrong = toNumber(
    problem.penalty_per_wrong ?? problem.penaltyPerWrong,
  );
  return {
    ...problem,
    problemIndex: (problem.problem_index ?? problem.problemIndex) as
      | string
      | undefined,
    problemId: toNumber(problem.problem_id ?? problem.problemId),
    penalty_per_wrong:
      penaltyPerWrong ?? (problem.penalty_per_wrong as number | undefined),
    solvedCount: toNumber(problem.solved_count ?? problem.solvedCount),
    submissionCount: toNumber(
      problem.submission_count ?? problem.submissionCount,
    ),
    acceptanceRate: toNumber(problem.acceptance_rate ?? problem.acceptanceRate),
    penaltyPerWrong,
  } as ContestProblemSummary;
}

function mapContestDetail(data: unknown): ContestDetail {
  if (!data || typeof data !== "object") return data as ContestDetail;
  const contest = data as BackendContestListItem & { problems?: unknown[] };
  const mapped = mapContestListItem(contest) as ContestDetail;
  const problems = Array.isArray(contest.problems)
    ? contest.problems.map(mapContestProblem)
    : [];
  return {
    ...mapped,
    problems,
  };
}

function mapGlobalRankingEntry(data: unknown): GlobalRankingEntry {
  if (!data || typeof data !== "object") return data as GlobalRankingEntry;
  const ranking = data as BackendGlobalRankingEntry;
  const rating = toNumber(ranking.rating) ?? 0;
  const ratingTitle =
    ranking.rating_title ??
    ranking.ratingTitle ??
    (rating > 0 ? "NEWBIE" : "NEWBIE");
  const maxRating = toNumber(ranking.max_rating ?? ranking.maxRating) ?? rating;
  const maxRatingTitle =
    ranking.max_rating_title ?? ranking.maxRatingTitle ?? ratingTitle;

  return {
    rank: toNumber(ranking.rank ?? ranking.global_rank) ?? 0,
    userId: (ranking.user_id ?? ranking.userId ?? ranking.id) as string,
    username: ranking.username as string,
    avatar: (ranking.avatar as string | null) ?? null,
    country: (ranking.country as string | null) ?? null,
    rating,
    maxRating,
    ratingTitle: ratingTitle as GlobalRankingEntry["ratingTitle"],
    maxRatingTitle: maxRatingTitle as GlobalRankingEntry["maxRatingTitle"],
    contestsAttended:
      toNumber(ranking.contests_attended ?? ranking.contestsAttended) ?? 0,
    badge: (ranking.badge as string | null) ?? null,
  };
}

// ============================================================================
// CONTEST QUERIES
// ============================================================================

export async function fetchUpcomingContests(): Promise<ContestListItem[]> {
  const result = await apiGet<ContestListItem[]>("/contest/upcoming");
  return result.map(mapContestListItem);
}

export async function fetchRunningContests(): Promise<ContestListItem[]> {
  const result = await apiGet<ContestListItem[]>("/contest/running");
  return result.map(mapContestListItem);
}

export async function fetchPastContests(
  page: number = 1,
  pageSize: number = 10,
): Promise<{ data: ContestListItem[]; total: number }> {
  const result = await apiGet<{ data: ContestListItem[]; total: number }>(
    "/contest/past",
    { params: { page, limit: pageSize } },
  );
  return {
    ...result,
    data: (result.data || []).map(mapContestListItem),
  };
}

export async function fetchContestDetail(
  contestId: string,
): Promise<ContestDetail> {
  const result = await apiGet<ContestDetail>(`/contest/${contestId}`);
  return mapContestDetail(result);
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
    { params: { page, limit, include_virtual: includeVirtual } },
  );
}

export async function fetchLiveRanking(
  contestId: string,
  limit: number = 100,
): Promise<ContestRankingEntry[]> {
  return apiGet<ContestRankingEntry[]>(
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
  const result = await apiGet<PaginatedResult<GlobalRankingEntry>>(
    "/rankings/global",
    { params: country ? { page, limit, country } : { page, limit } },
  );
  return {
    ...result,
    items: (result.items || []).map(mapGlobalRankingEntry),
  };
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
  return apiPost<void>(`/contest/${contestId}/virtual/finish`, { sessionId });
}

// ============================================================================
// USER CONTESTS
// ============================================================================

export async function fetchUserContests(
  type: "registered" | "participated" | "virtual" = "participated",
): Promise<ContestListItem[]> {
  const result = await apiGet<ContestListItem[]>(
    "/contest/user/my-contests",
    { params: { type } },
  );
  return result.map(mapContestListItem);
}

export async function fetchUserContestHistory(): Promise<UserContestHistory[]> {
  return apiGet<UserContestHistory[]>("/contest/user/history");
}

export async function fetchUserRatingHistory(): Promise<RatingHistoryEntry[]> {
  return apiGet<RatingHistoryEntry[]>("/contest/user/rating-history");
}

// ============================================================================
// CONTEST SUBMISSIONS
// ============================================================================

export interface ContestSubmissionDto {
  language: string;
  code: string;
}

export type ContestSubmissionResult = SubmissionRecord;

/**
 * Submit code for a contest problem
 */
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

/**
 * Get submissions for a specific contest problem
 */
export async function fetchContestProblemSubmissions(
  contestId: string,
  problemId: number,
): Promise<ContestSubmissionResult[]> {
  const data = await apiGet<unknown[]>(
    `/contest/${contestId}/problems/${problemId}/submissions`,
  );
  return data.map((item) => mapSubmission(item) as ContestSubmissionResult);
}

// ============================================================================
// NEW API METHODS (Task 4.1)
// ============================================================================

/**
 * Get contests list with optional filters
 */
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
    if (filters.type) {
      const types = Array.isArray(filters.type) ? filters.type : [filters.type];
      types.forEach((t) => params.append("type", t));
    }
    if (filters.isRated !== undefined) {
      params.append("isRated", String(filters.isRated));
    }
    if (filters.isPublic !== undefined) {
      params.append("isPublic", String(filters.isPublic));
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
    if (filters.limit !== undefined) {
      params.append("limit", String(filters.limit));
    }
    if (filters.sortBy) {
      params.append("sortBy", filters.sortBy);
    }
    if (filters.sortOrder) {
      params.append("sortOrder", filters.sortOrder);
    }
  }

  const queryString = params.toString();
  const url = queryString ? `/contest?${queryString}` : "/contest";

  const result = await apiGet<PaginatedResult<ContestListItem>>(url);
  return {
    ...result,
    items: (result.items || []).map(mapContestListItem),
  };
}

/**
 * Get contest details by slug
 */
export async function getContest(slug: string): Promise<ContestDetail> {
  return fetchContestDetail(slug);
}

/**
 * Get contest problems by slug
 */
export async function getContestProblems(
  slug: string,
): Promise<ContestProblem[]> {
  const data = await apiGet<unknown[]>(`/contest/${slug}/problems`);
  return (data || []).map((item) => {
    const problem = item as BackendContestProblemForContest;
    return {
      ...problem,
      problemId: String(problem.problem_id ?? problem.problemId ?? ""),
    } as ContestProblem;
  });
}

/**
 * Get contest announcements
 */
export async function getAnnouncements(
  slug: string,
): Promise<ContestAnnouncement[]> {
  return apiGet<ContestAnnouncement[]>(`/contest/${slug}/announcements`);
}

/**
 * Get contest ranking with options
 */
export async function getRanking(
  slug: string,
  options?: { page?: number; limit?: number; includeVirtual?: boolean },
): Promise<PaginatedResult<RankingEntry>> {
  const { page = 1, limit = 50, includeVirtual = true } = options || {};
  return apiGet<PaginatedResult<RankingEntry>>(
    `/contest/${slug}/ranking`,
    { params: { page, limit, include_virtual: includeVirtual } },
  );
}

/**
 * Register for a contest
 */
export async function register(slug: string): Promise<void> {
  return registerForContest(slug);
}

/**
 * Check in for a contest
 */
export async function checkIn(slug: string): Promise<void> {
  return apiPost<void>(`/contest/${slug}/check-in`);
}

/**
 * Withdraw from a contest
 */
export async function withdraw(slug: string): Promise<void> {
  return unregisterFromContest(slug);
}

/**
 * Get my participation status in a contest
 */
export async function getMyParticipation(
  slug: string,
): Promise<ParticipationStatus> {
  return fetchParticipationStatus(slug);
}

