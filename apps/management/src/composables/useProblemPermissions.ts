import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface ProblemPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
  moderate: ComputedRef<boolean>
}

export function useProblemPermissions(): { can: { problem: ProblemPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const problem: ProblemPermissionMap = {
    read: computed(() => has(PERM.PROBLEM_READ)),
    create: computed(() => has(PERM.PROBLEM_CREATE)),
    update: computed(() => has(PERM.PROBLEM_UPDATE)),
    delete: computed(() => has(PERM.PROBLEM_DELETE)),
    moderate: computed(() => has(PERM.MODERATE_PROBLEM)),
  }

  return { can: { problem } }
}
