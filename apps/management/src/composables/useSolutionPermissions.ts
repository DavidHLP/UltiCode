import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface SolutionPermissionMap {
  read: ComputedRef<boolean>
  moderate: ComputedRef<boolean>
  delete: ComputedRef<boolean>
}

export function useSolutionPermissions(): { can: { solution: SolutionPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const solution: SolutionPermissionMap = {
    read: computed(() => has(PERM.SOLUTION_READ)),
    moderate: computed(() => has(PERM.MODERATE_SOLUTION)),
    delete: computed(() => has(PERM.DELETE_SOLUTION)),
  }

  return { can: { solution } }
}
