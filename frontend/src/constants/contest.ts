/**
 * Contest-related constants and configurations
 */

// Contest Status
export const CONTEST_STATUS = {
  UPCOMING: "upcoming",
  RUNNING: "running",
  FINISHED: "finished",
} as const;

export type ContestStatus =
  (typeof CONTEST_STATUS)[keyof typeof CONTEST_STATUS];

// Contest Types
export const CONTEST_TYPE = {
  WEEKLY: "weekly",
  BIWEEKLY: "biweekly",
  MONTHLY: "monthly",
  SPECIAL: "special",
} as const;

export type ContestType = (typeof CONTEST_TYPE)[keyof typeof CONTEST_TYPE];

// Contest Participant Status
export const PARTICIPANT_STATUS = {
  REGISTERED: "REGISTERED",
  STARTED: "STARTED",
  FINISHED: "FINISHED",
  DISQUALIFIED: "DISQUALIFIED",
} as const;

export type ParticipantStatus =
  (typeof PARTICIPANT_STATUS)[keyof typeof PARTICIPANT_STATUS];

// Virtual Contest Status
export const VIRTUAL_CONTEST_STATUS = {
  NOT_STARTED: "NOT_STARTED",
  IN_PROGRESS: "IN_PROGRESS",
  COMPLETED: "COMPLETED",
} as const;

export type VirtualContestStatus =
  (typeof VIRTUAL_CONTEST_STATUS)[keyof typeof VIRTUAL_CONTEST_STATUS];

// Rating Titles
export const RATING_TITLE = {
  NEWBIE: "NEWBIE",
  PUPIL: "PUPIL",
  SPECIALIST: "SPECIALIST",
  EXPERT: "EXPERT",
  CANDIDATE_MASTER: "CANDIDATE_MASTER",
  MASTER: "MASTER",
  INTERNATIONAL_MASTER: "INTERNATIONAL_MASTER",
  GRANDMASTER: "GRANDMASTER",
  INTERNATIONAL_GRANDMASTER: "INTERNATIONAL_GRANDMASTER",
  LEGENDARY_GRANDMASTER: "LEGENDARY_GRANDMASTER",
} as const;

export type RatingTitle = (typeof RATING_TITLE)[keyof typeof RATING_TITLE];

// Rating Thresholds
export const RATING_THRESHOLDS = {
  [RATING_TITLE.NEWBIE]: 0,
  [RATING_TITLE.PUPIL]: 1200,
  [RATING_TITLE.SPECIALIST]: 1400,
  [RATING_TITLE.EXPERT]: 1600,
  [RATING_TITLE.CANDIDATE_MASTER]: 1900,
  [RATING_TITLE.MASTER]: 2100,
  [RATING_TITLE.INTERNATIONAL_MASTER]: 2300,
  [RATING_TITLE.GRANDMASTER]: 2400,
  [RATING_TITLE.INTERNATIONAL_GRANDMASTER]: 2600,
  [RATING_TITLE.LEGENDARY_GRANDMASTER]: 3000,
} as const;

// Rating Colors
export const RATING_COLORS = {
  [RATING_TITLE.NEWBIE]: "#808080", // Gray
  [RATING_TITLE.PUPIL]: "#008000", // Green
  [RATING_TITLE.SPECIALIST]: "#03A89E", // Cyan
  [RATING_TITLE.EXPERT]: "#0000FF", // Blue
  [RATING_TITLE.CANDIDATE_MASTER]: "#AA00AA", // Violet
  [RATING_TITLE.MASTER]: "#FF8C00", // Orange
  [RATING_TITLE.INTERNATIONAL_MASTER]: "#FF8C00", // Orange
  [RATING_TITLE.GRANDMASTER]: "#FF0000", // Red
  [RATING_TITLE.INTERNATIONAL_GRANDMASTER]: "#FF0000", // Red
  [RATING_TITLE.LEGENDARY_GRANDMASTER]: "#AA0000", // Dark Red
} as const;

// Helper function to get rating title from rating value
export function getRatingTitle(rating: number): RatingTitle {
  if (rating >= RATING_THRESHOLDS.LEGENDARY_GRANDMASTER)
    return RATING_TITLE.LEGENDARY_GRANDMASTER;
  if (rating >= RATING_THRESHOLDS.INTERNATIONAL_GRANDMASTER)
    return RATING_TITLE.INTERNATIONAL_GRANDMASTER;
  if (rating >= RATING_THRESHOLDS.GRANDMASTER) return RATING_TITLE.GRANDMASTER;
  if (rating >= RATING_THRESHOLDS.INTERNATIONAL_MASTER)
    return RATING_TITLE.INTERNATIONAL_MASTER;
  if (rating >= RATING_THRESHOLDS.MASTER) return RATING_TITLE.MASTER;
  if (rating >= RATING_THRESHOLDS.CANDIDATE_MASTER)
    return RATING_TITLE.CANDIDATE_MASTER;
  if (rating >= RATING_THRESHOLDS.EXPERT) return RATING_TITLE.EXPERT;
  if (rating >= RATING_THRESHOLDS.SPECIALIST) return RATING_TITLE.SPECIALIST;
  if (rating >= RATING_THRESHOLDS.PUPIL) return RATING_TITLE.PUPIL;
  return RATING_TITLE.NEWBIE;
}

// Helper function to get rating color
export function getRatingColor(rating: number): string {
  const title = getRatingTitle(rating);
  return RATING_COLORS[title];
}

// Contest submission status
export const SUBMISSION_STATUS = {
  PENDING: "Pending",
  JUDGING: "Judging",
  ACCEPTED: "Accepted",
  WRONG_ANSWER: "Wrong Answer",
  TIME_LIMIT_EXCEEDED: "Time Limit Exceeded",
  MEMORY_LIMIT_EXCEEDED: "Memory Limit Exceeded",
  OUTPUT_LIMIT_EXCEEDED: "Output Limit Exceeded",
  RUNTIME_ERROR: "Runtime Error",
  COMPILE_ERROR: "Compile Error",
  PRESENTATION_ERROR: "Presentation Error",
  SYSTEM_ERROR: "System Error",
} as const;

export type SubmissionStatus =
  (typeof SUBMISSION_STATUS)[keyof typeof SUBMISSION_STATUS];

// Contest problem scoring
export const PROBLEM_SCORES = {
  Q1: 3,
  Q2: 4,
  Q3: 5,
  Q4: 6,
} as const;

// Penalty calculation (in seconds)
export const PENALTY_PER_WRONG_ATTEMPT = 5 * 60; // 5 minutes

// Contest type labels
export const CONTEST_TYPE_LABELS = {
  [CONTEST_TYPE.WEEKLY]: "Weekly Contest",
  [CONTEST_TYPE.BIWEEKLY]: "Biweekly Contest",
  [CONTEST_TYPE.MONTHLY]: "Monthly Contest",
  [CONTEST_TYPE.SPECIAL]: "Special Contest",
} as const;

// Contest status badges
export const CONTEST_STATUS_BADGES = {
  [CONTEST_STATUS.UPCOMING]: {
    label: "Upcoming",
    color: "blue",
  },
  [CONTEST_STATUS.RUNNING]: {
    label: "Running",
    color: "green",
  },
  [CONTEST_STATUS.FINISHED]: {
    label: "Finished",
    color: "gray",
  },
} as const;
