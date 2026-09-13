import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface CommentPermissionMap {
  moderateForum: ComputedRef<boolean>
  moderateSolution: ComputedRef<boolean>
}

export function useCommentPermissions(): { can: { comment: CommentPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const comment: CommentPermissionMap = {
    moderateForum: computed(() => has(PERM.MODERATE_FORUM_COMMENT)),
    moderateSolution: computed(() => has(PERM.MODERATE_SOLUTION_COMMENT)),
  }

  return { can: { comment } }
}
