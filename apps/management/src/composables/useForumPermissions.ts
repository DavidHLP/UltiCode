import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface ForumPermissionMap {
  moderatePost: ComputedRef<boolean>
  deletePost: ComputedRef<boolean>
}

export function useForumPermissions(): { can: { forum: ForumPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const forum: ForumPermissionMap = {
    moderatePost: computed(() => has(PERM.MODERATE_FORUM_POST)),
    deletePost: computed(() => has(PERM.DELETE_FORUM_POST)),
  }

  return { can: { forum } }
}
