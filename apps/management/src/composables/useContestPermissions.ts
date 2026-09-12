import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface ContestPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
}

export function useContestPermissions(): { can: { contest: ContestPermissionMap } } {
  const authStore = useAuthStore()
  const has = (permission: (typeof PERM)[keyof typeof PERM]) =>
    authStore.hasPermission(permission.action, permission.resource)

  const contest: ContestPermissionMap = {
    read: computed(() => has(PERM.CONTEST_READ)),
    create: computed(() => has(PERM.CONTEST_CREATE)),
    update: computed(() => has(PERM.CONTEST_UPDATE)),
    delete: computed(() => has(PERM.CONTEST_DELETE)),
  }

  return { can: { contest } }
}
