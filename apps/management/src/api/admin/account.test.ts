import { beforeEach, describe, expect, it, vi } from 'vitest'
import { toast } from 'vue-sonner'
import { apiPatch, apiPost } from '@/utils/request'
import { accountApi, type AccountProfile } from './account'

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPatch: vi.fn(),
  apiPost: vi.fn(),
}))

vi.mock('vue-sonner', () => ({
  toast: {
    success: vi.fn(),
    warning: vi.fn(),
  },
    warning: vi.fn(),
}))

const profile: AccountProfile = {
  id: '1',
  username: 'admin',
  name: 'Administrator',
  email: 'admin@localhost.test',
  role: 'ADMIN',
  joined_at: '2026-06-12T00:00:00Z',
}

describe('accountApi success feedback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns the updated profile without showing a duplicate toast', async () => {
    vi.mocked(apiPatch).mockResolvedValue(profile)

    await expect(accountApi.updateProfile({ name: profile.name })).resolves.toEqual(profile)
    expect(toast.success).not.toHaveBeenCalled()
  })

  it('changes the password without showing a duplicate toast', async () => {
    vi.mocked(apiPost).mockResolvedValue(undefined)

    await expect(
      accountApi.changePassword({
        currentPassword: 'current-password',
        newPassword: 'new-password',
        confirmPassword: 'new-password',
      }),
    ).resolves.toBeUndefined()
    expect(toast.success).not.toHaveBeenCalled()
  })
})
