import { describe, expect, it, vi } from 'vitest'
import { apiUpload } from '@/utils/request'
import { canWriteUserPermissions, usersApi } from './users'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  apiUpload: vi.fn(),
}))

describe('user detail degradation guard', () => {
  it('allows permission writes only for a proven OK section', () => {
    expect(canWriteUserPermissions({ permissionsStatus: 'OK' })).toBe(true)
    expect(canWriteUserPermissions({ permissionsStatus: 'PARTIAL' })).toBe(false)
    expect(canWriteUserPermissions({ permissionsStatus: 'UNAVAILABLE' })).toBe(false)
    expect(canWriteUserPermissions(null)).toBe(false)
    expect(canWriteUserPermissions(undefined)).toBe(false)
  })
})

describe('user avatar upload API', () => {
  it('uses the admin avatar endpoint and multipart helper', async () => {
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    vi.mocked(apiUpload).mockResolvedValue('/api/users/avatars/user-7/avatar.webp')

    await expect(usersApi.uploadAvatar('user-7', file)).resolves.toBe(
      '/api/users/avatars/user-7/avatar.webp',
    )
    expect(apiUpload).toHaveBeenCalledWith('/admin/users/user-7/avatar', file, undefined)
  })
})
