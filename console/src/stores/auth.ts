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
 * Authentication Store
 *
 * Manages user authentication state using httpOnly cookies.
 * No localStorage tokens - authentication is handled entirely by cookies.
 */
export const useAuthStore = defineStore("auth", () => {
  // State
  const user = ref<User | null>(null);
  const isLoading = ref(false);
  const isInitialized = ref(false);

  // Computed
  const isAuthenticated = computed(() => !!user.value);
  const userId = computed(() => user.value?.id || "");
  const userName = computed(
    () => user.value?.name || user.value?.username || "",
  );
  const userRole = computed(() => user.value?.role || "");

  /**
   * Initialize the auth store by fetching current user from /auth/me
   * This is called once on app startup via router guard
   */
  async function initialize(): Promise<void> {
    if (isInitialized.value) {
      if (isDevelopment) {
        console.log("[Auth] Already initialized, skipping");
      }
      return;
    }

    if (isDevelopment) {
      console.log("[Auth] initialize() called");
    }

    isInitialized.value = true;
    await fetchUser();
  }

  /**
   * Fetch current user from /auth/me endpoint
   * Returns null if not authenticated (401)
   */
  async function fetchUser(): Promise<User | null> {
    try {
      isLoading.value = true;

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
    } catch (error) {
      if (isDevelopment) {
        console.log("[Auth] fetchUser error (user not logged in):", error);
      }
      // 401 means no valid session - clear state
      user.value = null;
      return null;
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * Login with username and password
   * Stores CSRF token and updates user state
   */
  async function login(credentials: LoginRequest): Promise<void> {
    isLoading.value = true;

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

      // Update user state - this makes isAuthenticated work correctly
      user.value = response.user;
      isInitialized.value = true;

      if (isDevelopment) {
        console.log("[Auth] Login successful, user set:", user.value);
        console.log("[Auth] isAuthenticated:", isAuthenticated.value);
      }
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * Register a new user account
   * Stores CSRF token and updates user state
   */
  async function register(data: RegisterRequest): Promise<void> {
    isLoading.value = true;

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
      isInitialized.value = true;

      if (isDevelopment) {
        console.log("[Auth] Register successful, user set:", user.value);
        console.log("[Auth] isAuthenticated:", isAuthenticated.value);
      }
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * Logout current user
   * Calls backend to clear cookies and clears local state
   */
  async function logout(): Promise<void> {
    isLoading.value = true;

    try {
      if (isDevelopment) {
        console.log("[Auth] logout() called");
      }

      await apiPost<void>("/auth/logout");

      if (isDevelopment) {
        console.log("[Auth] Logout successful");
      }
    } catch (error) {
      console.error("[Auth] Logout error:", error);
    } finally {
      clearUser();
      isLoading.value = false;
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
    // Note: we keep isInitialized = true to prevent re-initialization
    // The user will need to login again to set a new user
  }

  return {
    // State
    user,
    isLoading,
    isInitialized,
    // Computed
    isAuthenticated,
    userId,
    userName,
    userRole,
    // Actions
    initialize,
    fetchUser,
    login,
    register,
    logout,
    clearUser,
  };
});
