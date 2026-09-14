import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface ProblemListPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
  manageProblems: ComputedRef<boolean>
}

export function useProblemListPermissions(): {
  can: { problemList: ProblemListPermissionMap }
} {
  const problemList: ProblemListPermissionMap = usePermissionMap({
    read: PERM.PROBLEM_LIST_READ,
    create: PERM.PROBLEM_LIST_CREATE,
    update: PERM.PROBLEM_LIST_UPDATE,
    delete: PERM.PROBLEM_LIST_DELETE,
    manageProblems: PERM.PROBLEM_LIST_MANAGE_PROBLEMS,
  })

  return { can: { problemList } }
}
