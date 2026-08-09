import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  authStore: {
    isAuthenticated: true,
    clearUser: vi.fn(),
  },
  router: {
    currentRoute: {
      value: {
        name: 'problem-detail',
        fullPath: '/problems/p-1?tab=cases',
      },
    },
    push: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('@/router', () => ({
  default: mocks.router,
}))

import { runSessionExpired } from './runSessionExpired'

describe('runSessionExpired', () => {
  beforeEach(() => {
    mocks.authStore.isAuthenticated = true
    mocks.authStore.clearUser.mockClear()
    mocks.router.currentRoute.value = {
      name: 'problem-detail',
      fullPath: '/problems/p-1?tab=cases',
    }
    mocks.router.push.mockClear()
  })

  it('redirects to login with the current route', () => {
    runSessionExpired()

    expect(mocks.authStore.clearUser).toHaveBeenCalledOnce()
    expect(mocks.router.push).toHaveBeenCalledWith({
      name: 'login',
      query: { redirect: '/problems/p-1?tab=cases' },
    })
  })

  it('does not loop when the current route is already login', () => {
    mocks.router.currentRoute.value = {
      name: 'login',
      fullPath: '/login',
    }

    runSessionExpired()

    expect(mocks.authStore.clearUser).toHaveBeenCalledOnce()
    expect(mocks.router.push).not.toHaveBeenCalled()
  })
})
