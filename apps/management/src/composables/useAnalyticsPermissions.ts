import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface AnalyticsPermissionMap {
  read: ComputedRef<boolean>
}

export function useAnalyticsPermissions(): { can: { analytics: AnalyticsPermissionMap } } {
  const analytics: AnalyticsPermissionMap = usePermissionMap({
    read: PERM.ANALYTICS_READ,
  })

  return { can: { analytics } }
}
