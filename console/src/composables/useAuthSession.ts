import { ref } from "vue";
import type { LoginRequest, RegisterRequest, User } from "@/types/auth";
import type { LoginResponse } from "@/shared/auth-core/src/types";
import { apiGet, apiPost } from "@/utils/request";
import { csrfManager } from "@/shared/auth-core/src";

const isDevelopment = import.meta.env.DEV;

/**
 * Detect whether the browser has a `csrf_token` cookie set.
 *
 * The backend writes this cookie as a non-httpOnly sentinel on every
 * successful login / register / refresh. Reading it directly from
 * `document.cookie` lets us detect "there is a session to restore" even
 * after a hard page refresh, when the in-memory `csrfManager` token has
 * been wiped. The `access_token` cookie is httpOnly and inaccessible to
 * JS, so this sentinel cookie is the only client-readable signal of an
 * existing session.
 *
 * Returns false in non-browser environments (SSR, tests without jsdom).
 */
function hasCsrfCookie(): boolean {
  if (typeof document === "undefined") return false;
  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("csrf_token="));
}

/**
 * Authentication session composable.
 *
 * Owns the side-effecting auth flows (fetch / login / register / logout /
 * ensureUser / loadPermissions / initialize) and the concurrent-init
 * dedup promise. The dedup is **internal** — it is never exposed on the
 * composable's return shape. Callers that need to await the bootstrap
 * barrier use `initializationPromise` exposed by `useAuthStore`,
 * which is a thin computed wrapper over the same internal promise.
 */
export function useAuthSession() {
  // Reactive state — the store reads these refs through its selector
  // surface and may attach its own computed/aliases on top.
  const user = ref<User | null>(null);
  const status = ref<"idle" | "loading" | "ready" | "error">("idle");
  const error = ref<Error | null>(null);
  const permissions = ref<Set<string>>(new Set());

  // Private: prevents duplicate initialization calls. Not exposed.
  let initializationPromise: Promise<void> | null = null;

  /**
   * Initialize the auth session.
   *
   * Concurrent callers receive the same in-flight promise so /auth/me
   * fires exactly once even when bootstrap and the router guard race.
   */
  async function initialize(): Promise<void> {
    if (status.value === "ready") {
      return;
    }

    if (initializationPromise) {
      return initializationPromise;
    }

    status.value = "loading";
    error.value = null;

    initializationPromise = (async () => {
      try {
        const hasCsrf = hasCsrfCookie();
        if (hasCsrf) {
          await fetchUser();
        }
      } catch {
        // Backend unavailable or not authenticated - still mark as ready
        // App will function in guest mode
      } finally {
        status.value = "ready";
        initializationPromise = null;
      }
    })();

    return initializationPromise;
  }

  /**
   * Read-only accessor for the in-flight init promise. Returns null when
   * no init is in progress. Callers (router guards) may `await` this.
   */
  function getInitializationPromise(): Promise<void> | null {
    return initializationPromise;
  }

  /**
   * Ensure user information is loaded.
   */
  async function ensureUser(): Promise<User | null> {
    if (user.value) {
      return user.value;
    }

    try {
      return await fetchUser();
    } catch {
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
   */
  async function fetchUser(): Promise<User | null> {
    try {
      const response = await apiGet<{ user: User; csrfToken?: string }>(
        "/auth/me",
        { skipErrorHandler: true },
      );

      if (!response?.user) {
        console.error("[Auth] Invalid /auth/me response:", response);
        throw new Error("Invalid user response from /auth/me");
      }

      user.value = response.user;

      if (response.csrfToken) {
        csrfManager.refreshFromResponse(response);
      }

      return response.user;
    } catch {
      user.value = null;
      return null;
    }
  }

  /**
   * Login with username and password.
   */
  async function login(credentials: LoginRequest): Promise<void> {
    status.value = "loading";
    error.value = null;

    try {
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/login",
        credentials,
      );

      if (!fetchedUser) {
        throw new Error("Invalid login response");
      }

      if (csrfToken) {
        csrfManager.refreshFromResponse({ csrfToken });
      }

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
   */
  async function register(data: RegisterRequest): Promise<void> {
    status.value = "loading";
    error.value = null;

    try {
      const { user: fetchedUser, csrfToken } = await apiPost<LoginResponse>(
        "/auth/register",
        data,
      );

      if (!fetchedUser) {
        throw new Error("Invalid register response");
      }

      if (csrfToken) {
        csrfManager.refreshFromResponse({ csrfToken });
      }

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
   */
  function clearUser(): void {
    user.value = null;
    permissions.value.clear();
    csrfManager.clearToken();
    status.value = "ready";
    error.value = null;
  }

  /**
   * Reset the session to initial state
   */
  function reset(): void {
    user.value = null;
    status.value = "idle";
    error.value = null;
    initializationPromise = null;
    csrfManager.clearToken();
  }

  /**
   * Load permissions for the current user
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

  return {
    // state refs
    user,
    status,
    error,
    permissions,
    // actions
    initialize,
    ensureUser,
    fetchUser,
    login,
    register,
    logout,
    clearUser,
    reset,
    loadPermissions,
    // internal dedup accessor — store bridges this to its public
    // initializationPromise computed; not for direct external use.
    getInitializationPromise,
  };
}

export type AuthSession = ReturnType<typeof useAuthSession>;
