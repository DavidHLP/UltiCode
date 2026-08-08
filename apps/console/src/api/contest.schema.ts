import { z } from "zod";
import { ContestType, ContestStatus } from "@/types/contest";

// ============================================================================
// ENUM SCHEMAS
// ============================================================================

export const contestStatusSchema = z.nativeEnum(ContestStatus);
export const contestTypeSchema = z.nativeEnum(ContestType);
export const contestScoringModeSchema = z.enum(["SCORE", "ICPC", "IOI"]);
export const contestTieBreakerSchema = z.enum([
  "LAST_SOLVE_TIME",
  "TOTAL_TIME",
  "TOTAL_ATTEMPTS",
  "NONE",
]);
export const ratingTitleSchema = z.enum([
  "NEWBIE",
  "PUPIL",
  "SPECIALIST",
  "EXPERT",
  "CANDIDATE_MASTER",
  "MASTER",
  "INTERNATIONAL_MASTER",
  "GRANDMASTER",
  "INTERNATIONAL_GRANDMASTER",
  "LEGENDARY_GRANDMASTER",
]);

// ============================================================================
// CONTEST LIST ITEM — Matches backend ContestListVO
// ============================================================================

export const contestListItemSchema = z.object({
  id: z.string(),
  slug: z.string(),
  title: z.string(),
  status: z.union([contestStatusSchema, z.string()]),
  startTime: z.string(),
  endTime: z.string().nullable().default(null),
  duration: z.number().int(),
  contestType: z.union([contestTypeSchema, z.string()]),
  participantCount: z.number().int().default(0),
  problemCount: z.number().int().default(0),
  isPremium: z.boolean().default(false),
  isPublished: z.boolean().default(false),
  isVisible: z.boolean().default(false),
  maxParticipants: z.number().int().default(0),
  registeredCount: z.number().int().default(0),
  isParticipating: z.boolean().default(false),
  userRanking: z.number().int().default(0),
  isRated: z.boolean().default(false),
  scoringMode: contestScoringModeSchema,
  penaltyPerWrong: z.number().int().default(0),
  coverImage: z.string().default(""),
});

export type ContestListItemInput = z.infer<typeof contestListItemSchema>;

// ============================================================================
// CONTEST DETAIL — Matches backend ContestVO (extends ContestListVO fields)
// ============================================================================

export const contestDetailSchema = contestListItemSchema.extend({
  description: z.string().default(""),
  isVirtual: z.boolean().default(false),
  submissionCount: z.number().int().default(0),
  rules: z.string().default(""),
  registrationStart: z.string().default(""),
  registrationEnd: z.string().default(""),
  freezeTime: z.string().default(""),
  actualStartTime: z.string().default(""),
  actualEndTime: z.string().default(""),
  tieBreaker: contestTieBreakerSchema,
  scoringRuleId: z.string().default(""),
  createdAt: z.string().default(""),
  updatedAt: z.string().default(""),
  createdById: z.number().int().default(0),
  createdByUsername: z.string().default(""),
  problemIds: z.array(z.number().int()).default([]),
  tags: z.array(z.string()).default([]),
  userScore: z.number().int().default(0),
});

export type ContestDetailInput = z.infer<typeof contestDetailSchema>;

// ============================================================================
// CONTEST PROBLEM SUMMARY — Matches backend ContestProblemVO
// ============================================================================

export const contestProblemSummarySchema = z.object({
  id: z.string(),
  contestId: z.string(),
  problemId: z.number().int(),
  problemIndex: z.string().default(""),
  score: z.number().int().default(0),
  penaltyPerWrong: z.number().int().default(0),
  title: z.string().default(""),
  slug: z.string().default(""),
  difficulty: z.string().default(""),
  solvedCount: z.number().int().default(0),
  submissionCount: z.number().int().default(0),
  acceptanceRate: z.number().default(0),
});

export type ContestProblemSummaryInput = z.infer<
  typeof contestProblemSummarySchema
>;

// ============================================================================
// GLOBAL RANKING ENTRY — Matches backend GlobalRankingVO
// ============================================================================

export const globalRankingEntrySchema = z.object({
  rank: z.number().int(),
  userId: z.string(),
  username: z.string(),
  name: z.string().nullable().default(null),
  avatar: z.string().nullable().default(null),
  country: z.string().nullable().default(null),
  rating: z.number().int().nullable().default(null),
  maxRating: z.number().int().nullable().default(null),
  ratingTitle: ratingTitleSchema.default("NEWBIE"),
  maxRatingTitle: ratingTitleSchema.default("NEWBIE"),
  contestsAttended: z.number().int().default(0),
  badge: z.string().nullable().default(null),
});

export type GlobalRankingEntryInput = z.infer<typeof globalRankingEntrySchema>;

// ============================================================================
// PAGINATION HELPER
// ============================================================================

export function paginatedSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    total: z.number().int().default(0),
    page: z.number().int().default(1),
    pageSize: z.number().int().default(20),
    totalPages: z.number().int().default(0),
  });
}
