import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface ContestPermissionMap {
  read: ComputedRef<boolean>
  create: ComputedRef<boolean>
  update: ComputedRef<boolean>
  delete: ComputedRef<boolean>
}

export function useContestPermissions(): { can: { contest: ContestPermissionMap } } {
  const contest: ContestPermissionMap = usePermissionMap({
    read: PERM.CONTEST_READ,
    create: PERM.CONTEST_CREATE,
    update: PERM.CONTEST_UPDATE,
    delete: PERM.CONTEST_DELETE,
  })

  return { can: { contest } }
}
