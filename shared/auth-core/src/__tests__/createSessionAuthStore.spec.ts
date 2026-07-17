import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createSessionAuthStore, type SessionAuthTransport } from '../createSessionAuthStore'

interface TestUser {
  id: string
  role: string
}

const testUser: TestUser = { id: 'u1', role: 'USER' }

function makeTransport(
  overrides: Partial<SessionAuthTransport<TestUser>> = {},
): SessionAuthTransport<TestUser> {
  return {
    fetchCurrentUser: vi.fn(async () => ({ user: testUser, csrfToken: 'c' })),
    login: vi.fn(async () => ({ user: testUser, csrfToken: 'c' })),
    register: vi.fn(async () => ({ user: testUser, csrfToken: 'c' })),
    logout: vi.fn(async () => {}),
    loadPermissions: vi.fn(async () => ['READ:USER']),
    hasSessionCookie: vi.fn(() => true),
    refreshCsrf: vi.fn(),
    clearCsrf: vi.fn(),
    ...overrides,
  }
}

describe('createSessionAuthStore', () => {
  let transport: SessionAuthTransport<TestUser>

  beforeEach(() => {
    transport = makeTransport()
  })

  it('login transitions idle -> ready and records the user + CSRF', async () => {
    const store = createSessionAuthStore(transport)
    expect(store.status.value).toBe('idle')
    await store.login({ username: 'x', password: 'y' })
    expect(store.status.value).toBe('ready')
    expect(store.user.value).toEqual(testUser)
    expect(store.error.value).toBeNull()
    expect(transport.refreshCsrf).toHaveBeenCalledWith({ csrfToken: 'c' })
  })

  it('login failure sets error status and rethrows', async () => {
    const err = new Error('bad creds')
    transport = makeTransport({ login: vi.fn(async () => { throw err }) })
    const store = createSessionAuthStore(transport)
    await expect(store.login({})).rejects.toThrow('bad creds')
    expect(store.status.value).toBe('error')
    expect(store.error.value).toBe(err)
    expect(store.user.value).toBeNull()
  })

  it('initialize skips fetch when no session cookie', async () => {
    transport = makeTransport({ hasSessionCookie: vi.fn(() => false) })
    const store = createSessionAuthStore(transport)
    await store.initialize()
    expect(transport.fetchCurrentUser).not.toHaveBeenCalled()
    expect(store.status.value).toBe('ready')
    expect(store.user.value).toBeNull()
  })

  it('initialize still reaches ready when /me fails', async () => {
    transport = makeTransport({
      fetchCurrentUser: vi.fn(async () => { throw new Error('network') }),
    })
    const store = createSessionAuthStore(transport)
    await store.initialize()
    expect(store.status.value).toBe('ready')
    expect(store.user.value).toBeNull()
  })

  it('deduplicates concurrent initialize calls', async () => {
    const store = createSessionAuthStore(transport)
    await Promise.all([store.initialize(), store.initialize()])
    expect(transport.fetchCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('reset clears state and re-enables initialize', async () => {
    const store = createSessionAuthStore(transport)
    await store.initialize()
    expect(store.status.value).toBe('ready')
    store.reset()
    expect(store.status.value).toBe('idle')
    expect(store.user.value).toBeNull()
    expect(transport.clearCsrf).toHaveBeenCalled()
    await store.initialize()
    expect(transport.fetchCurrentUser).toHaveBeenCalledTimes(2)
  })

  it('logout clears the user even when the API fails', async () => {
    transport = makeTransport({ logout: vi.fn(async () => { throw new Error('boom') }) })
    const store = createSessionAuthStore(transport)
    await store.login({})
    await store.logout()
    expect(store.user.value).toBeNull()
    expect(store.status.value).toBe('ready')
    expect(transport.clearCsrf).toHaveBeenCalled()
  })

  it('loadPermissions populates the permission set', async () => {
    const store = createSessionAuthStore(transport)
    await store.loadPermissions()
    expect(store.permissions.value.has('READ:USER')).toBe(true)
  })
})
