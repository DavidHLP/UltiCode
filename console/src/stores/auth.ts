import { defineStore } from "pinia";
import {
  checkPermission,
  checkRole,
  checkAnyRole,
} from "@/shared/auth-core/src";
import { computed } from "vue";
import type { User } from "@/types/auth";
import { useAuthSession } from "@/composables/useAuthSession";

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
 * State container for the authenticated user. All side-effecting flows
 * (fetch / login / register / logout / ensureUser / loadPermissions /
 * initialize) live in `useAuthSession`. The store keeps only reactive
 * state, computed selectors, and thin action wrappers so consumers see
 * the exact same surface as before.
 */
export const useAuthStore = defineStore("auth", () => {
  const session = useAuthSession();

  // Computed selectors derived from session state
  const isAuthenticated = computed(() => !!session.user.value);
  const isInitialized = computed(() => session.status.value === "ready");
  const isLoading = computed(() => session.status.value === "loading");
  const userId = computed(() => session.user.value?.id || "");
  const userName = computed(
    () => session.user.value?.name || session.user.value?.username || "",
  );
  const userRole = computed(() => session.user.value?.role || "");

  /**
   * Awaitable bootstrap barrier.
   *
   * The router guard (`buildConsoleAuthAdapter` in
   * `router/index.ts`) checks this on every navigation, so the public
   * name is preserved exactly. The dedup implementation is internal to
   * `useAuthSession` and never leaves that module as a raw ref.
   */
  const initializationPromise = computed(() =>
    session.getInitializationPromise(),
  );

  // Action wrappers — each forwards to the session composable. Kept as
  // named functions on the store so call sites
  // (`authStore.login(...)`, `authStore.initialize()`) and tests
  // (`store.login(...)`, `store.initialize()`) continue to work.

  function initialize(): Promise<void> {
    return session.initialize();
  }

  function ensureUser(): Promise<User | null> {
    return session.ensureUser();
  }

  function fetchUser(): Promise<User | null> {
    return session.fetchUser();
  }

  function login(credentials: import("@/types/auth").LoginRequest): Promise<void> {
    return session.login(credentials);
  }

  function register(
    data: import("@/types/auth").RegisterRequest,
  ): Promise<void> {
    return session.register(data);
  }

  function logout(): Promise<void> {
    return session.logout();
  }

  function clearUser(): void {
    return session.clearUser();
  }

  function reset(): void {
    return session.reset();
  }

  function fetchCurrentUserId(): string | null {
    return session.user.value?.id || null;
  }

  function loadPermissions(): Promise<void> {
    return session.loadPermissions();
  }

  function hasPermission(action: string, resource: string): boolean {
    return checkPermission(session.permissions.value, action, resource);
  }

  function hasRole(role: string): boolean {
    return checkRole(session.user.value?.role, role);
  }

  function hasAnyRole(roles: string[]): boolean {
    return checkAnyRole(session.user.value?.role, roles);
  }

  return {
    // State
    user: session.user,
    status: session.status,
    error: session.error,
    permissions: session.permissions,
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
