import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { csrfManager } from '@ulticode/auth-core'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    getCurrentUser: vi.fn(),
    getPermissions: vi.fn(),
  },
}))

const user = {
  id: 'u-1',
  username: 'admin',
  name: 'Admin',
  email: 'admin@example.com',
  role: 'ADMIN',
  is_active: true,
  is_banned: false,
  joined_at: '2026-01-01T00:00:00Z',
}

describe('management auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    csrfManager.clearToken()
    document.cookie = 'csrf_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('trusts the login response and loads permissions without re-fetching /auth/me', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ user, csrfToken: 'login-csrf' })
    vi.mocked(authApi.getPermissions).mockResolvedValue(['READ:PROBLEM'])
    const store = useAuthStore()

    await expect(store.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)

    expect(authApi.login).toHaveBeenCalledOnce()
    expect(authApi.getCurrentUser).not.toHaveBeenCalled()
    expect(store.user).toEqual(user)
    expect(store.hasPermission('READ', 'PROBLEM')).toBe(true)
  })

  it('skips bootstrap /auth/me when the session sentinel is absent', async () => {
    const store = useAuthStore()

    await store.initialize()

    expect(authApi.getCurrentUser).not.toHaveBeenCalled()
    expect(store.isInitialized).toBe(true)
    expect(store.isAuthenticated).toBe(false)
  })

  it('restores a session when the exact session sentinel is present', async () => {
    document.cookie = 'csrf_token=session-csrf'
    vi.mocked(authApi.getCurrentUser).mockResolvedValue({ user, csrfToken: 'me-csrf' })
    vi.mocked(authApi.getPermissions).mockResolvedValue([])
    const store = useAuthStore()

    await store.initialize()

    expect(authApi.getCurrentUser).toHaveBeenCalledOnce()
    expect(store.user).toEqual(user)
    expect(store.isAuthenticated).toBe(true)
  })
})
