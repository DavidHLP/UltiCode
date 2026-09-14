import { reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

vi.mock('@/stores/auth')

describe('usePermissionMap', () => {
  it('maps a permission and an any-of rule to reactive predicates', () => {
    const granted = reactive({ problem: true, system: false })
    const hasPermission = vi.fn((action: string, resource: string) =>
      action === PERM.PROBLEM_UPDATE.action && resource === PERM.PROBLEM_UPDATE.resource
        ? granted.problem
        : action === PERM.SYSTEM_MANAGE_USERS.action
            && resource === PERM.SYSTEM_MANAGE_USERS.resource
          ? granted.system
          : false,
    )
    vi.mocked(useAuthStore).mockReturnValue({ hasPermission } as never)

    const permissions = usePermissionMap({
      update: PERM.PROBLEM_UPDATE,
      manage: [PERM.SYSTEM_MANAGE_USERS, PERM.PROBLEM_UPDATE],
    })

    expect(permissions.update.value).toBe(true)
    expect(permissions.manage.value).toBe(true)
    granted.problem = false
    expect(permissions.update.value).toBe(false)
    expect(permissions.manage.value).toBe(false)
    granted.system = true
    expect(permissions.manage.value).toBe(true)
    expect(hasPermission).toHaveBeenCalledWith(
      PERM.PROBLEM_UPDATE.action,
      PERM.PROBLEM_UPDATE.resource,
    )
    expect(hasPermission).toHaveBeenCalledWith(
      PERM.SYSTEM_MANAGE_USERS.action,
      PERM.SYSTEM_MANAGE_USERS.resource,
    )
  })

  it('fails closed when the auth store denies every rule', () => {
    const hasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({ hasPermission } as never)

    const permissions = usePermissionMap({
      read: PERM.USER_READ,
      manage: [PERM.SYSTEM_MANAGE_USERS, PERM.PROBLEM_UPDATE],
    })

    expect(permissions.read.value).toBe(false)
    expect(permissions.manage.value).toBe(false)
  })
})
