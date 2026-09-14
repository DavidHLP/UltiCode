import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface ProblemPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
  moderate: ComputedRef<boolean>
}

export function useProblemPermissions(): { can: { problem: ProblemPermissionMap } } {
  const problem: ProblemPermissionMap = usePermissionMap({
    read: PERM.PROBLEM_READ,
    create: PERM.PROBLEM_CREATE,
    update: PERM.PROBLEM_UPDATE,
    delete: PERM.PROBLEM_DELETE,
    moderate: PERM.MODERATE_PROBLEM,
  })

  return { can: { problem } }
}
