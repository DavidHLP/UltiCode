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
  // Track if user has explicitly logged out - used to skip /auth/me check in new tabs
  // Use localStorage for cross-tab persistence (sessionStorage is tab-isolated)
  const LOGOUT_KEY = "auth:explicitly_logged_out";

  function getHasExplicitlyLoggedOut(): boolean {
    try {
      return localStorage.getItem(LOGOUT_KEY) === "true";
    } catch {
      return false;
    }
  }

  function setHasExplicitlyLoggedOut(value: boolean): void {
    try {
      if (value) {
        localStorage.setItem(LOGOUT_KEY, "true");
      } else {
        localStorage.removeItem(LOGOUT_KEY);
      }
    } catch {
      // localStorage might be unavailable
    }
  }

  // Listen for logout events from other tabs
  function setupLogoutListener(): void {
    try {
      window.addEventListener("storage", (event: StorageEvent) => {
        if (event.key === LOGOUT_KEY && event.newValue === "true") {
          if (isDevelopment) {
            console.log("[Auth] Logout detected from another tab");
          }
          // Clear user state when logout is detected from another tab
          clearUser();
        }
      });
    } catch {
      // storage event might not be available
    }
  }

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

    // If user has explicitly logged out, skip /auth/me check
    // This prevents unnecessary 401 requests in new tabs after logout
    if (getHasExplicitlyLoggedOut()) {
      if (isDevelopment) {
        console.log(
          "[Auth] User explicitly logged out, skipping session check",
        );
      }
      status.value = "ready";
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

    // Setup cross-tab logout listener (only once)
    setupLogoutListener();

    if (isDevelopment) {
      console.log("[Auth] initialize() called - attempting to restore session");
    }

    // Create and store the promise
    _initializationPromise = (async () => {
      try {
        // Always attempt to restore session via /auth/me
        // The endpoint will return 401 if not authenticated
        // This properly validates the httpOnly cookie-based session
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
   *
   * Uses skipErrorHandler to prevent the response interceptor
   * from treating 401 as a global error (which would call clearUser)
   */
  async function fetchUser(): Promise<User | null> {
    // Prevent concurrent fetchUser calls
    if (_fetchUserPromise) {
      if (isDevelopment) {
        console.log("[Auth] fetchUser already in progress, waiting...");
      }
      return _fetchUserPromise;
    }

    _fetchUserPromise = (async () => {
      try {
        // /auth/me returns { user: User, csrfToken: string }
        // request.ts unwraps the Result<T> envelope
        const response = await apiGet<{ user: User; csrfToken: string }>(
          "/auth/me",
          { skipErrorHandler: true },
        );

        if (isDevelopment) {
          console.log("[Auth] /auth/me response:", response);
        }

        if (!response || !response.user) {
          console.error("[Auth] Invalid /auth/me response:", response);
          throw new Error("Invalid user response from /auth/me");
        }

        // Store CSRF token for subsequent state-changing requests
        if (response.csrfToken) {
          setCsrfToken(response.csrfToken);
        }

        user.value = response.user;

        if (isDevelopment) {
          console.log("[Auth] User data set:", user.value);
          console.log("[Auth] isAuthenticated:", isAuthenticated.value);
        }

        return response.user;
      } catch (err) {
        if (isDevelopment) {
          console.log("[Auth] fetchUser error (user not logged in):", err);
        }
        // 401 means no valid session - clear state
        user.value = null;
        return null;
      } finally {
        _fetchUserPromise = null;
      }
    })();

    return _fetchUserPromise;
  }

  // Store the promise to prevent concurrent fetchUser calls
  let _fetchUserPromise: Promise<User | null> | null = null;

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
      const response = await apiPost<LoginResponse>("/auth/login", credentials);

      if (isDevelopment) {
        console.log("[Auth] login response:", response);
      }

      if (!response || !response.user) {
        throw new Error("Invalid login response");
      }

      // Store CSRF token for subsequent state-changing requests
      if (response.csrfToken) {
        setCsrfToken(response.csrfToken);
      }

      // Update user state
      user.value = response.user;
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
      const response = await apiPost<LoginResponse>("/auth/register", data);

      if (isDevelopment) {
        console.log("[Auth] register response:", response);
      }

      if (!response || !response.user) {
        throw new Error("Invalid register response");
      }

      // Store CSRF token for subsequent state-changing requests
      if (response.csrfToken) {
        setCsrfToken(response.csrfToken);
      }

      // Update user state
      user.value = response.user;
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
      // Mark as explicitly logged out BEFORE clearing user
      // This ensures new tabs/windows know user has logged out
      setHasExplicitlyLoggedOut(true);
      clearUser();
    }
  }

  /**
   * Clear all authentication state
   * Called after 401/403 errors or manual logout
   *
   * NOTE: Does NOT set hasExplicitlyLoggedOut flag - that should only be set
   * by explicit logout action. 401 errors may be transient (session just expired)
   * and should not block session restoration on page refresh.
   */
  function clearUser(): void {
    if (isDevelopment) {
      console.log("[Auth] clearUser() called");
    }
    user.value = null;
    clearCsrfToken();
    error.value = null;
    // Note: Do NOT reset status to 'idle' here
    // Keeping status as 'ready' with user=null prevents re-initialization loops
    // The app remains in "ready but unauthenticated" state
    // Session restoration will be attempted on next initialize() call
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
    _fetchUserPromise = null;
    setHasExplicitlyLoggedOut(false);
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
