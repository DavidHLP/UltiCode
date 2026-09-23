import { reactive, type ComputedRef } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { PERM, type Permission } from '@/constants/permissions'
import {
  analyticsPermissionRules,
  commentPermissionRules,
  contestPermissionRules,
  forumPermissionRules,
  problemListPermissionRules,
  problemPermissionRules,
  solutionPermissionRules,
  systemPermissionRules,
  tagPermissionRules,
  userPermissionRules,
} from './domainPermissionMaps'
import { usePermissionMap } from './usePermissionMap'

vi.mock('@/stores/auth')

describe('domain permission maps', () => {
  it('preserves every domain can predicate and any-of rule behavior', () => {
    const granted = reactive(new Set<string>())
    const hasPermission = vi.fn((action: string, resource: string) =>
      granted.has(action + ':' + resource),
    )
    vi.mocked(useAuthStore).mockReturnValue({ hasPermission } as never)

    const can = {
      analytics: usePermissionMap(analyticsPermissionRules),
      comment: usePermissionMap(commentPermissionRules),
      contest: usePermissionMap(contestPermissionRules),
      forum: usePermissionMap(forumPermissionRules),
      problem: usePermissionMap(problemPermissionRules),
      problemList: usePermissionMap(problemListPermissionRules),
      solution: usePermissionMap(solutionPermissionRules),
      system: usePermissionMap(systemPermissionRules),
      tag: usePermissionMap(tagPermissionRules),
      user: usePermissionMap(userPermissionRules),
    }

    const predicates: Array<[ComputedRef<boolean>, Permission]> = [
      [can.analytics.read, PERM.ANALYTICS_READ],
      [can.comment.moderateForum, PERM.MODERATE_FORUM_COMMENT],
      [can.comment.moderateSolution, PERM.MODERATE_SOLUTION_COMMENT],
      [can.contest.read, PERM.CONTEST_READ],
      [can.contest.create, PERM.CONTEST_CREATE],
      [can.contest.update, PERM.CONTEST_UPDATE],
      [can.contest.delete, PERM.CONTEST_DELETE],
      [can.forum.moderatePost, PERM.MODERATE_FORUM_POST],
      [can.forum.deletePost, PERM.DELETE_FORUM_POST],
      [can.problem.read, PERM.PROBLEM_READ],
      [can.problem.create, PERM.PROBLEM_CREATE],
      [can.problem.update, PERM.PROBLEM_UPDATE],
      [can.problem.delete, PERM.PROBLEM_DELETE],
      [can.problem.moderate, PERM.MODERATE_PROBLEM],
      [can.problemList.read, PERM.PROBLEM_LIST_READ],
      [can.problemList.create, PERM.PROBLEM_LIST_CREATE],
      [can.problemList.update, PERM.PROBLEM_LIST_UPDATE],
      [can.problemList.delete, PERM.PROBLEM_LIST_DELETE],
      [can.problemList.manageProblems, PERM.PROBLEM_LIST_MANAGE_PROBLEMS],
      [can.solution.read, PERM.SOLUTION_READ],
      [can.solution.moderate, PERM.MODERATE_SOLUTION],
      [can.solution.delete, PERM.DELETE_SOLUTION],
      [can.system.read, PERM.SYSTEM_READ],
      [can.system.update, PERM.SYSTEM_UPDATE],
      [can.system.manageUsers, PERM.SYSTEM_MANAGE_USERS],
      [can.tag.read, PERM.TAG_READ],
      [can.tag.update, PERM.TAG_UPDATE],
      [can.tag.manage, PERM.SYSTEM_MANAGE_USERS],
      [can.tag.manage, PERM.PROBLEM_UPDATE],
      [can.user.read, PERM.USER_READ],
      [can.user.create, PERM.USER_CREATE],
      [can.user.update, PERM.USER_UPDATE],
      [can.user.delete, PERM.USER_DELETE],
      [can.user.moderate, PERM.MODERATE_USER],
    ]

    for (const [predicate, permission] of predicates) {
      granted.clear()
      expect(predicate.value).toBe(false)
      granted.add(permission.action + ':' + permission.resource)
      expect(predicate.value).toBe(true)
    }
  })
})
