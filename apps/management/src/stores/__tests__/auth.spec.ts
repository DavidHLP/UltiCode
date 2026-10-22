import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { csrfManager } from '@ulticode/auth-core'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { apiGet } from '@/utils/request'

vi.mock('@/utils/request', () => ({ apiGet: vi.fn() }))

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
    vi.mocked(apiGet).mockResolvedValue(undefined)
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

  it.each(['login', 'initialize'])('loads the App profile avatar during %s', async (operation) => {
    document.cookie = 'csrf_token=session-csrf'
    const avatar = '/api/users/avatars/u-1/custom.webp'
    vi.mocked(apiGet).mockResolvedValue({ avatar })
    vi.mocked(authApi.login).mockResolvedValue({ user })
    vi.mocked(authApi.getCurrentUser).mockResolvedValue({ user })
    vi.mocked(authApi.getPermissions).mockResolvedValue([])
    const store = useAuthStore()

    if (operation === 'login') await store.login({ username: 'admin', password: 'secret' })
    else await store.initialize()

    expect(apiGet).toHaveBeenCalledWith('/users/me', { skipErrorHandler: true })
    expect(store.user?.avatar).toBe(avatar)
    expect(store.user?.id).toBe(user.id)
  })

  it('keeps authentication and the identity avatar when profile loading fails', async () => {
    vi.mocked(apiGet).mockRejectedValueOnce(new Error('App unavailable'))
    vi.mocked(authApi.login).mockResolvedValue({
      user: { ...user, avatar: '/existing.webp' },
    })
    vi.mocked(authApi.getPermissions).mockResolvedValue([])
    const store = useAuthStore()

    await expect(store.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)

    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.avatar).toBe('/existing.webp')
  })

  it('releases the login lock before a profile request can refresh authentication', async () => {
    const previous = Object.getOwnPropertyDescriptor(navigator, 'locks')
    let held = false
    const profileLockStates: boolean[] = []
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: {
        request: async (_name: string, action: () => Promise<unknown>) => {
          held = true
          try {
            return await action()
          } finally {
            held = false
          }
        },
      },
    })
    vi.mocked(apiGet).mockImplementation(async () => {
      profileLockStates.push(held)
      throw new Error('Profile authentication failed')
    })
    vi.mocked(authApi.login).mockResolvedValue({ user })
    vi.mocked(authApi.getPermissions).mockResolvedValue([])
    const store = useAuthStore()
    try {
      await expect(store.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)
      expect(profileLockStates).toEqual([false])
    } finally {
      if (previous) Object.defineProperty(navigator, 'locks', previous)
      else Reflect.deleteProperty(navigator, 'locks')
    }
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
