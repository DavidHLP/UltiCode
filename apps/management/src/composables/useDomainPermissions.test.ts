import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useContestPermissions } from './useContestPermissions'
import { useForumPermissions } from './useForumPermissions'

vi.mock('@/stores/auth')

describe('domain permission maps', () => {
  it('maps contest update to UPDATE:CONTEST', () => {
    const hasPermission = vi.fn((action: string, resource: string) =>
      action === 'UPDATE' && resource === 'CONTEST',
    )
    vi.mocked(useAuthStore).mockReturnValue({ hasPermission } as never)

    const { can } = useContestPermissions()

    expect(can.contest.update.value).toBe(true)
    expect(hasPermission).toHaveBeenCalledWith('UPDATE', 'CONTEST')
  })

  it('maps forum post moderation and fails closed', () => {
    const hasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({ hasPermission } as never)

    const { can } = useForumPermissions()

    expect(can.forum.moderatePost.value).toBe(false)
    expect(can.forum.deletePost.value).toBe(false)
    expect(hasPermission).toHaveBeenCalledWith('MODERATE', 'FORUM_POST')
  })
})
