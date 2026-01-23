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
} from "@/types/contest";
import type { SubmissionRecord } from "@/types/submission";

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
  const contest = data as Record<string, unknown>;
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
    contest.scoringMode) as ContestListItem["scoring_mode"];
  const tieBreaker = (contest.tie_breaker ??
    contest.tieBreaker) as ContestListItem["tie_breaker"];
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
      scoringMode ?? (contest.scoring_mode as ContestListItem["scoring_mode"]),
    tie_breaker:
      tieBreaker ?? (contest.tie_breaker as ContestListItem["tie_breaker"]),
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
  const problem = data as Record<string, unknown>;
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
  const contest = data as Record<string, unknown>;
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
  const ranking = data as Record<string, unknown>;
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

export async function fetchContestList(): Promise<ContestListItem[]> {
  const result = await apiGet<{ items: ContestListItem[] }>("/contest/list");
  return (result.items || []).map(mapContestListItem);
}

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
    `/contest/past?page=${page}&limit=${pageSize}`,
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

export async function fetchContestStats(): Promise<ContestStats> {
  return apiGet<ContestStats>("/contest/stats");
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
    `/contest/${contestId}/ranking?page=${page}&limit=${limit}&include_virtual=${includeVirtual}`,
  );
}

export async function fetchLiveRanking(
  contestId: string,
  limit: number = 100,
): Promise<ContestRankingEntry[]> {
  return apiGet<ContestRankingEntry[]>(
    `/contest/${contestId}/live-ranking?limit=${limit}`,
  );
}

export async function fetchGlobalRankings(options?: {
  page?: number;
  limit?: number;
  country?: string;
}): Promise<PaginatedResult<GlobalRankingEntry>> {
  const { page = 1, limit = 50, country } = options || {};
  let url = `/rankings/global?page=${page}&limit=${limit}`;
  if (country) {
    url += `&country=${country}`;
  }
  const result = await apiGet<PaginatedResult<GlobalRankingEntry>>(url);
  return {
    ...result,
    items: (result.items || []).map(mapGlobalRankingEntry),
  };
}

// Legacy API for backward compatibility
export async function fetchGlobalRankingsLegacy(): Promise<
  GlobalRankingEntry[]
> {
  const result = await apiGet<GlobalRankingEntry[]>("/contest/global-ranking");
  return (result || []).map(mapGlobalRankingEntry);
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
    `/contest/user/my-contests?type=${type}`,
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
// PUBLIC USER DATA
// ============================================================================

export async function fetchUserContestHistoryById(
  userId: string,
): Promise<UserContestHistory[]> {
  return apiGet<UserContestHistory[]>(`/rankings/user/${userId}`);
}

export async function fetchUserRatingHistoryById(
  userId: string,
): Promise<RatingHistoryEntry[]> {
  return apiGet<RatingHistoryEntry[]>(
    `/rankings/user/${userId}/rating-history`,
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
