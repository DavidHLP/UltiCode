import type { PermissionComputedMap } from './usePermissionMap'
import { PERM } from '@/constants/permissions'

export const analyticsPermissionRules = { read: PERM.ANALYTICS_READ } as const
export type AnalyticsPermissionMap = PermissionComputedMap<typeof analyticsPermissionRules>

export const commentPermissionRules = {
  moderateForum: PERM.MODERATE_FORUM_COMMENT,
  moderateSolution: PERM.MODERATE_SOLUTION_COMMENT,
} as const
export type CommentPermissionMap = PermissionComputedMap<typeof commentPermissionRules>

export const contestPermissionRules = {
  read: PERM.CONTEST_READ,
  create: PERM.CONTEST_CREATE,
  update: PERM.CONTEST_UPDATE,
  delete: PERM.CONTEST_DELETE,
} as const
export type ContestPermissionMap = PermissionComputedMap<typeof contestPermissionRules>

export const forumPermissionRules = {
  moderatePost: PERM.MODERATE_FORUM_POST,
  deletePost: PERM.DELETE_FORUM_POST,
} as const
export type ForumPermissionMap = PermissionComputedMap<typeof forumPermissionRules>

export const problemPermissionRules = {
  read: PERM.PROBLEM_READ,
  create: PERM.PROBLEM_CREATE,
  update: PERM.PROBLEM_UPDATE,
  delete: PERM.PROBLEM_DELETE,
  moderate: PERM.MODERATE_PROBLEM,
} as const
export type ProblemPermissionMap = PermissionComputedMap<typeof problemPermissionRules>

export const problemListPermissionRules = {
  read: PERM.PROBLEM_LIST_READ,
  create: PERM.PROBLEM_LIST_CREATE,
  update: PERM.PROBLEM_LIST_UPDATE,
  delete: PERM.PROBLEM_LIST_DELETE,
  manageProblems: PERM.PROBLEM_LIST_MANAGE_PROBLEMS,
} as const
export type ProblemListPermissionMap = PermissionComputedMap<typeof problemListPermissionRules>

export const solutionPermissionRules = {
  read: PERM.SOLUTION_READ,
  moderate: PERM.MODERATE_SOLUTION,
  delete: PERM.DELETE_SOLUTION,
} as const
export type SolutionPermissionMap = PermissionComputedMap<typeof solutionPermissionRules>

export const systemPermissionRules = {
  read: PERM.SYSTEM_READ,
  update: PERM.SYSTEM_UPDATE,
  manageUsers: PERM.SYSTEM_MANAGE_USERS,
} as const
export type SystemPermissionMap = PermissionComputedMap<typeof systemPermissionRules>

export const tagPermissionRules = {
  read: PERM.TAG_READ,
  update: PERM.TAG_UPDATE,
  // System managers and problem editors can manage the shared tag catalog.
  manage: [PERM.SYSTEM_MANAGE_USERS, PERM.PROBLEM_UPDATE],
} as const
export type TagPermissionMap = PermissionComputedMap<typeof tagPermissionRules>

export const userPermissionRules = {
  read: PERM.USER_READ,
  create: PERM.USER_CREATE,
  update: PERM.USER_UPDATE,
  delete: PERM.USER_DELETE,
  moderate: PERM.MODERATE_USER,
} as const
export type UserPermissionMap = PermissionComputedMap<typeof userPermissionRules>
