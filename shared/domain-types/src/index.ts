/**
 * @ulticode/domain-types — cross-stack DTO contract shared by console + management.
 *
 * Proven canonical contract: `PageResult<T>` (consumed by callers across both
 * apps). The Problem / Contest / Comment / ... DTOs below are declared here as
 * the intended canonical home but are not yet consumed by production callers —
 * both apps still keep parallel definitions. Migrate them one-by-one only as a
 * caller is proven to need the shared shape; do not balloon this into a giant
 * superset (arch review #5).
 *
 * Backend serves camelCase JSON (Spring Boot default Jackson, no snake_case
 * naming strategy), so DTO fields are camelCase. Database snake_case columns
 * are mapped via MyBatis mapUnderscoreToCamelCase and never leak into transport.
 */

// ============================================================================
// Generic response shapes
// ============================================================================

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

// ============================================================================
// Problem domain
// ============================================================================

export type ProblemDifficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type ProblemStatus = 'solved' | 'attempted' | 'todo'

export interface Problem {
  id: number
  title: string
  slug: string
  difficulty: ProblemDifficulty
  acceptanceRate?: number
  tags: string[]
  status?: ProblemStatus
  isPremium?: boolean
  hasSolution?: boolean
  completedTime?: string
  sortOrder?: number
  addedAt?: string
}

// ============================================================================
// Contest domain
// ============================================================================

export enum ContestStatus {
  DRAFT = 'DRAFT',
  UPCOMING = 'UPCOMING',
  RUNNING = 'RUNNING',
  FINISHED = 'FINISHED',
  CANCELLED = 'CANCELLED',
}

export enum ContestType {
  ICPC = 'ICPC',
  IOI = 'IOI',
  CUSTOM = 'CUSTOM',
}

export enum ParticipantStatus {
  REGISTERED = 'REGISTERED',
  STARTED = 'STARTED',
  FINISHED = 'FINISHED',
  DISQUALIFIED = 'DISQUALIFIED',
}

export type ContestScoringMode = 'SCORE' | 'ICPC' | 'IOI'

export interface ContestProblem {
  problemId: number
  problemIndex: string
  score?: number
  penaltyPerWrong?: number
}

export interface RankingEntry {
  rank: number
  participantId: string
  userId: string
  username: string
  score: number
  penalty: number
  acceptedCount: number
}

// ============================================================================
// Comment + Forum + UserStats + ProblemList domains
// ============================================================================

export interface Comment {
  id: string
  content: string
  authorId: string
  authorName?: string
  authorAvatar?: string
  createdAt: string
  updatedAt?: string
  likes?: number
  isLiked?: boolean
  parentId?: string | null
}

export interface ForumPost {
  id: string
  title: string
  content: string
  authorId: string
  authorName?: string
  communityId?: string
  tags?: string[]
  viewCount: number
  likeCount: number
  dislikeCount: number
  commentCount: number
  isPinned: boolean
  isLocked: boolean
  createdAt: string
  updatedAt?: string
}

export interface ForumCommunity {
  id: string
  name: string
  slug: string
  description?: string
  postCount: number
  memberCount: number
  createdAt: string
}

export interface ForumUser {
  id: string
  username: string
  avatar?: string
}

export interface UserStats {
  userId: string
  username: string
  totalSubmissions: number
  acceptedSubmissions: number
  acceptanceRate: number
  rating?: number
  ranking?: number
  contestCount?: number
  streak?: number
}

export interface ProblemList {
  id: string
  title: string
  slug: string
  description?: string
  isPublic: boolean
  problemCount: number
  authorId: string
  authorName?: string
  tags?: string[]
  createdAt: string
  updatedAt?: string
}
