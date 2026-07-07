import { defineStore } from "pinia";
import { checkPermission, checkRole, checkAnyRole } from "@/shared/auth-core/src";
import { ref, computed } from "vue";
import type {
  User,
  LoginRequest,
  RegisterRequest,
  LoginResponse,
} from "@/types/auth";
import { apiGet, apiPost } from "@/utils/request";
import { csrfManager, getCsrfToken } from "@/utils/csrf";

const isDevelopment = import.meta.env.DEV;

/**
 * Authentication status states
 *
 * State machine transitions:
 * idle → loading (initialize starts)
 * loading → ready (initialize succeeds)
 * loading → error (initialize fails)
 * ready → idle (logout, resets for potential re-initialization)
 */
export type AuthStatus = "idle" | "loading" | "ready" | "error";

/**
 * Authentication Store
 *
 * Manages user authentication state using httpOnly cookies.
 * No localStorage tokens - authentication is handled entirely by cookies.
 *
 * Design changes:
 * - Uses explicit `status` state machine instead of boolean `isInitialized`
 * - `initialize()` is called once during app bootstrap, not in router guards
 * - Router guards can now make synchronous decisions based on `status === 'ready'`
 */
export const useAuthStore = defineStore("auth", () => {
  // State
  const user = ref<User | null>(null);
  const status = ref<AuthStatus>("idle");
  const error = ref<Error | null>(null);
  const permissions = ref<Set<string>>(new Set());

  // Private: prevents duplicate initialization calls
  let _initializationPromise: Promise<void> | null = null;

  // Computed
  const isAuthenticated = computed(() => !!user.value);
  const isInitialized = computed(() => status.value === "ready");
  const isLoading = computed(() => status.value === "loading");
  const userId = computed(() => user.value?.id || "");
  const userName = computed(
    () => user.value?.name || user.value?.username || "",
  );
  const userRole = computed(() => user.value?.role || "");

  /**
   * Expose the initialization promise for router guards.
   * Allows router to wait for auth initialization before making navigation decisions.
   */
  const initializationPromise = computed(() => _initializationPromise);

  /**
   * Initialize the auth store.
   *
   * IMPORTANT: This should be called ONCE during app bootstrap (main.ts).
   * It attempts to fetch user from /auth/me to restore session from httpOnly cookies.
   *
   * If the backend is unavailable or user is not authenticated, the app
   * will still load successfully in guest mode.
   *
   * Returns the initialization promise to prevent race conditions.
   * Multiple concurrent calls will wait for the same initialization.
   */
  async function initialize(): Promise<void> {
    // If already ready, skip
    if (status.value === "ready") {
      return;
    }

    // Return existing promise if initialization is in progress
    if (_initializationPromise) {
      return _initializationPromise;
    }

    // Set loading state
    status.value = "loading";
    error.value = null;

    // Create and store the promise
    _initializationPromise = (async () => {
      try {
        // Only call /auth/me if a CSRF token exists (set by backend at login).
        // No CSRF token = no session to restore, skip the unnecessary 401.
        // Note: httpOnly cookies (access_token) can't be read from JS,
        // but csrf_token is a non-httpOnly cookie that serves as a proxy signal.
        const hasCsrf = !!getCsrfToken();
        if (hasCsrf) {
          await fetchUser();
        }
      } catch {
        // Backend unavailable or not authenticated - still mark as ready
        // App will function in guest mode
      } finally {
        status.value = "ready";
        _initializationPromise = null;
      }
    })();

    return _initializationPromise;
  }

  /**
   * Ensure user information is loaded.
   * Fetches from /auth/me only if user is not already loaded.
   * This is called by router guards when authentication is required.
   *
   * Returns the user if authenticated, null otherwise.
   */
  async function ensureUser(): Promise<User | null> {
    // If we already have user data, return user.value
    if (user.value) {
      return user.value;
    }

    // Otherwise, fetch from backend
    try {
      return await fetchUser();
    } catch {
      // Connection error or 401 - user is not authenticated
      if (isDevelopment) {
        console.debug(
          "[Auth] ensureUser() - fetch failed, user not authenticated",
        );
      }
      return null;
    }
  }

  /**
   * Fetch current user from /auth/me endpoint
   * Returns null if not authenticated (401)
   */
  async function fetchUser(): Promise<User | null> {
    try {
      // /auth/me returns { user: User, csrfToken: string } after interceptor unwraps Result<T>
      const response = await apiGet<{ user: User; csrfToken?: string }>(
        "/auth/me",
        {
          skipErrorHandler: true,
        },
      );

      if (!response?.user) {
        console.error("[Auth] Invalid /auth/me response:", response);
        throw new Error("Invalid user response from /auth/me");
      }

      user.value = response.user;

      // Restore CSRF token from /auth/me response (survives page refresh)
      if (response.csrfToken) {
        csrfManager.refreshFromResponse(response);
      }

      return response.user;
    } catch {
      // 401 means no valid session - clear state
      user.value = null;
      return null;
    }
  }

  /**
   * Login with username and password.
   *
   * On a cold (anonymous) session, the server's CsrfValidationFilter bypasses
   * CSRF for unauthenticated POSTs — so this call works without an
   * X-CSRF-Token header. The response sets three HttpOnly cookies
   * (access_token, refresh_token, csrf_token) and the user/csrfToken in body.
   *
   * Once a session is established, any subsequent POST — including to
   * /auth/login itself — requires the X-CSRF-Token header. Re-submitting the
   * login form while still logged in will return 403 "CSRF token is required";
   * the UI must call `logout()` first or refresh the page to clear cookies.
   *
   * Persists the returned csrfToken via `csrfManager.refreshFromResponse`
   * so subsequent state-changing requests can attach it.
   */
  async function login(credentials: LoginRequest): Promise<void> {
    status.value = "loading";
    error.value = null;

    try {
      // /auth/login returns { csrfToken: string, user: User }
      // request.ts unwraps the Result<T> envelope
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/login",
        credentials,
      );

      if (!fetchedUser) {
        throw new Error("Invalid login response");
      }

      // Store CSRF token for subsequent state-changing requests
      if (csrfToken) {
        csrfManager.refreshFromResponse({ csrfToken });
      }

      // Update user state
      user.value = fetchedUser;
      status.value = "ready";
    } catch (err) {
      status.value = "error";
      error.value = err instanceof Error ? err : new Error(String(err));
      throw err;
    }
  }

  /**
   * Register a new user account.
   *
   * Register follows the same CSRF rule as login: it is only callable as a
   * fresh (anonymous) visitor. If the visitor already holds a csrf_token
   * cookie, the request will be rejected with 403 — the UI must clear the
   * session first (logout or hard refresh) before re-registering.
   *
   * On success the response also logs the user in: it sets the same three
   * HttpOnly cookies as login() and returns the user profile.
   */
  async function register(data: RegisterRequest): Promise<void> {
    status.value = "loading";
    error.value = null;

    try {
      // /auth/register returns { csrfToken: string, user: User }
      // request.ts unwraps the Result<T> envelope
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/register",
        data,
      );

      if (!fetchedUser) {
        throw new Error("Invalid register response");
      }

      // Store CSRF token for subsequent state-changing requests
      if (csrfToken) {
        csrfManager.refreshFromResponse({ csrfToken });
      }

      // Update user state
      user.value = fetchedUser;
      status.value = "ready";
    } catch (err) {
      status.value = "error";
      error.value = err instanceof Error ? err : new Error(String(err));
      throw err;
    }
  }

  /**
   * Logout current user
   * Calls backend to clear cookies and clears local state
   */
  async function logout(): Promise<void> {
    status.value = "loading";

    try {
      await apiPost<void>("/auth/logout");
    } catch (err) {
      console.error("[Auth] Logout error:", err);
    } finally {
      clearUser();
    }
  }

  /**
   * Clear all authentication state
   * Called after 401/403 errors or manual logout
   */
  function clearUser(): void {
    user.value = null;
    permissions.value.clear();
    csrfManager.clearToken();
    // Reset to ready state - app continues in guest mode
    status.value = "ready";
    error.value = null;
  }

  /**
   * Reset the store to initial state
   * Useful for testing or forced re-initialization
   */
  function reset(): void {
    user.value = null;
    status.value = "idle";
    error.value = null;
    _initializationPromise = null;
    csrfManager.clearToken();
  }

  /**
   * Get current user ID (helper for API calls)
   * @returns user ID or null if not authenticated
   */
  function fetchCurrentUserId(): string | null {
    return user.value?.id || null;
  }

  /**
   * Load permissions for the current user
   * Permissions are returned as strings in "action:resource" format
   * e.g., "read:problem", "write:solution", "*:*" for admin
   */
  async function loadPermissions(): Promise<void> {
    try {
      const response = await apiGet<string[]>("/auth/permissions", {
        skipErrorHandler: true,
      });
      permissions.value = new Set(response || []);
    } catch {
      permissions.value.clear();
    }
  }

  /**
   * Check if user has a specific permission
   * @param action - The action (e.g., "read", "write", "delete")
   * @param resource - The resource (e.g., "problem", "solution")
   * @returns true if user has permission
   */
  function hasPermission(action: string, resource: string): boolean {
    return checkPermission(permissions.value, action, resource);
  }

  /**
   * Check if user has a specific role
   * @param role - The role to check
   * @returns true if the user has the role
   */
  function hasRole(role: string): boolean {
    return checkRole(user.value?.role, role);
  }

  /**
   * Check if user has any of the provided roles.
   * 与 management 端的 hasAnyRole 对齐 (架构评审 Candidate 3 次要发现)。
   * @param roles - Roles to test; comparison is case-insensitive
   * @returns true if user holds at least one of the roles
   */
  function hasAnyRole(roles: string[]): boolean {
    return checkAnyRole(user.value?.role, roles);
  }

  return {
    // State
    user,
    status,
    error,
    permissions,
    // Computed
    isAuthenticated,
    isInitialized,
    isLoading,
    initializationPromise,
    userId,
    userName,
    userRole,
    // Actions
    initialize,
    ensureUser,
    fetchUser,
    login,
    register,
    logout,
    clearUser,
    reset,
    fetchCurrentUserId,
    loadPermissions,
    hasPermission,
    hasRole,
    hasAnyRole,
  };
});
