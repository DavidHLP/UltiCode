/**
 * @ulticode/domain-types — cross-stack DTO contract shared by console + management.
 *
 * Single source of truth for domain types previously duplicated between
 * console/src/types/ and management/src/api/admin/. Mirrors the
 * @ulticode/sandbox-types pattern. Both apps re-export from this package.
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
  acceptance_rate: number
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
