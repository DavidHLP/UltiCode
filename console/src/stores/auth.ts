import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { apiGet } from "@/utils/request";
import type { User } from "@/api/auth";
import { clearCsrfToken } from "@/utils/csrf";

const HAS_SESSION_KEY = "ulticode_has_session";

const isDevelopment = import.meta.env.DEV;

function hasSessionFlag(): boolean {
  return localStorage.getItem(HAS_SESSION_KEY) === "true";
}

export function setSessionFlag(): void {
  if (isDevelopment) {
    console.log("[Auth] Setting session flag");
  }
  localStorage.setItem(HAS_SESSION_KEY, "true");
}

export function clearSessionFlag(): void {
  if (isDevelopment) {
    console.log("[Auth] Clearing session flag");
  }
  localStorage.removeItem(HAS_SESSION_KEY);
}

export const useAuthStore = defineStore("auth", () => {
  const user = ref<User | null>(null);
  const permissions = ref<Set<string>>(new Set());
  const isLoading = ref(false);
  const isInitialized = ref(false);

  const isAuthenticated = computed(() => !!user.value);
  const userRole = computed(() => user.value?.role || "");
  const userName = computed(
    () => user.value?.name || user.value?.username || "",
  );
  const userId = computed(() => user.value?.id || "");

  async function fetchUser(): Promise<User | null> {
    try {
      isLoading.value = true;
      // /auth/me returns { csrfToken, user }, extract the user field
      const response = await apiGet<{ csrfToken?: string; user?: User }>(
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

      // Store CSRF token if present
      if (response.csrfToken) {
        const { setCsrfToken } = await import("@/utils/csrf");
        setCsrfToken(response.csrfToken);
      }

      user.value = response.user;
      if (isDevelopment) {
        console.log("[Auth] User data set:", user.value);
        console.log("[Auth] isAuthenticated:", isAuthenticated.value);
      }
      return response.user;
    } catch (error) {
      console.error("[Auth] fetchUser error:", error);
      // 401 means no valid session - clear flag to prevent future unnecessary calls
      user.value = null;
      permissions.value.clear();
      clearSessionFlag();
      return null;
    } finally {
      isLoading.value = false;
    }
  }

  async function initialize(): Promise<void> {
    if (isInitialized.value) return;

    if (isDevelopment) {
      console.log(
        "[Auth] initialize() called, hasSessionFlag:",
        hasSessionFlag(),
      );
    }

    isInitialized.value = true;

    // Skip API call if no session exists
    if (!hasSessionFlag()) {
      if (isDevelopment) {
        console.log("[Auth] No session flag, skipping fetchUser");
      }
      return;
    }

    await fetchUser();
  }

  function clearUser(): void {
    user.value = null;
    permissions.value.clear();
    clearCsrfToken();
    clearSessionFlag();
    isInitialized.value = false;
  }

  /**
   * Mark the auth store as initialized without calling fetchUser().
   * This is used after successful login/registration to prevent the router
   * guard from calling initialize() again, which would cause a race condition.
   */
  function markAsInitialized(): void {
    isInitialized.value = true;
  }

  // Permission system - for future use or if admin features are added
  async function loadPermissions() {
    if (!user.value) return;

    try {
      // Public frontend doesn't have permissions endpoint
      // This is kept for API compatibility with admin-frontend
      // If needed, implement the endpoint on backend
      permissions.value = new Set();
    } catch (error) {
      console.error("Failed to load permissions:", error);
    }
  }

  function hasPermission(action: string, resource: string): boolean {
    if (permissions.value.has("*:*")) return true;
    if (permissions.value.has(`${action}:${resource}`)) return true;
    if (permissions.value.has(`${action}:*`)) return true;
    return false;
  }

  function hasRole(role: string): boolean {
    return user.value?.role === role;
  }

  function hasAnyRole(roles: string[]): boolean {
    return roles.includes(user.value?.role || "");
  }

  return {
    user,
    permissions,
    isLoading,
    isInitialized,
    isAuthenticated,
    userRole,
    userName,
    userId,
    fetchUser,
    initialize,
    clearUser,
    markAsInitialized,
    loadPermissions,
    hasPermission,
    hasRole,
    hasAnyRole,
  };
});
