import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { apiGet } from "@/utils/request";
import type { User } from "@/api/auth";
import { clearCsrfToken } from "@/utils/csrf";

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
      // 401 is expected for unauthenticated users - no need to log
      user.value = null;
      permissions.value.clear();
      return null;
    } finally {
      isLoading.value = false;
    }
  }

  async function initialize(): Promise<void> {
    if (isInitialized.value) return;

    isInitialized.value = true;
    await fetchUser();
  }

  function clearUser(): void {
    user.value = null;
    permissions.value.clear();
    clearCsrfToken();
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
