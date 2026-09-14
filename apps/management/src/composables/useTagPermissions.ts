import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface TagPermissionMap {
  read: ComputedRef<boolean>
  update: ComputedRef<boolean>
  manage: ComputedRef<boolean>
}

export function useTagPermissions(): { can: { tag: TagPermissionMap } } {
  const tag: TagPermissionMap = usePermissionMap({
    read: PERM.TAG_READ,
    update: PERM.TAG_UPDATE,
    // Preserve the existing policy: system managers and problem editors can
    // manage the shared tag catalog.
    manage: [PERM.SYSTEM_MANAGE_USERS, PERM.PROBLEM_UPDATE],
  })

  return { can: { tag } }
}
