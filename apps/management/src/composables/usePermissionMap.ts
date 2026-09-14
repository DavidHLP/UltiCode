import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { Permission } from '@/constants/permissions'

type PermissionRule = Permission | readonly Permission[]

export type PermissionComputedMap<T extends Record<string, PermissionRule>> = {
  [K in keyof T]: ComputedRef<boolean>
}

/** Build reactive permission predicates for one management domain. */
export function usePermissionMap<T extends Record<string, PermissionRule>>(
  rules: T,
): PermissionComputedMap<T> {
  const authStore = useAuthStore()

  return Object.fromEntries(
    Object.entries(rules).map(([key, rule]) => {
      const permissions = Array.isArray(rule) ? rule : [rule]
      return [
        key,
        computed(() =>
          permissions.some((permission) =>
            authStore.hasPermission(permission.action, permission.resource),
          ),
        ),
      ]
    }),
  ) as PermissionComputedMap<T>
}
