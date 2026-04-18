import { defineStore } from "pinia";
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
      if (isDevelopment) {
      }
      return;
    }

    // Return existing promise if initialization is in progress
    if (_initializationPromise) {
      if (isDevelopment) {
      }
      return _initializationPromise;
    }

    // Set loading state
    status.value = "loading";
    error.value = null;

    if (isDevelopment) {
    }

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
        if (isDevelopment) {
        }
      } catch { // Backend unavailable or not authenticated - still mark as ready
        // App will function in guest mode
        if (isDevelopment) {
        }
      } finally {
        status.value = "ready";
        _initializationPromise = null;
        if (isDevelopment) {
        }
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
    // If we already have user data, return it
    if (user.value) {
      if (isDevelopment) {
      }
      return user.value;
    }

    // Otherwise, fetch from backend
    if (isDevelopment) {
    }

    try {
      return await fetchUser();
    } catch {
      // Connection error or 401 - user is not authenticated
      if (isDevelopment) {
        console.debug("[Auth] ensureUser() - fetch failed, user not authenticated");
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
      const response = await apiGet<{ user: User; csrfToken?: string }>("/auth/me", {
        skipErrorHandler: true,
      });

      if (isDevelopment) {
      }

      if (!response?.user) {
        console.error("[Auth] Invalid /auth/me response:", response);
        throw new Error("Invalid user response from /auth/me");
      }

      user.value = response.user;

      // Restore CSRF token from /auth/me response (survives page refresh)
      if (response.csrfToken) {
        csrfManager.refreshFromResponse(response);
      }

      if (isDevelopment) {
      }

      return response.user;
    } catch { if (isDevelopment) {} // 401 means no valid session - clear state
      user.value = null;
      return null;
    }
  }

  /**
   * Login with username and password
   * Stores CSRF token and updates user state
   */
  async function login(credentials: LoginRequest): Promise<void> {
    status.value = "loading";
    error.value = null;

    try {
      if (isDevelopment) {
      }

      // /auth/login returns { csrfToken: string, user: User }
      // request.ts unwraps the Result<T> envelope
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/login",
        credentials,
      );

      if (isDevelopment) {
      }

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

      if (isDevelopment) {
      }
    } catch (err) {
      status.value = "error";
      error.value = err instanceof Error ? err : new Error(String(err));
      throw err;
    }
  }

  /**
   * Register a new user account
   * Stores CSRF token and updates user state
   */
  async function register(data: RegisterRequest): Promise<void> {
    status.value = "loading";
    error.value = null;

    try {
      if (isDevelopment) {
      }

      // /auth/register returns { csrfToken: string, user: User }
      // request.ts unwraps the Result<T> envelope
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/register",
        data,
      );

      if (isDevelopment) {
      }

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

      if (isDevelopment) {
      }
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
      if (isDevelopment) {
      }

      await apiPost<void>("/auth/logout");

      if (isDevelopment) {
      }
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
    if (isDevelopment) {
    }
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
      if (isDevelopment) {
      }
    } catch { if (isDevelopment) {} permissions.value.clear();
    }
  }

  /**
   * Check if user has a specific permission
   * @param action - The action (e.g., "read", "write", "delete")
   * @param resource - The resource (e.g., "problem", "solution")
   * @returns true if user has permission
   */
  function hasPermission(action: string, resource: string): boolean {
    // Wildcard permission has access to everything
    if (permissions.value.has("*:*")) return true;
    if (permissions.value.has(`${action}:${resource}`)) return true;
    if (permissions.value.has(`${action}:*`)) return true;
    return false;
  }

  /**
   * Check if user has a specific role
   * @param role - The role to check
   * @returns true if user has the role
   */
  function hasRole(role: string): boolean {
    const userRoleValue = user.value?.role?.toUpperCase();
    const requiredRole = role.toUpperCase();
    return userRoleValue === requiredRole;
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
  };
});
