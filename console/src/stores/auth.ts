import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type {
  User,
  LoginRequest,
  RegisterRequest,
  LoginResponse,
} from "@/types/auth";
import { apiGet, apiPost } from "@/utils/request";
import { setCsrfToken, clearCsrfToken } from "@/utils/csrf";

const isDevelopment = import.meta.env.DEV;

/**
 * Check if auth cookies exist (httpOnly, so we check for presence only).
 * Returns true only if an access_token cookie is present.
 */
function hasAuthCookie(): boolean {
  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("access_token="));
}

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

  // Private: prevents duplicate initialization calls
  let _initializationPromise: Promise<void> | null = null;

  // Computed
  const isAuthenticated = computed(() => !!user.value);
  const isInitialized = computed(() => status.value === "ready");
  const isLoading = computed(() => status.value === "loading");
  const isInitializing = computed(() => status.value === "loading");
  const userId = computed(() => user.value?.id || "");
  const userName = computed(
    () => user.value?.name || user.value?.username || "",
  );
  const userRole = computed(() => user.value?.role || "");

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
        console.log("[Auth] Already initialized, skipping");
      }
      return;
    }

    // Return existing promise if initialization is in progress
    if (_initializationPromise) {
      if (isDevelopment) {
        console.log("[Auth] Initialization already in progress, waiting...");
      }
      return _initializationPromise;
    }

    // Set loading state
    status.value = "loading";
    error.value = null;

    if (isDevelopment) {
      console.log("[Auth] initialize() called - attempting to restore session");
    }

    // Create and store the promise
    _initializationPromise = (async () => {
      try {
        // Only attempt to restore session if auth cookies exist
        // Guests without cookies skip the /auth/me request entirely
        if (!hasAuthCookie()) {
          if (isDevelopment) {
            console.log(
              "[Auth] No auth cookies found, skipping session restore",
            );
          }
          return;
        }
        await fetchUser();
        if (isDevelopment) {
          console.log("[Auth] Session restored successfully");
        }
      } catch (err) {
        // Backend unavailable or not authenticated - still mark as ready
        // App will function in guest mode
        if (isDevelopment) {
          console.log("[Auth] Could not restore session:", err);
        }
      } finally {
        status.value = "ready";
        _initializationPromise = null;
        if (isDevelopment) {
          console.log("[Auth] Initialization complete, status: ready");
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
        console.log("[Auth] ensureUser() - user already loaded");
      }
      return user.value;
    }

    // Otherwise, fetch from backend
    if (isDevelopment) {
      console.log("[Auth] ensureUser() - fetching user from backend");
    }

    try {
      return await fetchUser();
    } catch {
      // Connection error or 401 - user is not authenticated
      if (isDevelopment) {
        console.log(
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
      // /auth/me returns User directly after interceptor unwraps Result<T> envelope
      const response = await apiGet<User>("/auth/me", {
        skipErrorHandler: true,
      });

      if (isDevelopment) {
        console.log("[Auth] /auth/me response:", response);
      }

      if (!response) {
        console.error("[Auth] Invalid /auth/me response:", response);
        throw new Error("Invalid user response from /auth/me");
      }

      user.value = response;

      if (isDevelopment) {
        console.log("[Auth] User data set:", user.value);
        console.log("[Auth] isAuthenticated:", isAuthenticated.value);
      }

      return response;
    } catch (err) {
      if (isDevelopment) {
        console.log("[Auth] fetchUser error (user not logged in):", err);
      }
      // 401 means no valid session - clear state
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
        console.log("[Auth] login() called with:", credentials.username);
      }

      // /auth/login returns { csrfToken: string, user: User }
      // request.ts unwraps the Result<T> envelope
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/login",
        credentials,
      );

      if (isDevelopment) {
        console.log("[Auth] login response:", { user: fetchedUser, csrfToken });
      }

      if (!fetchedUser) {
        throw new Error("Invalid login response");
      }

      // Store CSRF token for subsequent state-changing requests
      if (csrfToken) {
        setCsrfToken(csrfToken);
      }

      // Update user state
      user.value = fetchedUser;
      status.value = "ready";

      if (isDevelopment) {
        console.log("[Auth] Login successful, user set:", user.value);
        console.log("[Auth] isAuthenticated:", isAuthenticated.value);
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
        console.log("[Auth] register() called with:", data.username);
      }

      // /auth/register returns { csrfToken: string, user: User }
      // request.ts unwraps the Result<T> envelope
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/register",
        data,
      );

      if (isDevelopment) {
        console.log("[Auth] register response:", { user: fetchedUser, csrfToken });
      }

      if (!fetchedUser) {
        throw new Error("Invalid register response");
      }

      // Store CSRF token for subsequent state-changing requests
      if (csrfToken) {
        setCsrfToken(csrfToken);
      }

      // Update user state
      user.value = fetchedUser;
      status.value = "ready";

      if (isDevelopment) {
        console.log("[Auth] Register successful, user set:", user.value);
        console.log("[Auth] isAuthenticated:", isAuthenticated.value);
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
        console.log("[Auth] logout() called");
      }

      await apiPost<void>("/auth/logout");

      if (isDevelopment) {
        console.log("[Auth] Logout successful");
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
      console.log("[Auth] clearUser() called");
    }
    user.value = null;
    clearCsrfToken();
    // Reset to idle state - allows potential re-initialization
    status.value = "idle";
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
    clearCsrfToken();
  }

  return {
    // State
    user,
    status,
    error,
    // Computed
    isAuthenticated,
    isInitialized,
    isLoading,
    isInitializing,
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
  };
});
