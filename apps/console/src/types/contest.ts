import type { PageResult } from '@/shared/domain-types/src'

// ============================================================================
// ENUMS — Aligned with backend ContestQueryDTO/ContestVO
// ============================================================================

export enum ContestType {
  ICPC = "ICPC",
  IOI = "IOI",
  CUSTOM = "CUSTOM",
}

export enum ContestStatus {
  DRAFT = "DRAFT",
  UPCOMING = "UPCOMING",
  RUNNING = "RUNNING",
  FINISHED = "FINISHED",
  CANCELLED = "CANCELLED",
}

export enum ParticipantStatus {
  REGISTERED = "REGISTERED",
  STARTED = "STARTED",
  FINISHED = "FINISHED",
  DISQUALIFIED = "DISQUALIFIED",
}

export type ContestScoringMode = "SCORE" | "ICPC" | "IOI";
export type ContestTieBreaker =
  | "LAST_SOLVE_TIME"
  | "TOTAL_TIME"
  | "TOTAL_ATTEMPTS"
  | "NONE";
export type VirtualContestStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

// Codeforces-style rating titles
export type RatingTitle =
  | "NEWBIE"
  | "PUPIL"
  | "SPECIALIST"
  | "EXPERT"
  | "CANDIDATE_MASTER"
  | "MASTER"
  | "INTERNATIONAL_MASTER"
  | "GRANDMASTER"
  | "INTERNATIONAL_GRANDMASTER"
  | "LEGENDARY_GRANDMASTER";

// ============================================================================
// RATING HELPERS
// ============================================================================

export const RATING_THRESHOLDS: Record<RatingTitle, number> = {
  NEWBIE: 0,
  PUPIL: 1200,
  SPECIALIST: 1400,
  EXPERT: 1600,
  CANDIDATE_MASTER: 1900,
  MASTER: 2100,
  INTERNATIONAL_MASTER: 2300,
  GRANDMASTER: 2400,
  INTERNATIONAL_GRANDMASTER: 2600,
  LEGENDARY_GRANDMASTER: 2900,
};

export const RATING_COLORS: Record<RatingTitle, string> = {
  NEWBIE: "var(--foreground-muted)",
  PUPIL: "var(--status-success-mark)",
  SPECIALIST: "var(--status-info-mark)",
  EXPERT: "var(--accent-primary)",
  CANDIDATE_MASTER: "var(--status-special-mark)",
  MASTER: "var(--status-warning-mark)",
  INTERNATIONAL_MASTER: "var(--status-warning-mark)",
  GRANDMASTER: "var(--status-error-mark)",
  INTERNATIONAL_GRANDMASTER: "var(--status-error-mark)",
  LEGENDARY_GRANDMASTER: "var(--status-error-mark)",
};

export function getRatingTitle(rating: number): RatingTitle {
  if (rating >= RATING_THRESHOLDS.LEGENDARY_GRANDMASTER)
    return "LEGENDARY_GRANDMASTER";
  if (rating >= RATING_THRESHOLDS.INTERNATIONAL_GRANDMASTER)
    return "INTERNATIONAL_GRANDMASTER";
  if (rating >= RATING_THRESHOLDS.GRANDMASTER) return "GRANDMASTER";
  if (rating >= RATING_THRESHOLDS.INTERNATIONAL_MASTER)
    return "INTERNATIONAL_MASTER";
  if (rating >= RATING_THRESHOLDS.MASTER) return "MASTER";
  if (rating >= RATING_THRESHOLDS.CANDIDATE_MASTER) return "CANDIDATE_MASTER";
  if (rating >= RATING_THRESHOLDS.EXPERT) return "EXPERT";
  if (rating >= RATING_THRESHOLDS.SPECIALIST) return "SPECIALIST";
  if (rating >= RATING_THRESHOLDS.PUPIL) return "PUPIL";
  return "NEWBIE";
}

export function getRatingColor(rating: number): string {
  return RATING_COLORS[getRatingTitle(rating)];
}

// ============================================================================
// CONTEST LIST ITEM — Matches backend ContestListVO (pure camelCase)
// ============================================================================

export interface ContestListItem {
  id: string;
  slug: string;
  title: string;
  status: ContestStatus | string;
  startTime: string;
  endTime: string | null;
  duration: number;
  contestType: ContestType | string;
  participantCount: number;
  problemCount: number;
  isPremium: boolean;
  isPublished: boolean;
  isVisible: boolean;
  maxParticipants: number;
  registeredCount: number;
  isParticipating: boolean;
  userRanking: number;
  isRated: boolean;
  scoringMode: ContestScoringMode;
  penaltyPerWrong: number;
  coverImage: string;
}

// ============================================================================
// CONTEST DETAIL — Matches backend ContestVO (extends ContestListItem)
// ============================================================================

export interface ContestDetail extends ContestListItem {
  description: string;
  isVirtual: boolean;
  submissionCount: number;
  rules: string;
  registrationStart: string;
  registrationEnd: string;
  freezeTime: string;
  actualStartTime: string;
  actualEndTime: string;
  tieBreaker: ContestTieBreaker;
  scoringRuleId: string;
  createdAt: string;
  updatedAt: string;
  createdById: number;
  createdByUsername: string;
  problemIds: number[];
  tags: string[];
  userScore: number;
}

// ============================================================================
// CONTEST PROBLEM — Matches backend ContestProblemVO
// ============================================================================

export interface ContestProblemSummary {
  id: string;
  contestId: string;
  problemId: number;
  problemIndex: string;
  score: number;
  penaltyPerWrong: number;
  title: string;
  slug: string;
  difficulty: string;
  solvedCount: number;
  submissionCount: number;
  acceptanceRate: number;
}

export interface ContestProblem {
  id: string;
  contestId: string;
  problemId: string;
  problemIndex: string;
  score: number;
  penaltyPerWrong: number;
  order: number;
  title: string;
  slug: string;
  difficulty: string;
  solvedCount: number;
  submissionCount: number;
}

// ============================================================================
// PARTICIPATION — Matches backend ParticipationStatusDTO
// ============================================================================

export interface ParticipationStatus {
  contestId: string;
  title: string;
  status: ParticipantStatus | string;
  registeredAt: string;
  startedAt: string;
  completedAt: string;
  startTime: string;
  endTime: string;
  ranking: number;
  score: number;
  problemsSolved: number;
  totalProblems: number;
  hasStarted: boolean;
  isActive: boolean;
  isCompleted: boolean;
  canParticipate: boolean;
}

export interface VirtualContestSession {
  id: string;
  contestId: string;
  title?: string;
  status: VirtualContestStatus;
  registeredAt?: string;
  startedAt: string;
  endsAt: string;
  /**
   * 后端 ParticipationStatusDTO.score（用户当前总分）。
   */
  score?: number;
  /**
   * 后端 ParticipationStatusDTO.penalty（用户累计罚时，秒）。
   */
  penalty?: number;
  /**
   * 后端 ParticipationStatusDTO 同时返回的语义布尔（/virtual/start、/virtual/session）。
   * 前端优先用 isActive 判定会话活跃，避免 status 字面量跨栈错配
   * （后端 "started" vs 前端 VirtualContestStatus "IN_PROGRESS"）。
   */
  isActive?: boolean;
  hasStarted?: boolean;
  isCompleted?: boolean;
}

// ============================================================================
// RANKING — Matches backend ContestRankingVO
// ============================================================================

export interface ProblemResultEntry {
  problemIndex: string;
  problemId: number;
  isSolved: boolean;
  score: number;
  attempts: number;
  wrongAttempts: number;
  solveTime: number | null;
  penaltyTime: number;
}

export interface ContestRankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  score: number;
  penalty: number;
  problemsSolved: number;
  finishTime: number | null;
  isCurrentUser: boolean;
  progress: number | null;
  percentile: number | null;
  isParticipating: boolean;
  country: string | null;
  maxRating: number;
  ratingTitle: string;
  maxRatingTitle: string;
  contestsAttended: number;
  badge: string | null;
}

export interface GlobalRankingEntry {
  rank: number;
  userId: string;
  username: string;
  name: string | null;
  avatar: string | null;
  country: string | null;
  rating: number | null;
  maxRating: number | null;
  ratingTitle: RatingTitle;
  maxRatingTitle: RatingTitle;
  contestsAttended: number;
  badge: string | null;
}

// ============================================================================
// LIVE RANKING — Matches backend LiveRankingEntryVO
// ============================================================================

export interface LiveRankingEntry {
  rank: number | null;
  userId: string;
  username: string;
  name: string | null;
  avatar: string | null;
  score: number | null;
  penalty: number | null;
  problemsSolved: number;
  isCurrentUser: boolean | null;
  country?: string | null;
  ratingTitle?: string;
  finishTime?: number | null;
  progress?: number | null;
  percentile?: number | null;
  isParticipating?: boolean;
  maxRating?: number;
  maxRatingTitle?: string;
  contestsAttended?: number;
  badge?: string | null;
}

// ============================================================================
// RANKING ENTRY (for contest detail ranking display)
// ============================================================================

export interface RankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  country: string | null;
  score: number;
  penalty: number;
  problemsSolved: number;
  finishTime: number | null;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
  isVirtual: boolean;
  problemResults: ProblemResult[];
}

export interface ProblemResult {
  problemIndex: string;
  problemId: string;
  isSolved: boolean;
  score: number;
  attempts: number;
  wrongAttempts: number;
  solveTime: number | null;
  penaltyTime: number;
  firstSolve: boolean;
}

// ============================================================================
// USER CONTEST HISTORY — Matches backend UserContestHistoryVO
// ============================================================================

export interface UserContestHistory {
  contestId: string;
  title: string | null;
  slug: string | null;
  startTime: string | null;
  finishTime: string | null;
  rank: number | null;
  score: number | null;
  penalty: number | null;
  problemsSolved: number;
  totalParticipants: number | null;
  isRated: boolean | null;
}

// ============================================================================
// PAGINATED RESPONSE — alias to canonical PageResult from shared/domain-types
// ============================================================================

export type PaginatedResult<T> = PageResult<T>


// ============================================================================
// STATS
// ============================================================================

export interface ContestStats {
  totalParticipants: number;
  totalContests: number;
}

// ============================================================================
// CONTEST ANNOUNCEMENT — Matches backend ContestAnnouncement entity
// ============================================================================

export interface ContestAnnouncement {
  id: string;
  contestId: string;
  title: string;
  content: string;
  isPinned: boolean;
  createdAt: string;
  updatedAt: string;
  author: {
    id: string;
    username: string;
  } | null;
}

// ============================================================================
// CONTEST PARTICIPANT
// ============================================================================

export interface ContestParticipant {
  id: string;
  contestId: string;
  userId: string;
  status: ParticipantStatus;
  checkedInAt: string;
  startedAt: string;
  finishedAt: string;
  score: number;
  penalty: number;
  problemsSolved: number;
  isVirtual: boolean;
  registeredAt: string;
  user: {
    id: string;
    username: string;
    avatar: string | null;
  } | null;
}

// ============================================================================
// FIRST SOLVE NOTIFICATION
// ============================================================================

export interface FirstSolveNotification {
  contestId: string;
  problemIndex: string;
  problemId: string;
  userId: string;
  username: string;
  solvedAt: string;
  solveTime: number;
}

// ============================================================================
// CONTEST (Full interface for admin/management)
// ============================================================================

export interface Contest {
  id: string;
  title: string;
  slug: string;
  description: string;
  rules: string;
  coverImage: string;
  contestType: ContestType;
  status: ContestStatus;
  startTime: string;
  endTime: string | null;
  duration: number;
  isRated: boolean;
  isVisible: boolean;
  isPremium: boolean;
  isPublished: boolean;
  scoringMode: ContestScoringMode;
  tieBreaker: ContestTieBreaker;
  penaltyPerWrong: number;
  freezeTime: string;
  maxParticipants: number;
  registrationStart: string;
  registrationEnd: string;
  isVirtual: boolean;
  createdByUsername: string;
  createdAt: string;
  updatedAt: string;
}

// ============================================================================
// FILTERS — Matches backend ContestQueryDTO
// ============================================================================

export interface ContestFilters {
  status?: ContestStatus | ContestStatus[];
  contestType?: ContestType | ContestType[];
  isRated?: boolean;
  search?: string;
  startDateFrom?: string;
  startDateTo?: string;
  page?: number;
  pageSize?: number;
  sort?: "startTime" | "endTime" | "createdAt" | "title";
  direction?: "asc" | "desc";
}
