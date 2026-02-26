import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { apiGet } from "@/utils/request";
import type { User } from "@/api/auth";
import { clearCsrfToken } from "@/utils/csrf";

const HAS_SESSION_KEY = "ulticode_has_session";

function hasSessionFlag(): boolean {
  return localStorage.getItem(HAS_SESSION_KEY) === "true";
}

export function setSessionFlag(): void {
  localStorage.setItem(HAS_SESSION_KEY, "true");
}

export function clearSessionFlag(): void {
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
      const userData = await apiGet<User>("/auth/me");
      user.value = userData;
      // Public frontend doesn't have permissions endpoint, so we skip it
      return userData;
    } catch {
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

    isInitialized.value = true;

    // Skip API call if no session exists
    if (!hasSessionFlag()) {
      return;
    }

    await fetchUser();
  }

  function clearUser(): void {
    user.value = null;
    permissions.value.clear();
    clearCsrfToken();
    clearSessionFlag();
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
    loadPermissions,
    hasPermission,
    hasRole,
    hasAnyRole,
  };
});
