import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface SystemPermissionMap {
  read: ComputedRef<boolean>
  update: ComputedRef<boolean>
  manageUsers: ComputedRef<boolean>
}

export function useSystemPermissions(): { can: { system: SystemPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const system: SystemPermissionMap = {
    read: computed(() => has(PERM.SYSTEM_READ)),
    update: computed(() => has(PERM.SYSTEM_UPDATE)),
    manageUsers: computed(() => has(PERM.SYSTEM_MANAGE_USERS)),
  }

  return { can: { system } }
}
