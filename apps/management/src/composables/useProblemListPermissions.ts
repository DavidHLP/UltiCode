import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export function useProblemListPermissions() {
  const authStore = useAuthStore()

  function canEditBasicInfo(): boolean {
    return authStore.hasPermission(
      PERM.PROBLEM_LIST_UPDATE.action,
      PERM.PROBLEM_LIST_UPDATE.resource,
    )
  }

  function canEditVisibility(): boolean {
    return authStore.hasPermission(
      PERM.PROBLEM_LIST_UPDATE.action,
      PERM.PROBLEM_LIST_UPDATE.resource,
    )
  }

  function canEditBanner(): boolean {
    return authStore.hasPermission(
      PERM.PROBLEM_LIST_UPDATE.action,
      PERM.PROBLEM_LIST_UPDATE.resource,
    )
  }

  function canManageProblems(): boolean {
    return authStore.hasPermission(
      PERM.PROBLEM_LIST_MANAGE_PROBLEMS.action,
      PERM.PROBLEM_LIST_MANAGE_PROBLEMS.resource,
    )
  }

  return {
    canEditBasicInfo: computed(() => canEditBasicInfo()),
    canEditVisibility: computed(() => canEditVisibility()),
    canEditBanner: computed(() => canEditBanner()),
    canManageProblems: computed(() => canManageProblems()),
  }
}
