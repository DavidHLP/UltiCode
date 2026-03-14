// ============================================================================
// ENUMS
// ============================================================================

/**
 * Contest type classification
 */
export enum ContestType {
  WEEKLY = "WEEKLY",
  BIWEEKLY = "BIWEEKLY",
  MONTHLY = "MONTHLY",
  THEMED = "THEMED",
  CORPORATE = "CORPORATE",
  CAMPUS = "CAMPUS",
}

/**
 * Contest lifecycle status
 */
export enum ContestStatus {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  REGISTERING = "REGISTERING",
  UPCOMING = "UPCOMING",
  ONGOING = "ONGOING",
  RUNNING = "RUNNING",
  FREEZING = "FREEZING",
  FINISHED = "FINISHED",
  ARCHIVED = "ARCHIVED",
}

/**
 * Participant status in a contest
 */
export enum ParticipantStatus {
  REGISTERED = "REGISTERED",
  CHECKED_IN = "CHECKED_IN",
  STARTED = "STARTED",
  PARTICIPATING = "PARTICIPATING",
  FINISHED = "FINISHED",
  DISQUALIFIED = "DISQUALIFIED",
}

export type ContestScoringMode = "SCORE" | "ICPC";
export type ContestTieBreaker = "LAST_SOLVE_TIME" | "TOTAL_ATTEMPTS" | "NONE";
export type VirtualContestStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

// Legacy type aliases for backward compatibility
export type ContestParticipantStatus = ParticipantStatus;

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
  NEWBIE: "#808080",
  PUPIL: "#008000",
  SPECIALIST: "#03A89E",
  EXPERT: "#0000FF",
  CANDIDATE_MASTER: "#AA00AA",
  MASTER: "#FF8C00",
  INTERNATIONAL_MASTER: "#FF8C00",
  GRANDMASTER: "#FF0000",
  INTERNATIONAL_GRANDMASTER: "#FF0000",
  LEGENDARY_GRANDMASTER: "#FF0000",
};

export const RATING_DISPLAY_NAMES: Record<RatingTitle, string> = {
  NEWBIE: "Newbie",
  PUPIL: "Pupil",
  SPECIALIST: "Specialist",
  EXPERT: "Expert",
  CANDIDATE_MASTER: "Candidate Master",
  MASTER: "Master",
  INTERNATIONAL_MASTER: "International Master",
  GRANDMASTER: "Grandmaster",
  INTERNATIONAL_GRANDMASTER: "International Grandmaster",
  LEGENDARY_GRANDMASTER: "Legendary Grandmaster",
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
// CONTEST TYPES
// ============================================================================

export interface ContestListItem {
  id: string;
  title: string;
  slug: string;
  contest_type: ContestType | string;
  start_time: string;
  duration_minutes: number;
  status: ContestStatus | string;
  penalty_per_wrong?: number;
  scoring_mode?: ContestScoringMode;
  tie_breaker?: ContestTieBreaker;
  registered_count: number;
  participant_count: number;
  is_rated: boolean;
  description?: string;
  cover_image?: string;
  rules?: string;
  // Computed fields
  end_time?: string;
  // Aliases for frontend compatibility
  startTime?: string;
  endTime?: string;
  type?: ContestType | string;
  isRated?: boolean;
  durationMinutes?: number;
  registeredCount?: number;
  participantCount?: number;
  canRegister?: boolean;
  canStart?: boolean;
  penaltyPerWrong?: number;
  scoringMode?: ContestScoringMode;
  tieBreaker?: ContestTieBreaker;
}

export interface ContestProblemSummary {
  id: string;
  contest_id?: string;
  problem_id: number;
  problem_index: string;
  score: number;
  penalty_per_wrong?: number;
  solved_count: number;
  submission_count: number;
  // Problem details
  title?: string;
  slug?: string;
  difficulty?: string;
  acceptanceRate?: number;
  // Aliases
  problemIndex?: string;
  problemId?: number;
  solvedCount?: number;
  submissionCount?: number;
  penaltyPerWrong?: number;
}

export interface ContestDetail extends ContestListItem {
  problems: ContestProblemSummary[];
}

// ============================================================================
// PARTICIPATION TYPES
// ============================================================================

export interface ParticipationStatus {
  isRegistered: boolean;
  status: ParticipantStatus | string | null;
  participantId: string | null;
  virtualSessionId: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  totalScore: number;
  totalPenalty: number;
}

export interface VirtualContestSession {
  id: string;
  contest_id: string;
  user_id: string;
  status: VirtualContestStatus;
  started_at: string | null;
  ends_at: string | null;
  finished_at: string | null;
  total_score: number;
  total_penalty: number;
}

// ============================================================================
// RANKING TYPES
// ============================================================================

export interface ProblemResultEntry {
  problemIndex: string;
  problemId: number;
  isSolved: boolean;
  score: number;
  attempts: number;
  wrongAttempts?: number;
  solveTime: number | null;
  penaltyTime: number;
}

export interface ContestRankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  totalScore: number;
  totalPenalty: number;
  finishTime?: number | null;
  finish_time?: number | null;
  totalAttempts?: number;
  solvedCount: number;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
  isVirtual: boolean;
  problemResults: ProblemResultEntry[];
  // Legacy aliases
  score?: number;
  total_attempts?: number;
  country?: string;
}

export interface GlobalRankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar: string | null;
  country: string | null;
  rating: number;
  maxRating: number;
  ratingTitle: RatingTitle;
  maxRatingTitle: RatingTitle;
  contestsAttended: number;
  badge: string | null;
  // Legacy aliases
  id?: string;
}

// ============================================================================
// USER CONTEST HISTORY
// ============================================================================

export interface UserContestHistory {
  contestId: string;
  contestTitle: string;
  contestDate: string;
  rank: number;
  totalParticipants: number;
  score: number;
  solvedCount: number;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
  isVirtual: boolean;
}

export interface RatingHistoryEntry {
  contestId: string;
  contestTitle: string;
  date: string;
  rank: number;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
}

// ============================================================================
// PAGINATED RESPONSE
// ============================================================================

export interface PaginatedResult<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

// ============================================================================
// STATS
// ============================================================================

export interface ContestStats {
  total_participants: number;
  total_contests: number;
}

// ============================================================================
// CONTEST INTERFACES (Full definitions)
// ============================================================================

/**
 * Full contest interface with all properties
 */
export interface Contest {
  id: string;
  title: string;
  slug: string;
  description?: string;
  rules?: string;
  coverImage?: string;
  type: ContestType;
  status: ContestStatus;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  isRated: boolean;
  isPublic: boolean;
  scoringMode: ContestScoringMode;
  tieBreaker: ContestTieBreaker;
  penaltyPerWrong: number;
  freezeDurationMinutes?: number;
  maxParticipants?: number;
  checkInRequired: boolean;
  checkInStartMinutes: number;
  checkInEndMinutes: number;
  virtualEnabled: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Contest problem with full details
 */
export interface ContestProblem {
  id: string;
  contestId: string;
  problemId: string; // string because JSON doesn't support bigint
  problemIndex: string;
  score: number;
  penaltyPerWrong: number;
  order: number;
  // Problem details
  title: string;
  slug: string;
  difficulty: string;
  solvedCount: number;
  submissionCount: number;
}

/**
 * Contest participant details
 */
export interface ContestParticipant {
  id: string;
  contestId: string;
  userId: string;
  status: ParticipantStatus;
  checkedInAt?: string;
  startedAt?: string;
  finishedAt?: string;
  totalScore: number;
  totalPenalty: number;
  isVirtual: boolean;
  registeredAt: string;
  user?: {
    id: string;
    username: string;
    avatar?: string;
  };
}

/**
 * Ranking entry for display
 */
export interface RankingEntry {
  rank: number;
  userId: string;
  username: string;
  avatar?: string;
  country?: string;
  totalScore: number;
  totalPenalty: number;
  solvedCount: number;
  finishTime?: number;
  ratingBefore: number;
  ratingAfter: number;
  ratingChange: number;
  isVirtual: boolean;
  problemResults: ProblemResult[];
}

/**
 * Problem result in ranking
 */
export interface ProblemResult {
  problemIndex: string;
  problemId: string;
  isSolved: boolean;
  score: number;
  attempts: number;
  wrongAttempts: number;
  solveTime?: number;
  penaltyTime: number;
  firstSolve?: boolean;
}

/**
 * First solve notification for real-time updates
 */
export interface FirstSolveNotification {
  contestId: string;
  problemIndex: string;
  problemId: string;
  userId: string;
  username: string;
  solvedAt: string;
  solveTime: number;
}

/**
 * Contest announcement
 */
export interface ContestAnnouncement {
  id: string;
  contestId: string;
  title: string;
  content: string;
  isPinned: boolean;
  createdAt: string;
  updatedAt: string;
  author?: {
    id: string;
    username: string;
  };
}

/**
 * Filters for contest listing
 */
export interface ContestFilters {
  status?: ContestStatus | ContestStatus[];
  type?: ContestType | ContestType[];
  isRated?: boolean;
  isPublic?: boolean;
  search?: string;
  startDateFrom?: string;
  startDateTo?: string;
  page?: number;
  limit?: number;
  sortBy?: "startTime" | "createdAt" | "title";
  sortOrder?: "asc" | "desc";
}
