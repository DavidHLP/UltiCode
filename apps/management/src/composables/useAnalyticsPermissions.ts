import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'

export interface AnalyticsPermissionMap {
  read: ComputedRef<boolean>
}

export function useAnalyticsPermissions(): { can: { analytics: AnalyticsPermissionMap } } {
  const authStore = useAuthStore()
  const analytics: AnalyticsPermissionMap = {
    read: computed(() =>
      authStore.hasPermission(PERM.ANALYTICS_READ.action, PERM.ANALYTICS_READ.resource),
    ),
  }

  return { can: { analytics } }
}
