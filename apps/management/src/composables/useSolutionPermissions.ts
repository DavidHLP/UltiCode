import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface SolutionPermissionMap {
  read: ComputedRef<boolean>
  moderate: ComputedRef<boolean>
  delete: ComputedRef<boolean>
}

export function useSolutionPermissions(): { can: { solution: SolutionPermissionMap } } {
  const solution: SolutionPermissionMap = usePermissionMap({
    read: PERM.SOLUTION_READ,
    moderate: PERM.MODERATE_SOLUTION,
    delete: PERM.DELETE_SOLUTION,
  })

  return { can: { solution } }
}
