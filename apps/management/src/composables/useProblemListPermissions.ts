import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

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
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const problemList: ProblemListPermissionMap = {
    read: computed(() => has(PERM.PROBLEM_LIST_READ)),
    create: computed(() => has(PERM.PROBLEM_LIST_CREATE)),
    update: computed(() => has(PERM.PROBLEM_LIST_UPDATE)),
    delete: computed(() => has(PERM.PROBLEM_LIST_DELETE)),
    manageProblems: computed(() => has(PERM.PROBLEM_LIST_MANAGE_PROBLEMS)),
  }

  return { can: { problemList } }
}
