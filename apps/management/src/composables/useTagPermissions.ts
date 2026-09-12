import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface TagPermissionMap {
  read: ComputedRef<boolean>
  update: ComputedRef<boolean>
  manage: ComputedRef<boolean>
}

export function useTagPermissions(): { can: { tag: TagPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const tag: TagPermissionMap = {
    read: computed(() => has(PERM.TAG_READ)),
    update: computed(() => has(PERM.TAG_UPDATE)),
    // Preserve the existing policy: system managers and problem editors can
    // manage the shared tag catalog.
    manage: computed(() => has(PERM.SYSTEM_MANAGE_USERS) || has(PERM.PROBLEM_UPDATE)),
  }

  return { can: { tag } }
}
