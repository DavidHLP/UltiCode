import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface SystemPermissionMap {
  read: ComputedRef<boolean>
  update: ComputedRef<boolean>
  manageUsers: ComputedRef<boolean>
}

export function useSystemPermissions(): { can: { system: SystemPermissionMap } } {
  const system: SystemPermissionMap = usePermissionMap({
    read: PERM.SYSTEM_READ,
    update: PERM.SYSTEM_UPDATE,
    manageUsers: PERM.SYSTEM_MANAGE_USERS,
  })

  return { can: { system } }
}
