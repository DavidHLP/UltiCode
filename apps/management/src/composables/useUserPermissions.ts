import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface UserPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
  moderate: ComputedRef<boolean>
}

export function useUserPermissions(): { can: { user: UserPermissionMap } } {
  const user: UserPermissionMap = usePermissionMap({
    read: PERM.USER_READ,
    create: PERM.USER_CREATE,
    update: PERM.USER_UPDATE,
    delete: PERM.USER_DELETE,
    moderate: PERM.MODERATE_USER,
  })

  return { can: { user } }
}
