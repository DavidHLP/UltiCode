import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface UserPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
  moderate: ComputedRef<boolean>
}

export function useUserPermissions(): { can: { user: UserPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const user: UserPermissionMap = {
    read: computed(() => has(PERM.USER_READ)),
    create: computed(() => has(PERM.USER_CREATE)),
    update: computed(() => has(PERM.USER_UPDATE)),
    delete: computed(() => has(PERM.USER_DELETE)),
    moderate: computed(() => has(PERM.MODERATE_USER)),
  }

  return { can: { user } }
}
