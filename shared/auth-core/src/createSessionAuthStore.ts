import { ref, type Ref } from 'vue'

/**
 * Session lifecycle status for the status-machine auth flow.
 *
 * - `idle`    – no initialization attempted yet
 * - `loading` – an auth flow (initialize/login/register) is in progress
 * - `ready`   – bootstrap finished (authenticated or guest)
 * - `error`   – a login/register flow failed
 */
export type SessionStatus = 'idle' | 'loading' | 'ready' | 'error'

/**
 * Transport the session store calls to reach the backend. The consuming app
 * supplies these (wrapping its own `request` helper) so the session policy
 * stays framework/transport agnostic and the app keeps ownership of endpoint
 * URLs, error-handler flags, CSRF persistence, and the session-cookie sentinel.
 */
export interface SessionAuthTransport<U> {
  /** GET /auth/me (or equivalent) — resolves the current user + optional CSRF token. */
  fetchCurrentUser(): Promise<{ user: U; csrfToken?: string }>
  /** POST /auth/login — resolves the authenticated user directly (no re-fetch). */
  login(credentials: unknown): Promise<{ user: U; csrfToken?: string }>
  /** POST /auth/register — resolves the newly-registered user directly. */
  register(data: unknown): Promise<{ user: U; csrfToken?: string }>
  /** POST /auth/logout. */
  logout(): Promise<void>
  /** GET /auth/permissions — resolves the caller's permission strings. */
  loadPermissions(): Promise<string[]>
  /**
   * Whether a client-readable session sentinel (e.g. the non-httpOnly
   * `csrf_token` cookie) is present. Gates the `initialize` /auth/me call so
   * anonymous visitors never trigger a 401 on bootstrap.
   */
  hasSessionCookie(): boolean
  /** Persist the CSRF token carried by a login/register/me response. */
  refreshCsrf(response: { csrfToken?: string }): void
  /** Clear the persisted CSRF token (logout / clear / reset). */
  clearCsrf(): void
}

/** Reactive internals + actions returned by {@link createSessionAuthStore}. */
export interface SessionAuthStore<U> {
  user: Ref<U | null>
  status: Ref<SessionStatus>
  error: Ref<Error | null>
  permissions: Ref<Set<string>>
  initialize: () => Promise<void>
  whenInitialized: () => Promise<void> | null
  ensureUser: () => Promise<U | null>
  fetchUser: () => Promise<U | null>
  login: (credentials: unknown) => Promise<void>
  register: (data: unknown) => Promise<void>
  logout: () => Promise<void>
  clearUser: () => void
  reset: () => void
  loadPermissions: () => Promise<void>
}

/**
 * Status-machine auth session factory. Owns the reusable session policy for
 * apps that need a richer `idle/loading/ready/error` surface than the boolean
 * {@link createAuthStore} exposes: the concurrent-init dedup promise, the
 * CSRF-cookie-gated bootstrap, the login/register throw-and-record-error flow
 * (login trusts its own response and does not re-fetch /auth/me), and the
 * clear/reset teardown.
 *
 * <p>Previously this state machine lived as a bespoke 280-line composable
 * inside the console app. It now lives here so the policy is owned once and
 * each app is a thin transport adapter (architecture-review candidate #2).
 * CSRF persistence flows through the shared {@link csrfManager} singleton.
 *
 * @param transport per-app backend transport
 */
export function createSessionAuthStore<U>(transport: SessionAuthTransport<U>): SessionAuthStore<U> {
  const user = ref<U | null>(null) as Ref<U | null>
  const status = ref<SessionStatus>('idle')
  const error = ref<Error | null>(null)
  const permissions = ref<Set<string>>(new Set())

  // Private: prevents duplicate initialization. Never exposed as a raw ref.
  let initializationPromise: Promise<void> | null = null

  async function initialize(): Promise<void> {
    if (status.value === 'ready') {
      return
    }
    if (initializationPromise) {
      return initializationPromise
    }

    status.value = 'loading'
    error.value = null

    initializationPromise = (async () => {
      try {
        // fetchUser swallows transport/auth failures itself (returns null →
        // guest), so this catch is only a backstop around the cookie-presence
        // check. Either way we land in guest mode and mark ready.
        if (transport.hasSessionCookie()) {
          await fetchUser()
        }
      } catch {
        // Unexpected throw from the cookie check — fall through to guest mode.
      } finally {
        status.value = 'ready'
        initializationPromise = null
      }
    })()

    return initializationPromise
  }

  function whenInitialized(): Promise<void> | null {
    return initializationPromise
  }

  async function ensureUser(): Promise<U | null> {
    if (user.value) {
      return user.value
    }
    try {
      return await fetchUser()
    } catch {
      return null
    }
  }

  async function fetchUser(): Promise<U | null> {
    try {
      const response = await transport.fetchCurrentUser()
      if (!response?.user) {
        throw new Error('Invalid user response from fetchCurrentUser')
      }
      user.value = response.user
      if (response.csrfToken) {
        transport.refreshCsrf(response)
      }
      return response.user
    } catch {
      user.value = null
      return null
    }
  }

  /**
   * Shared login/register body: set loading, run the auth step, trust its
   * returned user (no /auth/me re-fetch), persist any CSRF token, then ready.
   * On failure record the error (and rethrow) so callers can react.
   */
  async function runAuthFlow(
    step: () => Promise<{ user: U; csrfToken?: string }>,
    invalidResponseLabel: string,
  ): Promise<void> {
    status.value = 'loading'
    error.value = null
    try {
      const { user: fetchedUser, csrfToken } = await step()
      if (!fetchedUser) {
        throw new Error(invalidResponseLabel)
      }
      if (csrfToken) {
        transport.refreshCsrf({ csrfToken })
      }
      user.value = fetchedUser
      status.value = 'ready'
    } catch (err) {
      status.value = 'error'
      error.value = err instanceof Error ? err : new Error(String(err))
      throw err
    }
  }

  async function login(credentials: unknown): Promise<void> {
    await runAuthFlow(() => transport.login(credentials), 'Invalid login response')
  }

  async function register(data: unknown): Promise<void> {
    await runAuthFlow(() => transport.register(data), 'Invalid register response')
  }

  async function logout(): Promise<void> {
    status.value = 'loading'
    try {
      await transport.logout()
    } catch (err) {
      console.error('[Auth] Logout error:', err)
    } finally {
      clearUser()
    }
  }

  function clearUser(): void {
    user.value = null
    permissions.value.clear()
    transport.clearCsrf()
    status.value = 'ready'
    error.value = null
  }

  function reset(): void {
    user.value = null
    status.value = 'idle'
    error.value = null
    initializationPromise = null
    transport.clearCsrf()
  }

  async function loadPermissions(): Promise<void> {
    try {
      const response = await transport.loadPermissions()
      permissions.value = new Set(response || [])
    } catch {
      permissions.value.clear()
    }
  }

  return {
    user,
    status,
    error,
    permissions,
    initialize,
    whenInitialized,
    ensureUser,
    fetchUser,
    login,
    register,
    logout,
    clearUser,
    reset,
    loadPermissions,
  }
}
