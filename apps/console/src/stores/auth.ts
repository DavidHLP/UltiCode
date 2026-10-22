import type { User } from "@/types/auth";
import { apiGet, apiPost } from "@/utils/request";
import {
  checkAnyRole,
  checkPermission,
  checkRole,
  createSessionAuthStore,
  csrfManager,
  hasCsrfCookie,
} from "@ulticode/auth-core";
import { defineStore } from "pinia";
import { computed } from "vue";

/** Merge the profile avatar onto the identity returned by /auth/me. */
async function withProfileAvatar(user: User): Promise<User> {
  try {
    const profile = await apiGet<{ avatar?: string | null }>("/users/me", {
      skipErrorHandler: true,
    });
    return profile?.avatar ? { ...user, avatar: profile.avatar } : user;
  } catch {
    return user;
  }
}

/** Console auth Pinia store backed by the shared session policy. */
export const useAuthStore = defineStore("auth", () => {
  const session = createSessionAuthStore<User>({
    fetchCurrentUser: async () => {
      const response = await apiGet<{ user: User; csrfToken?: string }>(
        "/auth/me",
        { skipErrorHandler: true },
      );
      if (response?.user) {
        response.user = await withProfileAvatar(response.user);
      }
      return response;
    },
    login: async (credentials) => {
      return apiPost<{ user: User; csrfToken?: string }>(
        "/auth/login",
        credentials,
      );
    },
    register: async (data) => {
      return apiPost<{ user: User; csrfToken?: string }>(
        "/auth/register",
        data,
      );
    },
    logout: () => apiPost<void>("/auth/logout"),
    loadPermissions: () =>
      apiGet<string[]>("/auth/permissions", { skipErrorHandler: true }),
    hasSessionCookie: hasCsrfCookie,
    refreshCsrf: (response) => csrfManager.refreshFromResponse(response),
    clearCsrf: () => csrfManager.clearToken(),
  });

  const isAuthenticated = computed(() => !!session.user.value);
  const isInitialized = computed(() => session.status.value === "ready");
  const isLoading = computed(() => session.status.value === "loading");
  const userId = computed(() => session.user.value?.id || "");
  const userName = computed(
    () => session.user.value?.name || session.user.value?.username || "",
  );
  const userRole = computed(() => session.user.value?.role || "");
  const initializationPromise = computed(() => session.whenInitialized());

  async function refreshProfileAvatar(): Promise<void> {
    const user = session.user.value;
    if (!user) return;
    const enriched = await withProfileAvatar(user);
    if (session.user.value === user) session.user.value = enriched;
  }

  async function login(credentials: unknown): Promise<void> {
    await session.login(credentials);
    // Optional profile 401s may refresh the session after the login lock is released.
    await refreshProfileAvatar();
  }

  async function register(data: unknown): Promise<void> {
    await session.register(data);
    await refreshProfileAvatar();
  }

  function fetchCurrentUserId(): string | null {
    return session.user.value?.id || null;
  }

  function setUserAvatar(avatar: string): void {
    const user = session.user.value;
    if (user) {
      session.user.value = { ...user, avatar };
    }
  }

  return {
    user: session.user,
    status: session.status,
    error: session.error,
    permissions: session.permissions,
    isAuthenticated,
    isInitialized,
    isLoading,
    initializationPromise,
    userId,
    userName,
    userRole,
    initialize: session.initialize,
    ensureUser: session.ensureUser,
    fetchUser: session.fetchUser,
    login,
    register,
    logout: session.logout,
    clearUser: session.clearUser,
    reset: session.reset,
    fetchCurrentUserId,
    setUserAvatar,
    loadPermissions: session.loadPermissions,
    hasPermission: (action: string, resource: string) =>
      checkPermission(session.permissions.value, action, resource),
    hasRole: (role: string) => checkRole(session.user.value?.role, role),
    hasAnyRole: (roles: string[]) =>
      checkAnyRole(session.user.value?.role, roles),
  };
});

export type AuthSession = ReturnType<typeof useAuthStore>;
