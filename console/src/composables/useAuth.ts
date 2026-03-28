import { computed } from "vue";
import { useAuthStore } from "@/stores/auth";
import { useRouter } from "vue-router";
import type { User, LoginRequest, RegisterRequest } from "@/types/auth";

/**
 * Unified Authentication Composable
 *
 * This is the RECOMMENDED way to access authentication state and methods
 * throughout the application. It provides:
 *
 * 1. Reactive access to auth state (user, isAuthenticated, etc.)
 * 2. Convenient methods for common auth operations
 * 3. Type-safe access to user properties
 *
 * Replaces the deprecated utils/auth.ts exports.
 *
 * @example
 * ```ts
 * // In a component
 * const { user, isAuthenticated, login, logout, requireAuth } = useAuth();
 *
 * // Check if user can perform action
 * if (!isAuthenticated.value) {
 *   await router.push({ name: 'login' });
 * }
 *
 * // Require auth for action (redirects if not authenticated)
 * if (!requireAuth()) return;
 * ```
 */
export function useAuth() {
  const authStore = useAuthStore();
  const router = useRouter();

  // Reactive state
  const user = computed(() => authStore.user);
  const isAuthenticated = computed(() => authStore.isAuthenticated);
  const isLoading = computed(() => authStore.isLoading);
  const status = computed(() => authStore.status);
  const error = computed(() => authStore.error);

  // User properties (convenience accessors)
  const userId = computed(() => authStore.userId);
  const userName = computed(() => authStore.userName);
  const userRole = computed(() => authStore.userRole);

  /**
   * Require authentication for an action
   *
   * If user is not authenticated, redirects to login page.
   * Returns true if authenticated, false otherwise.
   *
   * @example
   * ```ts
   * const { requireAuth } = useAuth();
   *
   * function someProtectedAction() {
   *   if (!requireAuth()) return; // Redirects to login if not authenticated
   *   // ... perform action
   * }
   * ```
   */
  function requireAuth(): boolean {
    if (!isAuthenticated.value) {
      router.push({
        name: "login",
        query: { redirect: router.currentRoute.value.fullPath },
      });
      return false;
    }
    return true;
  }

  /**
   * Require specific role for an action
   *
   * @param roles - Array of allowed roles
   * @returns true if user has required role, false otherwise
   */
  function requireRole(...roles: string[]): boolean {
    if (!requireAuth()) return false;
    return roles.includes(userRole.value);
  }

  /**
   * Login with credentials
   */
  async function login(credentials: LoginRequest): Promise<void> {
    return authStore.login(credentials);
  }

  /**
   * Register new account
   */
  async function register(data: RegisterRequest): Promise<void> {
    return authStore.register(data);
  }

  /**
   * Logout current user
   */
  async function logout(): Promise<void> {
    return authStore.logout();
  }

  /**
   * Fetch current user data
   */
  async function fetchUser(): Promise<User | null> {
    return authStore.fetchUser();
  }

  /**
   * Clear auth state (for testing or forced logout)
   */
  function clearUser(): void {
    authStore.clearUser();
  }

  return {
    // State
    user,
    isAuthenticated,
    isLoading,
    status,
    error,
    // User properties
    userId,
    userName,
    userRole,
    // Methods
    login,
    register,
    logout,
    fetchUser,
    clearUser,
    // Guards
    requireAuth,
    requireRole,
  };
}

/**
 * Type guard to check if user has a specific property
 *
 * @example
 * ```ts
 * const { user } = useAuth();
 *
 * if (hasUserProperty(user.value, 'email')) {
 *   console.log(user.value.email); // TypeScript knows email exists
 * }
 * ```
 */
export function hasUserProperty<K extends keyof User>(
  user: User | null,
  prop: K,
): user is User & { [P in K]: NonNullable<User[P]> } {
  return user != null && prop in user && user[prop] != null;
}
